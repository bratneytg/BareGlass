# 04 · Base de datos de usuarios (Well-Architected, prioridad: coste)

Objetivo: **gastar lo mínimo hoy sin cerrar la puerta a crecer.** Los seis pilares del *Well-Architected Framework* se aplican igual aunque no uses AWS. Consultado en noviembre de 2026.

## 1. Qué se guarda (y qué no)

| Dato | Dónde | Notas |
|---|---|---|
| Correo, hash de contraseña (scrypt), plan | `users` | Dato personal «normal». |
| Consentimiento y mayoría de edad | `users.consent_at`, `adult_confirmed_at` | Prueba de aceptación. |
| Enlaces guardados (URL + título) | `links`, **cifrados** | Pueden revelar la vida sexual → categoría especial del RGPD (art. 9). |
| Sesiones | `refresh_tokens` (solo hash) | Rotan en cada uso. |
| Pagos | **No** se guardan tarjetas | Solo `plan`, `plan_until` y eventos (`billing_events`). |
| Videos, historial, IP | **Nada** | Menos datos = menos riesgo y menos que proteger. |

## 2. Alternativas de dónde alojarlo

| Opción | Coste orientativo | Pros | Contras | Cuándo |
|---|---|---|---|---|
| **A. VPS + SQLite + Litestream** (actual) | VPS ≈ 6 €/mes + copia ≈ 1 € | Casi cero de operación, rapidísimo, datos en la UE, copia continua | Una sola máquina; escrituras de una en una | **0–5.000 usuarios** |
| **B. VPS + PostgreSQL propio** | ≈ 4–6 €/mes (mismo VPS) | Estándar, crece mucho, varias instancias de API | Tú administras copias y actualizaciones | 5.000–50.000 |
| **C. Postgres gestionado (Neon)** | Plan gratis (0,5 GB, escala a cero); de pago desde unos 5–19 $/mes | Sin administración, ramas, copias | Adquirida por Databricks; límites del plan gratis; términos sobre contenido hay que revisarlos | 50.000+ o si no quieres administrar |
| **D. Supabase (Postgres + Auth + API)** | Gratis (se **pausa** tras una semana sin uso) → ~25 $/mes | Mucho ya hecho (autenticación, API) | La pausa del plan gratis es mala para producción; acopla a su plataforma | Prototipos |
| **E. Cloudflare D1 + Workers** | Gratis: 5 GB, 5 M lecturas/día y 100 k escrituras/día; de pago ≈ 5 $/mes | Barato, global, sin servidores | Reescribir la API como Workers; techo de 10 GB por base; dependencia de Cloudflare | Si quieres «sin servidor» |
| **F. Turso (libSQL/SQLite en la nube)** | Plan gratis (el límite varía según la fuente: de 0,5 a 5 GB); de pago ≈ 5 $/mes | SQLite con réplicas, una base por usuario es posible | Servicio de terceros | Si quieres SQLite gestionado |
| **G. Firebase / DynamoDB** | Según uso | Escala enorme | Más caro de predecir, muy acoplado al proveedor, peor para consultas | No recomendado ahora |

Precios y límites **cambian**; confirma en cada web. Además: **revisa la política de uso aceptable de cada proveedor**. Algunos prohíben o restringen servicios para adultos; con la base de datos cifrada y sin contenido alojado el riesgo es menor, pero conviene preguntarlo antes de depender de uno.

**Nota sobre el VPS:** Hetzner subió precios en 2026 (el plan ARM CAX11 ronda ~6 €/mes tras junio) y a veces los planes económicos aparecen sin disponibilidad; hay alternativas europeas equivalentes (OVH, Scaleway, Contabo, etc.).

### Autenticación: propia o de terceros
| Opción | Pros | Contras |
|---|---|---|
| **Propia** (actual: correo + contraseña, JWT, refresh rotativo) | Control total, sin terceros que vean tus usuarios, 0 € | Hay que mantenerla segura (ya hay límite de intentos, scrypt, rotación) |
| **Servicio de terceros** (Auth0, Clerk, Supabase Auth…) | Menos código, MFA y recuperación de contraseña hechas | Cuestan al crecer; **tus usuarios y sus correos pasan por un tercero** (privacidad) y su política de contenido |
| **Passkeys / enlace mágico por correo** | Sin contraseñas que filtrar | Hace falta un servicio de correo transaccional |

Recomendación: mantener la propia y **añadir recuperación de contraseña por correo y, más adelante, passkeys**.

