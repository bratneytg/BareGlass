# Estrategias de ingresos

## 1. Principio
Cobrar por **conveniencia y privacidad**, no por contenido ajeno. La app no vende videos; vende un buen visor y un espacio privado sincronizado.

## 2. Modelo principal: freemium con suscripción

| | Sin cuenta | Gratis (cuenta) | **Premium** |
|---|---|---|---|
| Ver enlaces y archivos en VR | ✔ | ✔ | ✔ |
| Enlaces guardados | 5 (solo en el móvil) | 15 (cifrados, sincronizados) | **5.000** |
| Sincronización entre dispositivos | — | ✔ | ✔ |
| Funciones extra (hoja de ruta) | — | — | colecciones, perfiles de ajuste por gafas, notas, copia de seguridad cifrada, soporte prioritario |

Precios de partida para probar (ajustar con datos): **4,99 €/mes · 29,99 €/año · 59,99 € de por vida** (el pago único trae caja rápida y reduce cancelaciones; ofrécelo de forma limitada).

Ya está implementado: los límites por plan, `POST /v1/billing/checkout` (abre la página de pago del procesador) y `POST /v1/billing/webhook` (el procesador avisa y la cuenta pasa a Premium con fecha de caducidad).

## 3. Cobros: qué esperar
* Los grandes (Stripe, PayPal, Google/Apple) **no suelen aceptar** este negocio. Se usan procesadores especializados en contenido adulto/alto riesgo: CCBill, Segpay, Epoch, Verotel y similares. Todos clasifican el sector como de alto riesgo.
* Comisiones **más altas que la media** (como referencia pública, CCBill indica tarifas desde ~5,9 % + 0,55 USD por transacción para cuentas de alto riesgo; cada caso se cotiza) y **contracargos** frecuentes: reserva ~10–12 % de los ingresos para comisiones + devoluciones al calcular márgenes.
* Pasos: solicitar cuenta de comerciante (necesitarán tu empresa o autónomo, web con términos, política de reembolso, aviso 18+ y descripción del producto) → configurar un «formulario alojado» → copiar su URL en `BG_CHECKOUT_URL` → configurar el webhook hacia `/v1/billing/webhook` (hay que adaptar el formato del evento del procesador elegido; el esquema actual es genérico: `event_id`, `type`, `user_id`, `until`).
* Descriptor discreto en el extracto bancario (nombre neutro): es una exigencia de los propios usuarios y reduce contracargos por «no reconozco este cargo».
* Cripto (opcional, más adelante): BTC/stablecoins con un proveedor de pagos cripto para quien valore el anonimato.

## 4. Ingresos adicionales (ordenados por esfuerzo)

1. **Afiliación de hardware:** gafas Cardboard/VR, mandos Bluetooth, auriculares. Enlaces de afiliado en la web y en un apartado «Equipo recomendado». Sin riesgo y con margen decente.
2. **Afiliación de privacidad:** VPN y gestores de contraseñas con programas de afiliados (encajan con el público).
3. **Programa de revendedores/creadores:** el creador cobra un % de cada suscripción que traiga; tú pagas solo si vende.
4. **Publicidad propia y discreta** (opcional, solo en plan gratis): banner nativo de una red para adultos en la pantalla de inicio, **nunca durante la reproducción** ni dentro del visor. Rinde poco por usuario y puede dañar la confianza; probar solo cuando haya decenas de miles de usuarios.
5. **Catálogo con licencia (fase 2, el de mayor potencial):** acuerdos con estudios y creadores independientes que ofrezcan contenido VR propio dentro de la app con reparto de ingresos (p. ej. 70/30 a su favor). Resuelve de raíz el problema de «¿de dónde saco videos?», aporta contenido verificado y legal, y justifica un precio mayor. Exige verificación de edad certificada, comprobación de identidad y consentimiento de los intérpretes, y moderación — es otro negocio, con otras obligaciones legales.
6. **Propinas / «comprar un café»** en la web: ingreso pequeño, coste cero.

## 5. Ejemplo de números (hipótesis ilustrativas, no promesas)

Supuestos: 10.000 instalaciones activas · 30 % se registra (3.000) · 3 % de los registrados paga (90) · precio medio efectivo 4 €/mes tras descuentos anuales.

* Ingresos brutos: 90 × 4 € = **360 €/mes**
* Comisiones + contracargos (~12 %): −43 €
* Infraestructura: −10 €
* **Neto: ≈ 307 €/mes**

Con 100.000 instalaciones activas y el mismo embudo: ≈ 3.070 €/mes. Los palancas que más pesan son la **activación** (que vean un video en VR el primer día), la **conversión a registro** y la **retención** — por eso el plan de marketing mide esos tres antes de gastar en publicidad.

## 6. Qué medir cada mes
Ingreso mensual recurrente (MRR), cancelaciones, contracargos (% de transacciones; los procesadores penalizan por encima de ~1 %), CAC por canal, y relación valor-del-usuario / CAC (> 3 es sano).
