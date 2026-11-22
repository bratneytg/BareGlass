# Legal y distribución — lo que hay que resolver antes de promocionar

> No soy abogado y esto no es asesoría legal. Es un mapa de los puntos de riesgo que debe revisar un abogado de medios/protección de datos de tu país antes de lanzar.

## 1. Dónde se puede (y no se puede) distribuir
* **Google Play: no.** Su política prohíbe apps que contengan o promocionen pornografía o servicios de finalidad sexual. Intentarlo arriesga la cuenta de desarrollador.
* **Opciones reales:** descarga directa del APK desde tu web (firmado siempre con la misma clave), tiendas alternativas de Android que acepten contenido adulto (revisa sus condiciones una a una) y, más adelante, una app web (PWA) para quien no quiera instalar APK. Para publicar actualizaciones, la app puede comprobar una versión en tu servidor.
* Compilación de publicación: crear un *keystore* propio, **guardarlo en un sitio seguro con copia** (si lo pierdes no podrás actualizar la app) y firmar el APK de release (`assembleRelease`). Hoy el CI genera un APK de pruebas.
* **Código público.** El repositorio `BareGlass` está público. Considera ponerlo **privado** si no quieres que la competencia lea tu código o que el nombre del proyecto aparezca en buscadores asociado a contenido adulto.

## 2. Verificación de edad
* La app actual usa una **declaración propia** («tengo 18 años») y una casilla de consentimiento. Es lo mínimo y **puede no bastar**:
  * En **Alemania**, el JMStV (art. 4.2) solo permite pornografía en telemedios si el proveedor asegura que es accesible únicamente a adultos (**«grupo cerrado de usuarios»**) mediante un sistema de verificación de edad que cumpla los criterios de la KJM; existen sistemas aprobados por la KJM (por ejemplo, de identificación automática o por vídeo). La KJM puede ordenar el bloqueo de servicios sin verificación, también de proveedores de fuera de Alemania que se dirigen al país.
  * La Comisión Europea, en sus directrices del art. 28 de la DSA, considera que la **autodeclaración suele ser insuficiente** para contenido adulto.
  * Otros países (Reino Unido, Francia y varios estados de EE. UU., entre otros) también exigen verificación.
* **Recomendación:** (a) hasta tener verificación certificada, **no promociones ni distribuyas en Alemania/UE** ni en países con exigencia clara; (b) integrar un proveedor de verificación (el código está preparado: el registro pasa por un único punto, `POST /v1/auth/register`, donde añadir el paso «verificado»); (c) bloquear por país en la web de descarga.
* Pregunta clave para tu abogado: si un **reproductor que no aloja ni cataloga contenido** cuenta como proveedor de telemedios con contenido pornográfico. Cuanto más «catálogo» o recomendaciones añadas, más cerca estás de serlo. Por eso el diseño actual no incluye ningún buscador ni lista de sitios.

## 3. Contenido de terceros
* El usuario pega enlaces; BareGlass no los aloja ni los indexa. Aun así:
  * **Términos de uso** que prohíban enlazar material ilegal (en especial cualquier contenido con menores o sin consentimiento) y cláusula de cancelación de cuenta.
  * **Tolerancia cero con material de abuso sexual infantil**: canal de denuncia visible; si recibes una denuncia o lo detectas, no lo reenvíes y repórtalo a las autoridades/línea de denuncia nacional (red INHOPE).
  * **Procedimiento de aviso y retirada** (notice-and-takedown) y dirección de contacto para titulares de derechos.
  * Los enlaces guardados van **cifrados**: tú no los ves (es bueno para la privacidad); decide con tu abogado qué puedes y debes hacer ante una orden legal.

## 4. Protección de datos (RGPD)
* Los enlaces guardados pueden revelar **vida sexual / orientación sexual**: categoría especial del art. 9. Incluso datos que solo permiten *deducirlo* cuentan (jurisprudencia del TJUE). Necesitas **consentimiento explícito** (hecho: casilla separada y registro de fecha), **DPIA**, registro de tratamientos y política de privacidad con lenguaje claro.
* Derechos ya implementados: acceso/portabilidad (exportar) y supresión (borrar cuenta con borrado criptográfico).
* En Alemania: **Impressum** completo en la web y en la app, y encargado de protección de datos si te corresponde por tamaño.
* Encargados del tratamiento: contrato de tratamiento de datos (DPA) con el hosting, el bucket de copias y el procesador de pagos.

## 5. Pagos
Procesador especializado, con **descriptor discreto**, política de reembolso y baja fácil (cancelar en un toque reduce contracargos). Cumplir con los requisitos de las marcas de tarjeta para comercios de alto riesgo.

## 6. Marca
Comprobar que «BareGlass» está libre como marca en la UE/EE. UU. y que el dominio y las cuentas sociales están disponibles antes de invertir en publicidad.

## 7. Lista de comprobación previa al lanzamiento
- [ ] Revisión por abogado (verificación de edad, términos, privacidad, DPIA)
- [ ] Verificación de edad certificada o restricción por países
- [ ] Impressum, términos, política de privacidad, canal de denuncias
- [ ] DPA con proveedores · registro de tratamientos
- [ ] Keystore de release guardado y APK firmado
- [ ] Cuenta de procesador de pagos aprobada · webhook probado
- [ ] Simulacro de restauración de la base de datos
- [ ] Monitorización de `/healthz`
