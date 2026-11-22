# 02 · Suscripciones y modalidades de cobro

## 1. Modelos posibles

| Modelo | Cómo funciona | Pros | Contras | Encaje |
|---|---|---|---|---|
| **Freemium + suscripción** (el actual) | Gratis con límites; Premium mensual/anual | Fácil de entender, ingresos recurrentes, la versión gratis atrae usuarios | Hay que ganarse la conversión (2–4 % típico) | ⭐ Base |
| **Pago único (de por vida)** | 49–79 € una vez | Caja inmediata, sin cancelaciones, atrae a quien odia suscripciones | Sin ingreso recurrente; hay que seguir pagando servidor del usuario para siempre | ⭐ Oferta limitada |
| **Créditos / saldo prepago** | Compras un paquete (p. ej. 10 €) y se gasta en funciones | Menos transacciones (menos comisión), encaja con cripto y Paysafecard, sin «suscripción» | Más complejo de explicar; gasto irregular | Útil como alternativa |
| **Pago por funciones** | Desbloqueos sueltos (p. ej. perfiles de gafas, copias de seguridad) | Muy flexible | Fricción en cada compra | Complemento |
| **Códigos de licencia** | Códigos de 30 días/1 año/vida | Cero comisión de pasarela, ideal para regalos, afiliados y promociones | Fraude si se filtran | ⭐ Para arrancar |
| **Donaciones / «invítame a un café»** | Voluntario | Cero fricción | Ingreso pequeño | Complemento |
| **Publicidad en el plan gratis** | Banner discreto en la pantalla de inicio | Monetiza a quien no paga | Rinde poco; baja la confianza; nunca dentro del visor | Solo con mucha escala |
| **Licencia B2B / marca blanca** | Vendes el visor a estudios/creadores con su marca | Ticket alto | Ventas lentas, más soporte | Más adelante |

## 2. Qué limita cada plan y por qué

La app es útil sin pagar (ver y abrir videos en VR **siempre es gratis**). Lo que se limita son cosas con **coste real para ti** o **valor claro para quien más la usa**:

| | Sin cuenta | Gratis | Premium |
|---|---|---|---|
| Ver enlaces / archivos en VR | ✔ | ✔ | ✔ |
| Enlaces guardados | 5 (en el móvil) | 15 (cifrados, sincronizados) | 5.000 |
| Varios dispositivos | — | ✔ | ✔ |
| Colecciones / carpetas | — | — | ✔ (hoja de ruta) |
| Perfiles de ajuste por gafas | 1 | 1 | ilimitados (hoja de ruta) |
| Copia de seguridad cifrada exportable | — | — | ✔ (hoja de ruta) |
| Soporte | comunidad | comunidad | prioritario |

Regla: **no limites el visor.** Si el reproductor funciona mal en gratis, nadie llega a Premium; si funciona bien, el límite de enlaces y las comodidades empujan solas.

## 3. Precios para probar

* **Mensual** 4,99 € · **Anual** 29,99 € (≈2,50 €/mes, «2 meses gratis» y más) · **De por vida** 59,99 € (oferta de tiempo limitado).
* Ancla el anual: es el que más te conviene (menos comisiones, menos cancelaciones, caja por adelantado).
* **Prueba gratuita de 7 días** del plan Premium sin tarjeta (se activa desde el servidor) suele convertir mejor que pedir tarjeta de entrada.
* Experimenta con el precio con grupos distintos (3,99 / 4,99 / 6,99 €) y mide conversión × ingreso, no solo conversión.
* Paridad de poder adquisitivo: precios algo menores en países de menor renta.

## 4. Ciclo de vida de una suscripción

1. **Alta:** `checkout` → página de pago → webhook `subscription.active` con fecha `until`.
2. **Renovación:** el procesador cobra y manda otro webhook que **extiende** `until`. Si falla el cobro, deja **3–7 días de gracia** antes de bajar a gratis.
3. **Cancelación:** botón en la cuenta que lleva a la gestión del procesador; el plan sigue hasta `until` (no cortes antes de lo pagado).
4. **Reembolsos y contracargos:** el webhook de contracargo baja el plan y marca la cuenta; si un usuario acumula contracargos, se bloquea.
5. **Al bajar a gratis con más de 15 enlaces:** **no borres nada**; déjalos en solo lectura y bloquea nuevos hasta bajar del límite (evita enfados y reclamaciones).
6. **Datos al cancelar la cuenta:** borrado total (ya implementado).

Estado en la base de datos: `users.plan`, `users.plan_until` y la tabla de eventos `billing_events` (idempotencia). Lo que conviene añadir: `plan_source` (tarjeta / cripto / código), `grace_until` y `trial_used`.

## 5. Control de abusos
* **Compartir cuenta:** límite de sesiones activas (p. ej. 3 dispositivos); no hace falta DRM porque no vendes contenido.
* **Códigos:** un solo uso, caducidad, registro de quién los canjea y límite de intentos por IP.
* **Pruebas gratis repetidas:** una por correo verificado y por dispositivo; no por IP (comparten IP muchas personas).
* **Fraude con tarjetas robadas** (frecuente en el sector): el antifraude del procesador, importes pequeños los primeros días y retrasar funciones costosas hasta que pase el periodo de contracargo.

## 6. Qué mostrar al usuario
* Precio final con impuestos, periodo de renovación y cómo cancelar **antes** de pagar (lo exige la normativa de consumo y reduce reclamaciones).
* Recibo por correo con el nombre neutro del descriptor.
* Pantalla «Mi plan» con fecha de renovación (ya existe la base en `AccountActivity`).

## 7. Recomendación
Lanzar con **gratis + Premium anual/mensual + códigos**, añadir **pago único** como oferta limitada a la comunidad inicial, y medir. Si la conversión a suscripción es floja pero la gente usa la app a diario, probar **créditos prepago** (Paysafecard/cripto).