## 3. Diseño actual (opción A)

```
Móvil ──HTTPS──▶ Caddy (certificado gratis) ──▶ API FastAPI ──▶ SQLite (WAL)
                                                                   │
                                                Litestream ────────┘ copia continua ─▶ bucket barato (B2 / R2 / S3)
```
* Hospedaje en la UE (RGPD más sencillo). La API no sirve video: no hay ancho de banda caro.
* **Coste mensual estimado:** VPS ≈ 6 € · copias ≈ 1 € · dominio ≈ 1 € → **unos 8–10 €/mes** hasta varios miles de usuarios.

## 4. Seguridad (no se recorta)
* **Cifrado por usuario (envelope):** cada usuario tiene una clave de datos (AES-256-GCM) guardada cifrada con la clave maestra `BG_MASTER_KEY`; los enlaces se cifran con esa clave y quedan ligados al id del usuario. Un volcado de la base **no revela enlaces**. Al borrar la cuenta se destruye la clave: **borrado criptográfico**, también en copias antiguas.
* **Contraseñas** con scrypt; tokens de acceso de 15 min y de refresco de un solo uso, guardados solo como hash.
* **Límite de intentos**, mismo error para «correo» y «contraseña» incorrectos.
* **Webhook de pagos** firmado (HMAC-SHA256) e idempotente.
* **Sin SDK de analítica ni publicidad** dentro de la app.
* **RGPD art. 9:** los datos que revelan o permiten deducir la vida sexual son de categoría especial; la vía realista es el **consentimiento explícito** (casilla propia y registro de fecha, ya implementados), DPIA, política de privacidad, exportar (`GET /v1/me/export`) y borrar (`DELETE /v1/me`).
* **Debilidad conocida:** la clave maestra está en una variable de entorno del mismo servidor. Mejora: moverla a un KMS (AWS/GCP/Vault) para que el servidor nunca la tenga entera.

### Otros pilares
* **Fiabilidad:** copias continuas + **simulacro de restauración trimestral**; `GET /healthz` monitorizado; reinicio automático. Límite asumido: una instancia (una caída = minutos de corte; el móvil sigue reproduciendo archivos y enlaces).
* **Excelencia operativa:** todo en el repositorio (infra, pruebas, CI); configuración por variables de entorno; migraciones numeradas al pasar a Postgres.
* **Rendimiento:** consultas por índice; con el límite de enlaces por plan, la lista es pequeña; paginar si crece.
* **Sostenibilidad:** una máquina pequeña casi siempre ocupada gasta menos que una flota infrautilizada.

## 5. Camino de escalado (sin reescribir)
Toda la SQL vive en `app/db.py` y `main.py`.

| Fase | Señal | Cambio | Coste extra aprox. |
|---|---|---|---|
| **0** (0–5.000 usuarios) | — | VPS + SQLite + Litestream | ~8–10 €/mes |
| **1** (5.000–50.000) | CPU > 60 % sostenida o esperas de escritura | VPS mayor; límites de intentos en el borde (Cloudflare) | +10–30 € |
| **2** (50.000–300.000) | ≥ 2 instancias de API o despliegues sin corte | **PostgreSQL** (propio o Neon) cambiando `db.py`; Redis para límites/sesiones; clave maestra en KMS | +50–200 € |
| **3** (> 300.000) | Latencia entre continentes, picos | Réplicas de lectura, CDN, varias regiones, colas | según uso |

Cada paso se justifica por una métrica (CPU, latencia p95, errores 5xx), no por intuición.

## 6. Qué **no** hacer todavía
* Kubernetes, microservicios o bases distribuidas.
* Guardar historial de reproducción «para recomendar» (multiplica el riesgo legal).
* Alojar video propio (ancho de banda caro y responsabilidad legal directa).

## Fuentes
* Neon vs Supabase (plan gratis): https://neon.com/guides/neon-vs-supabase-free-plan
* Alternativas y D1/Turso/Postgres propio: https://dev.to/devtoolpicks/best-supabase-alternatives-for-indie-hackers-in-2026-honest-picks-2d73 · https://cskwork.github.io/free-tier-showcase/services/cloudflare-d1/
* Precios de Hetzner 2026: https://www.bitdoze.com/hetzner-cloud-cost-optimized-plans/ · https://betterstack.com/community/guides/web-servers/hetzner-cloud-review/
* RGPD art. 9 y datos inferidos: https://www.dataprotection.ie/organisations/know-your-obligations/lawful-processing/special-category-data
