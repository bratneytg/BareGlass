# 01 · Pagos

Consultado en noviembre de 2026. Verifica tarifas y condiciones antes de decidir.

## 1. El problema de fondo

Cobrar por software orientado a público adulto es **«alto riesgo»** para casi todos los procesadores: más contracargos, más normas de las marcas de tarjeta y más riesgo reputacional. Por eso las pasarelas «de moda» suelen estar cerradas, y las que funcionan cobran más. Además, **Google Play y App Store no son una opción** (ver documento 07), así que los pagos se hacen **en tu web**, no dentro de la app: la app abre una página de pago y, al terminar, el servidor activa el plan por *webhook*.

## 2. Alternativas

### A. Procesadores especializados en adultos / alto riesgo
CCBill, Segpay, Epoch, Verotel y similares. CCBill indica que atiende «streaming websites & apps» y modelos de suscripción con recurrencia, y que cubre más de 90 modelos de alto riesgo. Como referencia pública, hay guías que citan tarifas de partida de ~5,9 % + 0,55 USD por transacción en alto riesgo; cada cuenta se cotiza.

* ➕ Suscripciones recurrentes, formularios alojados, gestión de contracargos y antifraude, descriptor discreto, experiencia con el sector.
* ➖ Comisiones altas (calcula **10–15 % todo incluido** con contracargos y reservas), aprobación con revisión de tu negocio, y exigen aviso 18+, términos y política de reembolso.
* 🧭 Es la ruta «normal» y la que mejor convierte con tarjeta.

### B. Cripto autoalojada (BTCPay Server)
Software libre y autoalojado, sin comisiones de procesador (solo la red) y sin intermediario; se puede montar en un VPS de menos de 10 $/mes. La versión 2.3 (dic. 2025) añadió **suscripciones**, con renovación manual o con saldo precargado que se descuenta del plan.

* ➕ 0 % de comisión, sin contracargos, máxima privacidad para el usuario, nadie puede cerrarte la cuenta.
* ➖ Poca gente paga con cripto (conversión baja), volatilidad (cobra en stablecoin o convierte rápido), y **fiscalidad** propia (consulta a tu asesor fiscal en Alemania).
* 🧭 Excelente como **segunda vía**, no como única.

### C. Prepago (Paysafecard)
Tarjeta prepago de 16 dígitos que se compra en tienda o en línea; es habitual en juegos, apuestas y contenido adulto, y al ser prepago **no tiene contracargos**. Condiciones importantes: no admite reembolsos a la tarjeta, **no acepta como comerciantes a particulares** (hay que estar registrado al menos como autónomo/empresa) y, para servicios adultos, **exige una verificación de edad reconocida** (la autodeclaración no vale).

* ➕ Muy usado en Alemania, sin tarjeta ni datos bancarios, sin contracargos.
* ➖ Requiere alta como comerciante vía un proveedor de pagos (Mollie, Adyen, Worldline…; comprueba que acepte tu sector) y verificación de edad.
* 🧭 Muy buena opción para el mercado alemán una vez tengas verificación de edad (documento 05).

### D. Códigos de licencia / vales
Generas códigos (30 días, 1 año, de por vida) y los vendes tú o a través de **revendedores, afiliados y creadores**; el usuario los canjea en la app. Tu servidor solo valida el código.

* ➕ Cero comisión de pasarela en tus ventas directas, ideal para sorteos, promociones, regalos y para «pagar» a creadores con licencias.
* ➖ Hay que evitar fraude (códigos filtrados) y registrar cada código.
* 🧭 Barato de construir (una tabla `codes` y un endpoint `POST /v1/redeem`); muy útil para el arranque.

### E. Domiciliación SEPA / transferencia
Muy extendida en Alemania y barata, pero lenta, con devoluciones de hasta semanas y datos bancarios del usuario en tu sistema. Solo con un proveedor que acepte tu sector. Poco recomendable al inicio.

### F. Plataformas que **no** sirven para este negocio
* **Stripe**: declara que actualmente no puede trabajar con negocios que vendan u ofrezcan contenido o servicios para adultos.
* **Paddle**: su política prohíbe «contenido y servicios para adultos y otros restringidos por edad», incluidos productos sexualmente orientados.
* **Lemon Squeezy**: prohíbe el contenido sexual o pornográfico «de cualquier tipo».
* **Google Play / Apple**: ver documento 07.

