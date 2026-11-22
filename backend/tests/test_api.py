import base64
import hashlib
import hmac
import json
import os
import sqlite3
import time

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BG_DB_PATH", str(tmp_path / "t.db"))
    monkeypatch.setenv("BG_MASTER_KEY", base64.b64encode(os.urandom(32)).decode())
    monkeypatch.setenv("BG_JWT_SECRET", base64.b64encode(os.urandom(32)).decode())
    monkeypatch.setenv("BG_WEBHOOK_SECRET", "whsec")
    monkeypatch.setenv("BG_CHECKOUT_URL", "https://pay.example/checkout")
    monkeypatch.setenv("BG_FREE_LINKS", "2")
    c = TestClient(create_app(Settings()))
    c.db_path = str(tmp_path / "t.db")
    return c


def reg(c, email="a@b.com"):
    r = c.post("/v1/auth/register", json={"email": email, "password": "supersecret1", "adult": True, "consent": True})
    assert r.status_code == 200, r.text
    return r.json()


def H(tok):
    return {"Authorization": "Bearer " + tok["access"]}


def test_register_requires_adult(client):
    r = client.post("/v1/auth/register", json={"email": "a@b.com", "password": "supersecret1", "adult": False, "consent": True})
    assert r.status_code == 400


def test_register_requires_consent(client):
    r = client.post("/v1/auth/register", json={"email": "a@b.com", "password": "supersecret1", "adult": True, "consent": False})
    assert r.status_code == 400


def test_register_login_duplicate(client):
    t = reg(client)
    assert t["user"]["plan"] == "free"
    assert client.post("/v1/auth/register", json={"email": "A@b.com", "password": "supersecret1", "adult": True, "consent": True}).status_code == 409
    assert client.post("/v1/auth/login", json={"email": "a@b.com", "password": "mal"}).status_code == 401
    assert client.post("/v1/auth/login", json={"email": "a@b.com", "password": "supersecret1"}).status_code == 200
    assert client.get("/v1/me", headers=H(t)).json()["email"] == "a@b.com"
    assert client.get("/v1/me").status_code == 401


def test_refresh_rotates(client):
    t = reg(client)
    r = client.post("/v1/auth/refresh", json={"refresh": t["refresh"]})
    assert r.status_code == 200
    assert client.post("/v1/auth/refresh", json={"refresh": t["refresh"]}).status_code == 401
    assert client.post("/v1/auth/logout", json={"refresh": r.json()["refresh"]}).status_code == 200
    assert client.post("/v1/auth/refresh", json={"refresh": r.json()["refresh"]}).status_code == 401


def test_links_encrypted_at_rest_and_limited(client):
    t = reg(client)
    h = H(t)
    assert client.post("/v1/links", json={"url": "ftp://example.com/x.mp4", "title": "x"}, headers=h).status_code == 400
    r = client.post("/v1/links", json={"url": "https://example.com/video.mp4", "title": "secreto"}, headers=h)
    assert r.status_code == 200
    raw = sqlite3.connect(client.db_path).execute("SELECT sealed FROM links").fetchone()[0]
    assert b"example.com" not in raw and b"secreto" not in raw
    client.post("/v1/links", json={"url": "https://example.com/2.mp4"}, headers=h)
    assert client.post("/v1/links", json={"url": "https://example.com/3.mp4"}, headers=h).status_code == 402
    lst = client.get("/v1/links", headers=h).json()
    assert len(lst) == 2 and {x["url"] for x in lst} == {"https://example.com/video.mp4", "https://example.com/2.mp4"}
    assert client.delete("/v1/links/" + lst[0]["id"], headers=h).status_code == 200
    assert len(client.get("/v1/links", headers=H(t)).json()) == 1


def test_users_cannot_see_each_others_links(client):
    a, b = reg(client, "a@b.com"), reg(client, "c@d.com")
    lid = client.post("/v1/links", json={"url": "https://example.com/a.mp4"}, headers=H(a)).json()["id"]
    assert client.get("/v1/links", headers=H(b)).json() == []
    client.delete("/v1/links/" + lid, headers=H(b))
    assert len(client.get("/v1/links", headers=H(a)).json()) == 1


def test_delete_account_erases_everything(client):
    t = reg(client)
    client.post("/v1/links", json={"url": "https://example.com/a.mp4"}, headers=H(t))
    assert client.delete("/v1/me", headers=H(t)).status_code == 200
    con = sqlite3.connect(client.db_path)
    for tbl in ("users", "links", "refresh_tokens"):
        assert con.execute(f"SELECT COUNT(*) FROM {tbl}").fetchone()[0] == 0
    assert client.get("/v1/me", headers=H(t)).status_code == 401


def test_export(client):
    t = reg(client)
    client.post("/v1/links", json={"url": "https://example.com/a.mp4", "title": "t"}, headers=H(t))
    d = client.get("/v1/me/export", headers=H(t)).json()
    assert d["user"]["email"] == "a@b.com" and d["links"][0]["title"] == "t"


def _hook(client, body, key=b"whsec"):
    raw = json.dumps(body).encode()
    sig = hmac.new(key, raw, hashlib.sha256).hexdigest()
    return client.post("/v1/billing/webhook", content=raw, headers={"X-Signature": sig, "Content-Type": "application/json"})


def test_billing_flow(client):
    t = reg(client)
    uid = t["user"]["id"]
    assert client.post("/v1/billing/checkout", headers=H(t)).json()["url"].endswith("client_ref=" + uid)
    assert _hook(client, {"event_id": "e0", "type": "subscription.active", "user_id": uid, "until": 9}, key=b"mal").status_code == 401
    ev = {"event_id": "e1", "type": "subscription.active", "user_id": uid, "until": int(time.time()) + 3600}
    assert _hook(client, ev).status_code == 200
    assert _hook(client, ev).json().get("duplicate") is True
    me = client.get("/v1/me", headers=H(t)).json()
    assert me["plan"] == "premium" and me["link_limit"] == 5000
    assert _hook(client, {"event_id": "e2", "type": "subscription.cancelled", "user_id": uid}).status_code == 200
    assert client.get("/v1/me", headers=H(t)).json()["plan"] == "free"


def test_expired_premium_is_free(client):
    t = reg(client)
    uid = t["user"]["id"]
    _hook(client, {"event_id": "e1", "type": "subscription.active", "user_id": uid, "until": int(time.time()) - 5})
    assert client.get("/v1/me", headers=H(t)).json()["plan"] == "free"


def test_login_rate_limit(client):
    reg(client)
    codes = [client.post("/v1/auth/login", json={"email": "a@b.com", "password": "x"}).status_code for _ in range(12)]
    assert 429 in codes
