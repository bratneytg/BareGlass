"""Configuración por variables de entorno (nada sensible en el código)."""
import base64
import os


def _b64key(name: str) -> bytes:
    raw = os.environ.get(name, "")
    if not raw:
        raise RuntimeError(f"Falta la variable de entorno {name}")
    key = base64.b64decode(raw)
    if len(key) != 32:
        raise RuntimeError(f"{name} debe ser una clave de 32 bytes en base64")
    return key


class Settings:
    def __init__(self) -> None:
        self.db_path = os.environ.get("BG_DB_PATH", "data/bareglass.db")
        self.master_key = _b64key("BG_MASTER_KEY")      # envuelve la clave de cada usuario
        self.jwt_secret = _b64key("BG_JWT_SECRET")      # firma de los access tokens
        self.webhook_secret = os.environ.get("BG_WEBHOOK_SECRET", "")
        self.checkout_url = os.environ.get("BG_CHECKOUT_URL", "")   # página de pago alojada por el procesador
        self.access_ttl = int(os.environ.get("BG_ACCESS_TTL", "900"))            # 15 min
        self.refresh_ttl = int(os.environ.get("BG_REFRESH_TTL", str(60 * 86400)))  # 60 días
        self.free_link_limit = int(os.environ.get("BG_FREE_LINKS", "15"))
        self.premium_link_limit = int(os.environ.get("BG_PREMIUM_LINKS", "5000"))