> ⚠️ **No intentes entrar «disfrazando» el negocio** (por ejemplo describiendo la app como un simple reproductor en esas plataformas mientras la promocionas como app para adultos). Si te descubren, cierran la cuenta y **retienen los fondos pendientes**. Hazlo con la verdad: o un procesador que acepte tu sector, o un producto realmente distinto con su propia web y su propia comunicación.

### G. Acuerdos con terceros que cobran por ti
Si alianzas con una web o creador que ya tiene su cobro, ellos venden y tú recibes un reparto (p. ej. 70/30). Menos control, menos trabajo, menos riesgo de cuenta propia.

## 3. Cómo se conecta con el código actual

Ya existe: `POST /v1/billing/checkout` (devuelve la URL de pago) y `POST /v1/billing/webhook` (firmado con HMAC; eventos `subscription.active` / `subscription.cancelled`; idempotente por `event_id`). La app abre el pago en el navegador y el usuario pulsa «Actualizar estado de mi plan».

Qué falta para cada vía:

| Vía | Trabajo |
|---|---|
| Procesador de adultos | Un *adaptador* que traduzca el formato de webhook del procesador al evento interno (cada uno tiene el suyo) y mapee `client_ref` → `user_id`. |
| BTCPay | Webhook de «factura liquidada» → `subscription.active` con `until = ahora + 30/365 días`; botón «renovar» (pago único por periodo). |
| Paysafecard | Se integra a través del proveedor de pagos; misma lógica de webhook. |
| Códigos | Tabla `codes(code, days, used_by, used_at)` + `POST /v1/redeem`; generador de códigos en lote. |

## 4. Dinero: cómo calcularlo bien
* **Comisión real** = tarifa + fija por transacción + **contracargos** (en adultos son frecuentes: reserva ~10 %) + reservas retenidas. Pide una simulación al procesador.
* **Descriptor en el extracto**: nombre neutro y reconocible; reduce el «no reconozco este cargo».
* **Cancelar en un toque** (en la app) baja los contracargos más que cualquier antifraude.
* **Planes anuales** → menos transacciones, menos comisión fija, menos cancelaciones.
* **Impuestos**: vender servicios digitales a consumidores de la UE implica IVA del país del comprador (ventanilla única OSS); en Alemania necesitas alta de actividad. Habla con un *Steuerberater* **antes** de cobrar; los procesadores te pedirán empresa o autónomo registrado.

## 5. Recomendación por fases

1. **Antes de lanzar (0 €):** gratis + **códigos de licencia** para los primeros usuarios y colaboradores.
2. **Primeras semanas:** BTCPay (cripto) como vía privada, y **solicitud en paralelo** a 2 procesadores de adultos (por si uno rechaza o tarda).
3. **Con verificación de edad lista:** añadir Paysafecard y tarjeta.
4. **Al crecer:** negociar tarifas, ofrecer planes anuales con descuento y valorar un segundo procesador de respaldo (no depender de uno solo).

## Fuentes
* CCBill – negocio adulto: https://CCBILL.com/industries/adult-business · tarifas de referencia: https://www.cardfellow.com/blog/credit-card-processing-adult-entertainment
* Stripe, negocios no admitidos: https://support.stripe.com/questions/restricted-business-list-faqs
* Paddle, qué no se puede vender: https://www.paddle.com/help/start/account-verification/what-am-i-not-allowed-to-sell-on-paddle
* Lemon Squeezy, productos prohibidos: https://docs.lemonsqueezy.com/help/getting-started/prohibited-products
* BTCPay Server: https://docs.btcpayserver.org/BTCPayVsOthers/ · suscripciones 2.3: https://blog.btcpayserver.org/btcpay-server-2-3-0/
* Paysafecard (condiciones para comerciantes y adultos): https://docs.connect.worldline-solutions.com/payment-product/paysafecard/additional-information · sin contracargos y uso en adultos: https://docs.axepta.bnpparibas/x/UwBH
