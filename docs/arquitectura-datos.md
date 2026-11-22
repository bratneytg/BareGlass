# Arquitectura de datos de BareGlass (Well-Architected, prioridad: coste)

Decisión de partida: **gastar lo mínimo hoy sin cerrar la puerta a crecer mañana.** Los seis pilares del AWS Well-Architected Framework se aplican igual aunque no uses AWS; aquí van ordenados por la prioridad que pediste: primero coste, y el resto sin ponerle trampas al futuro.

## 1. Qué se guarda (y qué no)

| Dato | Dónde | Notas |
|---|---|---|
| Correo, hash de contraseña (scrypt), plan | tabla `users` | Único dato personal «normal». |
| Consentimiento y confirmación de mayoría de edad | `users.consent_at`, `adult_confirmed_at` | Prueba de que el usuario aceptó. |
| Enlaces guardados (URL + título) | tabla `links`, **cifrados** | Pueden revelar la vida sexual del usuario → dato de categoría especial (ver §3). |
| Sesiones | `refresh_tokens` (solo el hash) | Rotan en cada uso. |
| Pagos | **no se guardan tarjetas**: el procesador (CCBill, Segpay…) | Solo `plan` y `plan_until` + id de evento (`billing_events`). |
| Videos, historial de reproducción, IP | **nada** | La app no aloja ni registra lo que ves. Cuanto menos se guarda, menos hay que proteger y menos puede filtrarse. |

## 2. Diseño elegido hoy: una máquina pequeña, todo incluido

```
Móvil ──HTTPS──▶ Caddy (certificado gratis) ──▶ API FastAPI ──▶ SQLite (modo WAL)
                                                                    │
                                                  Litestream ───────┘ copia continua ──▶ almacenamiento barato (Backblaze B2 / Cloudflare R2)
```

* **Un VPS europeo pequeño** (clase 2 vCPU / 2–4 GB; del orden de unos pocos € al mes, comprueba el precio actual) ejecuta los tres contenedores de `backend/deploy/docker-compose.yml`.
* **SQLite en modo WAL** aguanta con holgura miles de usuarios activos con este patrón de uso (pocas escrituras, lecturas simples). Cuesta **0 €** extra y no hay servidor de base de datos que mantener.
* **Litestream** replica cada cambio a un bucket S3-compatible (céntimos al mes) → recuperación a un punto casi exacto, con 7 días de historial.
* Hospedar en la UE simplifica el RGPD.
* La API no es propietaria de ningún vídeo: no hay ancho de banda caro (el tráfico de video va del sitio de origen al móvil, no pasa por tus servidores).

**Coste orientativo mensual (estimaciones, verifica precios actuales):** VPS ≈ 4–8 € · bucket de copias ≈ 1 € · dominio ≈ 1 € → **unos 6–10 € al mes** hasta varios miles de usuarios.

## 3. Seguridad (pilar que no se recorta)

* **Cifrado por usuario (envelope encryption).** Cada usuario tiene su propia clave de datos (DEK, AES-256-GCM) guardada cifrada con una clave maestra (`BG_MASTER_KEY`). Los enlaces se cifran con su DEK y quedan ligados al id del usuario (no se pueden mover entre cuentas). Un volcado de la base de datos o de una copia **no revela enlaces**. Al borrar la cuenta se destruye la DEK: es un borrado criptográfico, también dentro de las copias antiguas.
* **Contraseñas** con scrypt; tokens de acceso de 15 min (JWT HS256) y de refresco opacos, de un solo uso (rotación) y guardados solo como hash.
* **Límite de intentos** en registro/inicio de sesión; error idéntico para «correo» y «contraseña» incorrectos.
* **Webhook de pagos** firmado con HMAC-SHA256 e idempotente (cada evento se aplica una vez).
* **Sin SDK de analítica ni publicidad dentro de la app.** Vale para la privacidad y para el RGPD.
* **RGPD — categoría especial.** Los datos sobre vida sexual (también los que se *deducen*, según el TJUE) están en el art. 9 del RGPD: su tratamiento está prohibido salvo excepciones, y la vía realista aquí es el **consentimiento explícito** (la app lo pide con una casilla propia y la API lo registra). Además: evaluación de impacto (DPIA), política de privacidad clara, derecho de acceso/portabilidad (`GET /v1/me/export`) y de supresión (`DELETE /v1/me`) ya implementados.
* **Debilidad conocida y barata de corregir:** la clave maestra vive en una variable de entorno del mismo servidor. Mejora cuando haya dinero/usuarios: moverla a un KMS (AWS KMS, GCP KMS o Hashicorp Vault) para que el servidor nunca la tenga entera.

