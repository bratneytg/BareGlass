"""Contraseñas (scrypt), tokens (JWT HS256) y cifrado de datos por usuario (AES-GCM), solo con librerías estándar + cryptography."""
import base64
import hashlib
import hmac
import json
import os
import secrets
import time

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

_SCRYPT = dict(n=2 ** 14, r=8, p=1, maxmem=64 * 1024 * 1024, dklen=32)


def hash_password(pw: str) -> str:
    salt = os.urandom(16)
    h = hashlib.scrypt(pw.encode(), salt=salt, **_SCRYPT)
    return "scrypt$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(h).decode()


def verify_password(pw: str, stored: str) -> bool:
    try:
        _, s, h = stored.split("$")
        salt, want = base64.b64decode(s), base64.b64decode(h)
    except Exception:
        return False
    got = hashlib.scrypt(pw.encode(), salt=salt, **_SCRYPT)
    return hmac.compare_digest(got, want)


def _b64u(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def _b64u_dec(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def make_jwt(secret: bytes, sub: str, ttl: int) -> str:
    head = _b64u(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    body = _b64u(json.dumps({"sub": sub, "exp": int(time.time()) + ttl}, separators=(",", ":")).encode())
    sig = hmac.new(secret, f"{head}.{body}".encode(), hashlib.sha256).digest()
    return f"{head}.{body}.{_b64u(sig)}"


def read_jwt(secret: bytes, token: str) -> str | None:
    """Devuelve el id de usuario si el token es válido y no ha caducado."""
    try:
        head, body, sig = token.split(".")
        if json.loads(_b64u_dec(head)).get("alg") != "HS256":
            return None
        want = hmac.new(secret, f"{head}.{body}".encode(), hashlib.sha256).digest()
        if not hmac.compare_digest(want, _b64u_dec(sig)):
            return None
        data = json.loads(_b64u_dec(body))
        if data["exp"] < time.time():
            return None
        return str(data["sub"])
    except Exception:
        return None


def new_refresh_token() -> tuple[str, str]:
    """(token en claro para el cliente, hash para guardar)."""
    t = secrets.token_urlsafe(32)
    return t, hashlib.sha256(t.encode()).hexdigest()


def hash_token(t: str) -> str:
    return hashlib.sha256(t.encode()).hexdigest()


# --- cifrado en reposo: cada usuario tiene su propia clave de datos (DEK), guardada cifrada con la clave maestra (KEK).
# Borrar al usuario destruye su DEK: aunque quedara una copia de seguridad, sus enlaces serían ilegibles.

def new_user_key(master: bytes) -> tuple[bytes, bytes]:
    dek = os.urandom(32)
    nonce = os.urandom(12)
    return dek, nonce + AESGCM(master).encrypt(nonce, dek, b"dek")


def open_user_key(master: bytes, wrapped: bytes) -> bytes:
    return AESGCM(master).decrypt(wrapped[:12], wrapped[12:], b"dek")


def seal(dek: bytes, user_id: str, data: dict) -> bytes:
    nonce = os.urandom(12)
    return nonce + AESGCM(dek).encrypt(nonce, json.dumps(data, ensure_ascii=False).encode(), user_id.encode())


def unseal(dek: bytes, user_id: str, blob: bytes) -> dict:
    return json.loads(AESGCM(dek).decrypt(blob[:12], blob[12:], user_id.encode()))
