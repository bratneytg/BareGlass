"""SQLite en modo WAL: coste casi cero, copia continua con Litestream. Toda la SQL vive aquí para poder pasar a PostgreSQL cambiando solo este archivo."""
import os
import sqlite3
import threading

SCHEMA = [
    """CREATE TABLE IF NOT EXISTS users(
        id TEXT PRIMARY KEY,
        email TEXT NOT NULL UNIQUE,
        pw_hash TEXT NOT NULL,
        dek_wrapped BLOB NOT NULL,
        plan TEXT NOT NULL DEFAULT 'free',
        plan_until INTEGER NOT NULL DEFAULT 0,
        adult_confirmed_at INTEGER NOT NULL,
        consent_at INTEGER NOT NULL,
        created_at INTEGER NOT NULL)""",
    """CREATE TABLE IF NOT EXISTS refresh_tokens(
        token_hash TEXT PRIMARY KEY,
        user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        expires_at INTEGER NOT NULL)""",
    "CREATE INDEX IF NOT EXISTS ix_rt_user ON refresh_tokens(user_id)",
    """CREATE TABLE IF NOT EXISTS links(
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        sealed BLOB NOT NULL,
        created_at INTEGER NOT NULL)""",
    "CREATE INDEX IF NOT EXISTS ix_links_user ON links(user_id, created_at DESC)",
    """CREATE TABLE IF NOT EXISTS billing_events(
        event_id TEXT PRIMARY KEY,
        user_id TEXT,
        kind TEXT NOT NULL,
        created_at INTEGER NOT NULL)""",
]


class DB:
    def __init__(self, path: str) -> None:
        if os.path.dirname(path):
            os.makedirs(os.path.dirname(path), exist_ok=True)
        self.path = path
        self.lock = threading.Lock()   # SQLite admite una escritura a la vez; para este tamaño sobra
        con = self.connect()
        con.execute("PRAGMA journal_mode=WAL")
        for s in SCHEMA:
            con.execute(s)
        con.commit()
        con.close()

    def connect(self) -> sqlite3.Connection:
        con = sqlite3.connect(self.path, timeout=10, check_same_thread=False)
        con.row_factory = sqlite3.Row
        con.execute("PRAGMA foreign_keys=ON")
        con.execute("PRAGMA synchronous=NORMAL")
        return con