## 4. Fiabilidad

* Copias continuas (Litestream) + **simulacro de restauración** cada trimestre: `litestream restore -o prueba.db …` y abrirla.
* `GET /healthz` para monitorizar (UptimeRobot gratis).
* Reinicio automático de contenedores (`restart: unless-stopped`).
* Límite asumido hoy: **una sola instancia** → una caída de la máquina = minutos de corte (el móvil sigue reproduciendo archivos y enlaces; solo falla la cuenta). Para una app de este tipo es un riesgo aceptable al inicio.

## 5. Excelencia operativa

* Todo está en el repositorio: infraestructura (`deploy/`), pruebas (`backend/tests`, 11 pruebas) y CI (GitHub Actions) que ejecuta pruebas del backend y compila el APK en cada cambio.
* Configuración por variables de entorno; nada sensible en el código.
* Esquema con `CREATE TABLE IF NOT EXISTS`; cuando cambie, añadir migraciones numeradas (Alembic al pasar a PostgreSQL).
* Despliegue: `docker compose pull && docker compose up -d` por SSH o con una acción de GitHub.

## 6. Eficiencia de rendimiento

* Consultas por índice (`links(user_id, created_at)`); sin joins costosos.
* Descifrar 15–5000 enlaces por petición es trivial (AES-GCM acelerado por hardware).
* Si la lista de enlaces crece mucho: paginar `GET /v1/links` (campo `cursor`) — ya se acota con el límite del plan (15 gratis / 5000 premium).

## 7. Sostenibilidad

Una máquina pequeña casi siempre ocupada gasta menos que una flota infrautilizada. Al escalar, preferir autoescalado a sobredimensionar.

## 8. Hoja de ruta para escalar (sin reescribir)

Toda la SQL está en `app/db.py` + `main.py`; el resto no depende de SQLite. Disparadores sugeridos:

| Fase | Señal para pasar de fase | Cambio | Coste extra aprox. |
|---|---|---|---|
| **0 · Hoy** (0–5.000 usuarios) | — | 1 VPS + SQLite + Litestream | ~6–10 €/mes |
| **1** (5.000–50.000) | CPU > 60 % sostenida o esperas por bloqueo de escritura | VPS mayor; limitador de intentos al borde (Cloudflare) | +10–30 € |
| **2** (50.000–300.000) | Necesitas ≥ 2 instancias de API o despliegues sin corte | **PostgreSQL gestionado** (Neon/Supabase/RDS) cambiando `db.py`; Redis para límites de uso y sesiones; clave maestra en KMS | +50–200 € |
| **3** (> 300.000) | Latencia entre continentes, picos | Réplicas de lectura, CDN delante de la API, varias regiones, colas para webhooks y correos | según uso |

Cada paso se justifica por una métrica (CPU, latencia p95, errores 5xx), no por intuición.

## 9. Lo que *no* conviene hacer todavía

* Kubernetes, microservicios o bases de datos distribuidas: coste y trabajo sin beneficio a esta escala.
* Guardar historial de reproducción «para recomendar»: multiplica el riesgo legal y de privacidad.
* Alojar vídeo propio: ancho de banda caro y responsabilidad legal directa sobre el contenido.
