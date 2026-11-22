"""API de BareGlass: cuentas, enlaces guardados y suscripción. No aloja ni descarga ningún video: solo guarda los enlaces cifrados del propio usuario."""
import hashlib
import hmac
import secrets
import time
import uuid
from collections import defaultdict, deque
from urllib.parse import urlparse, urlencode

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field

from . import security as sec
from .config import Settings
from .db import DB


class RateLimiter:
    """Ventana deslizante en memoria. Con varias instancias hay que moverlo a Redis o al borde (Cloudflare)."""

    def __init__(self, limit: int, window: int) -> None:
        self.limit, self.window = limit, window
        self.hits: dict[str, deque] = defaultdict(deque)

    def check(self, key: str) -> None:
        now = time.time()
        q = self.hits[key]
        while q and q[0] < now - self.window:
            q.popleft()
        if len(q) >= self.limit:
            raise HTTPException(429, "Demasiados intentos, espera unos minutos")
        q.append(now)


class Register(BaseModel):
    email: str = Field(min_length=5, max_length=254)
    password: str = Field(min_length=8, max_length=200)
    adult: bool = True   # obsoleto: la app ya no lo pide (se mantiene por compatibilidad)
    consent: bool   # consentimiento explícito a guardar los enlaces cifrados (pueden revelar datos personales sensibles, RGPD art. 9)


class Login(BaseModel):
    email: str = Field(max_length=254)
    password: str = Field(max_length=200)


class Refresh(BaseModel):
    refresh: str = Field(max_length=200)


class LinkIn(BaseModel):
    url: str = Field(min_length=8, max_length=2048)
    title: str = Field(default="", max_length=200)


