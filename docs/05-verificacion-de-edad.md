# 05 · Verificación de edad

Consultado en noviembre de 2026. No es asesoría legal.

## 1. Por qué importa
* En **Alemania**, la pornografía solo puede ofrecerse en telemedios si es accesible únicamente a adultos (**«grupo cerrado de usuarios»**), con un sistema de verificación de edad que cumpla los criterios de la **KJM**. La autodeclaración («tengo 18») **no basta**.
* En la UE, las directrices de la DSA consideran la autodeclaración generalmente insuficiente para contenido adulto.
* Los procesadores y Paysafecard también lo exigen para servicios adultos.
* Si tu servicio no aloja ni cataloga contenido, quizá no seas el «proveedor» de ese contenido, pero **no lo des por hecho**: es la pregunta principal para tu abogado (doc. 07).

## 2. Alternativas

| Opción | Cómo funciona | Coste orientativo | Pros | Contras |
|---|---|---|---|---|
| **Autodeclaración** (actual) | Casilla «tengo 18» | 0 € | Cero fricción | No cumple en Alemania/UE para contenido adulto |
| **Estimación facial** (Yoti, Didit…) | Selfie → edad estimada; si queda dudoso, se pide documento | Publicado ~0,10 $/comprobación en un proveedor (Didit); Yoti sin precio público; otros ~1–5 $ según sus propias comparativas | Rápida, sin documento en la mayoría de casos | En la KJM exige margen: el sistema de Yoti debe estimar ≥ 21 años para aceptar 18+; los precios son afirmaciones de los propios proveedores |
| **Documento + selfie (KYC)** | Foto del DNI/pasaporte y prueba de vida | ~0,33 $ publicado en un proveedor; 1–5 $ en otros | Muy fiable | Más fricción; recoges o gestionas datos sensibles (mejor que lo haga el proveedor y tú solo recibas «mayor de 18: sí/no») |
| **Identificación aprobada por la KJM** (p. ej. cidaas ID validator, aprobado en enero de 2026) | Identificación automática con IA y documento | Según proveedor | Ya aprobado en Alemania | Integración y contrato |
| **App europea de verificación de edad («mini wallet»)** | Prueba de «mayor de 18» sin revelar identidad, basada en la cartera de identidad digital de la UE | Gratis (software libre); despliegue por Estados miembros | Máxima privacidad; es el futuro | La Comisión pide a los Estados que esté disponible a finales de 2026; aún no es universal |
| **Tarjeta de crédito / banco** | El pago confirma mayoría de edad | Incluido en el cobro | Sin pasos extra para quien paga | La KJM no considera suficiente por sí solo; no cubre a los usuarios gratuitos |

*Las certificaciones y precios que citan los proveedores son afirmaciones suyas: verifica la aprobación vigente en la web de la KJM/FSM antes de contratar.*

## 3. Dónde encaja en la app

* **Hoy:** pantalla 18+ y casilla de consentimiento en el registro.
* **Con verificación certificada:**
  1. **Requerirla solo a quien crea una cuenta** (y a la primera reproducción desde un enlace si quieres máxima cautela), no a cada apertura.
  2. El proveedor devuelve un resultado firmado («mayor de 18») y **tu servidor guarda solo eso** (`age_verified_at`, `method`, `provider_ref`), nunca la foto ni el documento.
  3. Caducidad razonable (p. ej. 12 meses) o ligada al dispositivo.
* **Cambios en el código:** campo `age_verified_at` en `users`; endpoint `POST /v1/age/start` (crea la sesión con el proveedor) y `POST /v1/age/webhook` (recibe el resultado); la app abre el flujo del proveedor en el navegador/WebView y bloquea el visor de enlaces hasta recibir «verificado».
* **Sin cuenta (modo invitado):** si decides exigir verificación, el modo invitado también debe pasar por ella o quedar limitado a archivos locales.

## 4. Recomendación
1. **Ahora:** mantener la autodeclaración **solo en pruebas cerradas** y no promocionar en Alemania/UE.
2. **Antes de abrir al público:** contratar un proveedor con aprobación KJM/FSM vigente y precio claro (pide presupuesto a 2–3) e integrarlo en el registro.
3. **Cuando la app de la UE esté disponible en tus países:** ofrecerla como método principal (gratis y respetuosa con la privacidad) y dejar la estimación facial como alternativa.
4. **Alternativa de arranque barata:** limitar la distribución por país (bloqueo geográfico en la web) a lugares donde la autodeclaración sea suficiente según tu abogado.

## Fuentes
* Directrices DSA y JMStV (grupo cerrado de usuarios): https://www.taylorwessing.com/de/interface/2025/online-and-ai-generated-content/minors-platforms-and-harmful-content · https://merlin.obs.coe.int/download/9271/pdf
* KJM aprueba sistema de identificación (enero 2026): https://merlin.obs.coe.int/article/10482
* Yoti, aprobación KJM y margen de 3 años: https://www.yoti.com/blog/age-estimation
* Comparativa de precios del sector (afirmaciones de proveedor): https://didit.me/blog/top-age-verification-software-alternatives-2026/
* Aplicación europea de verificación de edad: https://digital-strategy.ec.europa.eu/en/news/commission-urges-member-states-rollout-eu-age-verification-app
