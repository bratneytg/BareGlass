# BareGlass

Visor de video en realidad virtual para adultos (Android + gafas Cardboard). Pega un enlace o abre un archivo y míralo en VR; cuenta opcional para guardar enlaces cifrados y suscripción Premium.

* **Aplicación Android** (`app/`): aviso +18, registro/inicio de sesión (o uso sin cuenta), pegar enlace (directo `.mp4/.webm/.m3u8` o página con video), abrir archivos del móvil, VR con seguimiento de cabeza y menú con la mirada, pantalla curva/plana, zoom, altura y separación estéreo, enlaces guardados, cuenta y Premium.
* **API** (`backend/`): FastAPI + SQLite (WAL) + Litestream. Cifrado por usuario. Despliegue en un VPS pequeño con `backend/deploy/`.
* **Documentos** (`docs/`): [arquitectura de datos](docs/arquitectura-datos.md) · [marketing](docs/marketing.md) · [ingresos](docs/ingresos.md) · [legal y distribución](docs/legal-y-distribucion.md).

## Compilar la app

El CI (GitHub Actions) compila el APK en cada cambio: pestaña *Actions* → último «Build» → artefacto **BareGlass-apk**.

Para que la app hable con tu servidor, define en GitHub → *Settings → Secrets and variables → Actions → Variables* la variable `API_BASE` (por ejemplo `https://api.tudominio.com`). En local: `gradle assembleDebug -PapiBase=https://api.tudominio.com`. También se puede escribir en la app: pantalla de entrada → «Servidor (avanzado)».

## Probar la API en local

```bash
cd backend
pip install -r requirements.txt
export BG_MASTER_KEY=$(python3 -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())")
export BG_JWT_SECRET=$(python3 -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())")
uvicorn app.asgi:app --reload
python -m pytest -q        # pruebas
```

## Despliegue

```bash
cd backend/deploy
cp .env.example .env   # rellena claves, URL de pago y credenciales del bucket de copias
# edita Caddyfile con tu dominio
docker compose up -d --build
```

## Estado
Versión inicial 0.1: la parte de pagos está preparada pero necesita una cuenta de un procesador de pagos para adultos; la verificación de edad es por declaración (ver `docs/legal-y-distribucion.md` antes de promocionar).