def create_app(settings: Settings | None = None) -> FastAPI:
    cfg = settings or Settings()
    db = DB(cfg.db_path)
    auth_rl = RateLimiter(10, 600)
    app = FastAPI(title="BareGlass API", docs_url=None, redoc_url=None)

    def now() -> int:
        return int(time.time())

    def plan_of(u) -> str:
        return "premium" if u["plan"] == "premium" and u["plan_until"] > now() else "free"

    def user_json(u) -> dict:
        p = plan_of(u)
        return {
            "id": u["id"], "email": u["email"], "plan": p, "plan_until": u["plan_until"] if p == "premium" else 0,
            "link_limit": cfg.premium_link_limit if p == "premium" else cfg.free_link_limit,
        }

    def issue(con, u) -> dict:
        tok, h = sec.new_refresh_token()
        con.execute("INSERT INTO refresh_tokens VALUES(?,?,?)", (h, u["id"], now() + cfg.refresh_ttl))
        con.commit()
        return {"access": sec.make_jwt(cfg.jwt_secret, u["id"], cfg.access_ttl), "refresh": tok, "user": user_json(u)}

    def current(authorization: str = Header(default="")):
        uid = sec.read_jwt(cfg.jwt_secret, authorization[7:]) if authorization.startswith("Bearer ") else None
        if not uid:
            raise HTTPException(401, "Sesión no válida")
        con = db.connect()
        try:
            u = con.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
        finally:
            con.close()
        if not u:
            raise HTTPException(401, "Sesión no válida")
        return u

    def ip(req: Request) -> str:
        fwd = req.headers.get("x-forwarded-for", "")
        return fwd.split(",")[0].strip() if fwd else (req.client.host if req.client else "?")

    @app.get("/healthz")
    def healthz():
        return {"ok": True}

    @app.post("/v1/auth/register")
    def register(body: Register, req: Request):
        auth_rl.check("reg:" + ip(req))
        email = body.email.strip().lower()
        if "@" not in email or " " in email or len(email.split("@")[-1]) < 3:
            raise HTTPException(400, "Correo no válido")
        if not body.consent:
            raise HTTPException(400, "Necesitamos tu consentimiento para guardar tus enlaces de forma cifrada")
        dek, wrapped = sec.new_user_key(cfg.master_key)
        uid = uuid.uuid4().hex
        pw = sec.hash_password(body.password)
        with db.lock:
            con = db.connect()
            try:
                try:
                    con.execute(
                        "INSERT INTO users(id,email,pw_hash,dek_wrapped,adult_confirmed_at,consent_at,created_at) VALUES(?,?,?,?,?,?,?)",
                        (uid, email, pw, wrapped, now(), now(), now()))
                except Exception:
                    raise HTTPException(409, "Ese correo ya está registrado")
                u = con.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
                return issue(con, u)
            finally:
                con.close()

    @app.post("/v1/auth/login")
    def login(body: Login, req: Request):
        email = body.email.strip().lower()
        auth_rl.check("log:" + ip(req))
        auth_rl.check("log:" + email)
        con = db.connect()
        try:
            u = con.execute("SELECT * FROM users WHERE email=?", (email,)).fetchone()
            ok = sec.verify_password(body.password, u["pw_hash"]) if u else sec.verify_password(body.password, "x$x$x")
            if not u or not ok:
                raise HTTPException(401, "Correo o contraseña incorrectos")
            with db.lock:
                return issue(con, u)
        finally:
            con.close()

    @app.post("/v1/auth/refresh")
    def refresh(body: Refresh):
        h = sec.hash_token(body.refresh)
        with db.lock:
            con = db.connect()
            try:
                r = con.execute("SELECT * FROM refresh_tokens WHERE token_hash=?", (h,)).fetchone()
                if not r or r["expires_at"] < now():
                    raise HTTPException(401, "Sesión caducada")
                con.execute("DELETE FROM refresh_tokens WHERE token_hash=?", (h,))   # rotación: cada refresh sirve una vez
                u = con.execute("SELECT * FROM users WHERE id=?", (r["user_id"],)).fetchone()
                if not u:
                    raise HTTPException(401, "Sesión caducada")
                return issue(con, u)
            finally:
                con.close()

    @app.post("/v1/auth/logout")
    def logout(body: Refresh):
        with db.lock:
            con = db.connect()
            try:
                con.execute("DELETE FROM refresh_tokens WHERE token_hash=?", (sec.hash_token(body.refresh),))
                con.commit()
            finally:
                con.close()
        return {"ok": True}

    @app.get("/v1/me")
    def me(u=Depends(current)):
        return user_json(u)

    @app.delete("/v1/me")
    def delete_me(u=Depends(current)):
        """Borrado total (RGPD art. 17): cuenta, sesiones y enlaces; la clave de datos desaparece con la fila."""
        with db.lock:
            con = db.connect()
            try:
                con.execute("DELETE FROM users WHERE id=?", (u["id"],))
                con.commit()
            finally:
                con.close()
        return {"ok": True}

    def links_of(u) -> list[dict]:
        con = db.connect()
        try:
            rows = con.execute("SELECT * FROM links WHERE user_id=? ORDER BY created_at DESC", (u["id"],)).fetchall()
        finally:
            con.close()
        dek = sec.open_user_key(cfg.master_key, u["dek_wrapped"])
        out = []
        for r in rows:
            d = sec.unseal(dek, u["id"], r["sealed"])
            out.append({"id": r["id"], "url": d["url"], "title": d.get("title", ""), "created_at": r["created_at"]})
        return out

    @app.get("/v1/me/export")
    def export(u=Depends(current)):
        """Portabilidad (RGPD art. 20)."""
        return {"user": user_json(u), "links": links_of(u)}

    @app.get("/v1/links")
    def list_links(u=Depends(current)):
        return links_of(u)

    @app.post("/v1/links")
    def add_link(body: LinkIn, u=Depends(current)):
        p = urlparse(body.url.strip())
        if p.scheme not in ("http", "https") or not p.netloc:
            raise HTTPException(400, "El enlace debe empezar por http:// o https://")
        lid = uuid.uuid4().hex
        dek = sec.open_user_key(cfg.master_key, u["dek_wrapped"])
        sealed = sec.seal(dek, u["id"], {"url": body.url.strip(), "title": body.title.strip()})
        limit = user_json(u)["link_limit"]
        with db.lock:
            con = db.connect()
            try:
                n = con.execute("SELECT COUNT(*) FROM links WHERE user_id=?", (u["id"],)).fetchone()[0]
                if n >= limit:
                    raise HTTPException(402, f"Has llegado al límite de {limit} enlaces. Hazte Premium para guardar más.")
                con.execute("INSERT INTO links VALUES(?,?,?,?)", (lid, u["id"], sealed, now()))
                con.commit()
            finally:
                con.close()
        return {"id": lid, "url": body.url.strip(), "title": body.title.strip(), "created_at": now()}

    @app.delete("/v1/links/{lid}")
    def del_link(lid: str, u=Depends(current)):
        with db.lock:
            con = db.connect()
            try:
                con.execute("DELETE FROM links WHERE id=? AND user_id=?", (lid, u["id"]))
                con.commit()
            finally:
                con.close()
        return {"ok": True}

    @app.post("/v1/billing/checkout")
    def checkout(u=Depends(current)):
        """Devuelve la página de pago alojada por el procesador (CCBill, Segpay, Epoch…). La app la abre en el navegador."""
        if not cfg.checkout_url:
            raise HTTPException(503, "Los pagos todavía no están activados")
        sep = "&" if "?" in cfg.checkout_url else "?"
        return {"url": cfg.checkout_url + sep + urlencode({"client_ref": u["id"]})}

    @app.post("/v1/billing/webhook")
    async def webhook(req: Request, x_signature: str = Header(default="")):
        """El procesador de pagos avisa aquí. Firma HMAC-SHA256 del cuerpo; cada evento se procesa una sola vez."""
        raw = await req.body()
        if not cfg.webhook_secret:
            raise HTTPException(503, "Webhook sin configurar")
        want = hmac.new(cfg.webhook_secret.encode(), raw, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(want, x_signature):
            raise HTTPException(401, "Firma no válida")
        ev = await req.json()
        eid, kind, uid = str(ev.get("event_id", "")), str(ev.get("type", "")), str(ev.get("user_id", ""))
        if not eid or kind not in ("subscription.active", "subscription.cancelled"):
            raise HTTPException(400, "Evento no válido")
        with db.lock:
            con = db.connect()
            try:
                if con.execute("SELECT 1 FROM billing_events WHERE event_id=?", (eid,)).fetchone():
                    return {"ok": True, "duplicate": True}
                if not con.execute("SELECT 1 FROM users WHERE id=?", (uid,)).fetchone():
                    raise HTTPException(404, "Usuario desconocido")
                if kind == "subscription.active":
                    until = int(ev.get("until", 0))
                    con.execute("UPDATE users SET plan='premium', plan_until=? WHERE id=?", (until, uid))
                else:
                    con.execute("UPDATE users SET plan='free', plan_until=0 WHERE id=?", (uid,))
                con.execute("INSERT INTO billing_events VALUES(?,?,?,?)", (eid, uid, kind, now()))
                con.commit()
            finally:
                con.close()
        return {"ok": True}

    return app
