package com.bareglass.app

enum class MK { TOGGLE, BUTTON, ROW, TEXT, INPUT }

data class MockItem(val kind: MK, val title: String, val sub: String = "", val action: String = "")

data class MockScreen(
    val icon: String,
    val title: String,
    val intro: String,
    val premium: Boolean = false,
    val items: List<MockItem>
)

/** Pantallas «maqueta»: muestran la estructura prevista; casi todos sus controles aún no hacen nada. */
object Mockups {
    val all: Map<String, MockScreen> = linkedMapOf(
        "settings" to MockScreen("⚙", "Ajustes", "Preferencias del visor y de la app.", false, listOf(
            MockItem(MK.TOGGLE, "Abrir siempre en modo VR"),
            MockItem(MK.TOGGLE, "Pantalla curva por defecto"),
            MockItem(MK.TOGGLE, "Recordar la posición de cada video"),
            MockItem(MK.TOGGLE, "Ocultar la app en «recientes» (privacidad)"),
            MockItem(MK.TOGGLE, "Avisarme de novedades"),
            MockItem(MK.ROW, "Idioma", "Español"),
            MockItem(MK.ROW, "Bloqueo con PIN o huella", "", "open:pin"),
            MockItem(MK.ROW, "Dispositivos y sesiones", "", "open:devices"),
            MockItem(MK.ROW, "Copia de seguridad y mis datos", "", "open:backup"),
            MockItem(MK.ROW, "Legal y privacidad", "", "open:legal"),
            MockItem(MK.ROW, "Acerca de BareGlass", "", "open:about")
        )),
        "pin" to MockScreen("🔒", "Bloqueo con PIN", "Protege la app para que nadie más la abra.", false, listOf(
            MockItem(MK.TOGGLE, "Pedir PIN al abrir la app"),
            MockItem(MK.TOGGLE, "Usar huella o rostro"),
            MockItem(MK.TOGGLE, "Bloquear al minimizar"),
            MockItem(MK.BUTTON, "Cambiar PIN"),
            MockItem(MK.TEXT, "Cuando exista, el PIN se guardará solo en este móvil.")
        )),
        "legal" to MockScreen("⚖", "Legal y privacidad", "Textos legales y canal de denuncias (borradores).", false, listOf(
            MockItem(MK.ROW, "Términos de uso", "", "legal:terms"),
            MockItem(MK.ROW, "Política de privacidad", "", "legal:privacy"),
            MockItem(MK.ROW, "Aviso legal (Impressum)", "", "legal:impressum"),
            MockItem(MK.ROW, "Denunciar contenido", "", "open:report"),
            MockItem(MK.ROW, "Contacto", "", "legal:contact")
        )),
        "report" to MockScreen("🚩", "Denunciar contenido", "Avisa de un enlace con contenido ilegal o que infringe derechos. Se revisa cuanto antes.", false, listOf(
            MockItem(MK.INPUT, "Enlace o descripción"),
            MockItem(MK.ROW, "Contenido ilegal", "Se revisa y, si procede, se comunica a las autoridades.", "report"),
            MockItem(MK.ROW, "Infracción de derechos de autor", "", "report"),
            MockItem(MK.ROW, "Otro motivo", "", "report")
        )),
        "invite" to MockScreen("🎁", "Invitar amigos", "Cada amigo que se registre con tu código os da +10 enlaces guardables a los dos.", false, listOf(
            MockItem(MK.TEXT, "Tu código: BARE-7QX2"),
            MockItem(MK.BUTTON, "Compartir mi invitación", "", "share"),
            MockItem(MK.TEXT, "Invitaciones aceptadas: 0 · Enlaces extra ganados: 0")
        )),
        "redeem" to MockScreen("🎟", "Canjear código", "Activa Premium con un código de licencia (regalos, promociones, afiliados).", false, listOf(
            MockItem(MK.INPUT, "Escribe tu código"),
            MockItem(MK.BUTTON, "Canjear", "", "redeem"),
            MockItem(MK.TEXT, "En esta demo, el código de prueba es BARE-DEMO.")
        )),
        "collections" to MockScreen("📁", "Colecciones", "Organiza tus enlaces en carpetas.", true, listOf(
            MockItem(MK.ROW, "Favoritos", "12 enlaces"),
            MockItem(MK.ROW, "Para ver luego", "4 enlaces"),
            MockItem(MK.ROW, "Noche de cine 3D", "7 enlaces"),
            MockItem(MK.BUTTON, "+ Nueva colección")
        )),
        "profiles" to MockScreen("🥽", "Perfiles de gafas", "Guarda los ajustes que mejor se ven con cada visor.", true, listOf(
            MockItem(MK.ROW, "Cardboard genérico", "Separación −0,08 · zoom 100 % · curva"),
            MockItem(MK.ROW, "Gafas con lentes grandes", "Separación −0,05 · zoom 130 % · plana"),
            MockItem(MK.BUTTON, "+ Guardar los ajustes actuales")
        )),
        "devices" to MockScreen("📱", "Dispositivos y sesiones", "Dónde tienes la sesión abierta.", false, listOf(
            MockItem(MK.ROW, "Este móvil", "Activo ahora"),
            MockItem(MK.ROW, "Tablet", "Último uso: hace 3 días"),
            MockItem(MK.BUTTON, "Cerrar sesión en los demás dispositivos")
        )),
        "backup" to MockScreen("💾", "Copia de seguridad y mis datos", "Descarga todo lo que BareGlass guarda sobre ti.", false, listOf(
            MockItem(MK.BUTTON, "Descargar mis datos (JSON)", "", "export"),
            MockItem(MK.TEXT, "Tus enlaces se guardan cifrados. Al borrar tu cuenta se destruye la clave y no se pueden recuperar.")
        )),
        "help" to MockScreen("❓", "Ayuda e instalación", "Guías rápidas.", false, listOf(
            MockItem(MK.ROW, "Cómo instalar la app", "Descarga el APK desde la web, permite «instalar apps desconocidas» para tu navegador y ábrelo."),
            MockItem(MK.ROW, "Ajustar el visor a tus gafas", "En el visor, mantén pulsado para abrir ajustes: separación, zoom, altura y lente."),
            MockItem(MK.ROW, "Formatos 3D", "Lado a lado (SBS), arriba-abajo (TB) o 2D: elige el formato antes de pulsar «Ver en VR»."),
            MockItem(MK.ROW, "Un enlace no funciona", "Prueba con el enlace directo del archivo (.mp4, .webm, .m3u8) o genera un informe de errores.")
        )),
        "about" to MockScreen("ℹ", "Acerca de BareGlass", "Tu cine en realidad virtual.", false, listOf(
            MockItem(MK.TEXT, "Versión ${BuildConfig.VERSION_NAME}"),
            MockItem(MK.TEXT, "Reproducción con Media3 ExoPlayer. Los enlaces guardados se cifran con una clave propia de cada usuario.")
        ))
    )

    val texts: Map<String, String> = mapOf(
        "terms" to "BORRADOR — Términos de uso\n\n1. BareGlass es un visor: no aloja ni vende videos.\n2. Debes tener la edad mínima legal para usar el servicio en tu país.\n3. Está prohibido enlazar o reproducir contenido ilegal o sin autorización.\n4. Podemos cerrar cuentas que incumplan estas normas.\n\n(Texto provisional: debe revisarlo un abogado.)",
        "privacy" to "BORRADOR — Política de privacidad\n\nGuardamos tu correo, tu plan y, si lo decides, tus enlaces cifrados. No guardamos videos ni historial de reproducción. Puedes exportar y borrar tus datos desde la app.\n\n(Texto provisional: debe revisarlo un abogado.)",
        "impressum" to "BORRADOR — Aviso legal (Impressum)\n\nNombre / empresa, dirección, correo de contacto y datos de registro.\n\n(Pendiente de rellenar con tus datos.)",
        "contact" to "Contacto: soporte@bareglass.test (ejemplo)"
    )
}
