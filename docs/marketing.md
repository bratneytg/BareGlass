# Estrategia de marketing y publicidad

## 0. Realidad del terreno (condiciona todo)

* **Google Play no admite apps de pornografía**: su política prohíbe apps que contengan o promocionen contenido sexual, incluida la pornografía, o servicios cuya finalidad sea el placer sexual. No intentes entrar «disfrazando» la app: te vetarán la cuenta de desarrollador (y con ella tus otras apps). Distribución propia → ver `legal-y-distribucion.md`.
* **Meta, Google Ads y TikTok no sirven anuncios de contenido adulto.** La publicidad de pago va por **redes de anuncios para adultos** (p. ej. ExoClick, TrafficJunky, JuicyAds, TrafficStars; revisa sus condiciones y tarifas actuales) y por acuerdos directos con webs/creadores.
* El comprador es **privacidad-sensible**: «discreción» es una característica de producto y el mejor argumento de venta.

## 1. Posicionamiento

**Promesa:** *«Tu visor privado: pega un enlace, ponte las gafas. Sin cuentas ajenas, sin rastro, sin anuncios en tu contenido.»*

Diferenciales reales de la app (todos ya construidos):
1. Visor VR con seguimiento de cabeza y menú con la mirada: no necesitas tocar el móvil con las gafas puestas.
2. Pantalla curva con zoom, ajuste de altura y separación estéreo para distintos móviles/gafas.
3. Pegas *cualquier* enlace o abres archivos locales.
4. **Privacidad**: enlaces cifrados con clave por usuario, sin analítica de terceros, borrado total en un toque.
5. Funciona con gafas Cardboard baratas (hardware de ~10–20 €) → barrera de entrada baja.

Tono de marca: sensual, elegante, adulto; sin explicitud en el material promocional público (te protege en redes y buscadores y atrae a un público con más poder adquisitivo que el «tube» genérico).

## 2. Embudo y métricas

`Visita web → instalación del APK → primer video en VR (activación) → registro → guardar enlaces → Premium`

| Métrica | Objetivo inicial | Por qué |
|---|---|---|
| Instalación / visita a la web | 15–25 % | La instalación manual de APK tiene fricción; la landing debe explicarla con capturas. |
| Activación (primer video visto en < 24 h) | > 50 % de las instalaciones | Es la señal de que el producto funciona. |
| Retención día 7 | > 20 % | Normal en utilidades. |
| Registro | 25–40 % de activos | El límite de 5 enlaces sin cuenta lo empuja. |
| Conversión a Premium | 2–4 % de registrados | Referencia prudente para freemium. |
| CAC (coste por usuario activado) | < 1/3 del ingreso de 12 meses del usuario | Regla sana para no perder dinero con la publicidad. |

Mide con **eventos propios y anónimos** en tu servidor (sin SDK de terceros) o con la analítica de la web (Plausible/Umami, sin cookies).

## 3. Canales, de más barato a más caro

### A. Orgánico y gratuito (empezar aquí)
* **Web propia con landing + guía de instalación** (el activo más importante: es tu «tienda»). Páginas de SEO sin contenido explícito: «cómo ver videos en VR con gafas Cardboard», «mejores ajustes para móvil 6"», «diferencia 180° / lado a lado / arriba-abajo». Esas búsquedas existen y no tienen competencia fuerte.
* **YouTube / TikTok / Instagram con contenido *neutro***: tutoriales de configuración de gafas VR, reseñas de gafas baratas, demos con material libre de derechos. Enlazan a la landing. No muestres contenido adulto: te cierran el canal.
* **Comunidades de VR y de hardware** (foros, Discord, subreddits de Cardboard/VR): ayuda genuina, aporta valor, sigue las normas de autopromoción de cada una. Para comunidades NSFW, pide permiso a los moderadores y respeta sus reglas.
* **X (Twitter) y Telegram**: permiten contenido adulto etiquetado. Canal propio de novedades + grupo de soporte; publica trucos, actualizaciones y sorteos de licencias Premium.
* **Programa de invitación**: cada invitado que se registre = +10 enlaces guardables para ambos (cuesta ~0 €, el servidor aguanta de sobra).

### B. Alianzas (coste bajo, mejor conversión)
* **Creadores y performers que quieran una vía legal y privada de distribuir su VR**: ofréceles un código de afiliación (p. ej. 30 % del primer año de cada suscriptor que traigan). Pagas solo por resultados.
* **Reseñadores de gafas VR / gadgets**: envío de licencias Premium gratis a cambio de reseña honesta.
* **Tiendas de gafas Cardboard**: insertar una tarjeta «descarga BareGlass» en el paquete.

### C. Publicidad de pago (cuando la activación esté demostrada)
* Redes de anuncios para adultos con **pujas por instalación/registro**, empezando con **150–300 €/mes** de prueba:
  1. 3 creatividades sugerentes pero no explícitas × 3 textos × 2 países.
  2. Segmentar por dispositivo Android, país y franja horaria (tarde-noche).
  3. Medir **coste por activación**, no por clic.
  4. Matar lo que no funcione en 7 días; duplicar lo que baje del CAC objetivo.
* No hagas publicidad engañosa («gratis y sin límites»…) ni te dirijas a menores. Es ilegal y te baja de las redes.

### D. Retención (barato y rentable)
* Correo breve a los 3 días si no han visto ningún video; notificación opcional de novedades; página de «novedades» en la app.

## 4. Plan de 90 días

| Semanas | Hito |
|---|---|
| 1–2 | Landing + política de privacidad + términos + Impressum; APK firmado y descargable; analítica sin cookies. Invitar a 20–50 beta testers (foros, amigos). |
| 3–4 | Arreglos según beta; 5 artículos SEO y 3 vídeos tutorial neutros; cuenta en X y canal de Telegram. |
| 5–8 | Programa de invitación y de afiliados; 5–10 reseñas; empezar publicidad de prueba (150 €). |
| 9–12 | Activar pagos (procesador aprobado); primer Premium; subir presupuesto solo en el canal con mejor CAC; decidir si pagar verificación de edad certificada para abrir Alemania/UE de forma legal. |

## 5. Riesgos de marca
* Antivirus/Play Protect pueden avisar al instalar un APK fuera de la tienda: explica cada paso en la landing y firma siempre con la misma clave.
* Reputación: una sola queja de contenido ilegal enlazado debe tener respuesta en horas (ver política de contenido en `legal-y-distribucion.md`).
