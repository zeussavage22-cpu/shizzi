package dev.shizzi

import java.util.Locale

/**
 * Lightweight translation layer.
 *
 * Every user-facing string in the app is written in English and passed through [tr]. When the
 * device language is Spanish the Spanish text is returned; otherwise the English text is returned
 * unchanged, so the app behaves exactly as before on non-Spanish devices (and in unit tests).
 */
object I18n {

    /** True when the device's current language is Spanish (any region). */
    val isSpanish: Boolean
        get() = Locale.getDefault().language == "es"

    internal val spanish: Map<String, String> = mapOf(
        // Home / status
        "Start" to "Iniciar",
        "Stop" to "Detener",
        "Settings" to "Ajustes",
        "CANCEL" to "CANCELAR",
        "STATUS " to "ESTADO ",
        "Connected" to "Conectado",
        "Ready" to "Listo",
        "Starting" to "Iniciando",
        "Failed" to "Falló",
        "Waiting for USB" to "Esperando USB",
        "TUNNEL ACTIVE" to "TÚNEL ACTIVO",
        "Not connected" to "No conectado",
        "Connecting" to "Conectando",
        "NO SHIZUKU" to "SIN SHIZUKU",
        "SHIZUKU OFF" to "SHIZUKU APAGADO",
        "PERMISSION NEEDED" to "FALTA PERMISO",
        "VPN IGNORED" to "VPN IGNORADA",
        "VPN CONNECTED" to "VPN CONECTADA",
        "Back" to "Atrás",
        "More options" to "Más opciones",

        // Live session stats (new)
        "Devices" to "Dispositivos",
        "Downloaded" to "Descargado",
        "Uploaded" to "Subido",
        "Time" to "Tiempo",
        "Speed" to "Velocidad",
        "Total data" to "Datos totales",

        // Settings sections
        "Tethering" to "Compartir conexión",
        "Appearance" to "Apariencia",
        "Permissions" to "Permisos",
        "Advanced" to "Avanzado",
        "Developer" to "Desarrollador",
        "About" to "Acerca de",
        "Session limits" to "Límites de sesión",

        // Settings rows
        "USB tethering" to "Compartir por USB",
        "Open system settings" to "Abrir ajustes del sistema",
        "system settings" to "los ajustes del sistema",
        "Design" to "Diseño",
        "Accent" to "Color de acento",
        "Logging" to "Registro",
        "View logs" to "Ver registro",
        "Run diagnostics" to "Ejecutar diagnóstico",
        "Restart onboarding" to "Repetir configuración inicial",
        "Report a bug" to "Reportar un error",
        "Author" to "Autor",
        "the issue tracker" to "el gestor de errores",
        "Automation" to "Automatización",
        "Allows tasker apps to manage Shizzi" to "Permite que apps como Tasker controlen Shizzi",
        "View setup instructions" to "Ver instrucciones de configuración",
        "Token" to "Token",
        "Copy token" to "Copiar token",
        "Regenerate token" to "Generar nuevo token",

        // Session limits (new)
        "Stop after" to "Detener después de",
        "Data limit" to "Límite de datos",
        "Off" to "Desactivado",
        "Stop sharing automatically after this much time" to
            "Deja de compartir automáticamente tras este tiempo",
        "Stop sharing automatically once devices use this much data" to
            "Deja de compartir automáticamente al usar estos datos",
        "Time limit reached" to "Se alcanzó el límite de tiempo",
        "Data limit reached" to "Se alcanzó el límite de datos",

        // Choices
        "Auto" to "Automático",
        "Always" to "Siempre",
        "Never" to "Nunca",
        "Also share over USB when a cable is connected" to
            "También compartir por USB cuando haya un cable conectado",
        "Share over USB only; requires a cable" to "Compartir solo por USB; requiere cable",
        "Share through the hotspot only" to "Compartir solo por el punto de acceso",
        "Bind to active VPN if available" to "Usar la VPN activa si hay una",
        "Refuse connection without active VPN" to "No conectar si no hay VPN activa",
        "Ignore active VPN" to "Ignorar la VPN activa",
        "Match system" to "Igual que el sistema",
        "Light" to "Claro",
        "Dark" to "Oscuro",
        "Default" to "Predeterminado",
        "Wallpaper" to "Fondo de pantalla",
        "Hard edges, bold borders, offset shadows" to
            "Bordes rectos, contornos marcados y sombras desplazadas",
        "Rounded surfaces, tonal color, soft motion" to
            "Superficies redondeadas, color tonal y animaciones suaves",
        "Color picker" to "Selector de color",
        "Add color" to "Añadir color",

        // Shizuku / permissions
        "Status" to "Estado",
        "Service" to "Servicio",
        "Version" to "Versión",
        "Not installed" to "No instalado",
        "Not running" to "No está en ejecución",
        "Permission required" to "Permiso necesario",
        "GRANT PERMISSION" to "CONCEDER PERMISO",
        "Grant" to "Conceder",
        "Required for core functionality" to "Necesario para el funcionamiento principal",
        "Notifications" to "Notificaciones",
        "Background activity" to "Actividad en segundo plano",
        "Required to keep your session running" to "Necesario para mantener la sesión activa",
        "Required for external session management" to
            "Necesario para controlar la sesión desde otras apps",
        "Shizuku is not installed" to "Shizuku no está instalado",
        "Shizuku is installed but not running" to "Shizuku está instalado pero no está en ejecución",
        "Shizzi needs permission from Shizuku" to "Shizzi necesita permiso de Shizuku",

        // Toasts / log
        "Running diagnostics..." to "Ejecutando diagnóstico...",
        "Diagnostics completed" to "Diagnóstico completado",
        "Diagnostics failed" to "El diagnóstico falló",
        "Export" to "Exportar",
        "Export report" to "Exportar informe",
        "Log" to "Registro",
        "DESELECT ALL" to "QUITAR SELECCIÓN",
        "SELECT ALL" to "SELECCIONAR TODO",
        "CLEAR" to "BORRAR",
        "COPY ALL" to "COPIAR TODO",
        "COPY 1 LINE" to "COPIAR 1 LÍNEA",
        "Clear the log?" to "¿Borrar el registro?",
        "This action cannot be undone." to "Esta acción no se puede deshacer.",
        "Clear" to "Borrar",
        "Log cleared" to "Registro borrado",
        "Cleared this app's entries only" to "Solo se borraron las entradas de esta app",
        "No logs yet" to "Aún no hay registros",
        "Logging is disabled" to "El registro está desactivado",
        "Logs will appear here" to "Los registros aparecerán aquí",
        "Start a session" to "Iniciar una sesión",
        "Enable logging" to "Activar registro",
        "SCROLL TO TOP" to "IR AL INICIO",
        "SCROLL TO BOTTOM" to "IR AL FINAL",

        // Automation
        "Toggle" to "Alternar",
        "Setup" to "Configuración",
        "Send a broadcast intent with these values." to
            "Envía un intent de difusión (broadcast) con estos valores.",
        "Action" to "Acción",
        "Package" to "Paquete",
        "Class" to "Clase",
        "Target" to "Destino",
        "Broadcast receiver" to "Receptor de difusión",
        "Extra name" to "Nombre del extra",
        "Extra value" to "Valor del extra",

        // Onboarding
        "Welcome" to "Bienvenido",
        "Rootless wifi-tethering with Shizuku." to "Comparte tu conexión Wi-Fi sin root, con Shizuku.",
        "Get started" to "Empezar",
        "Continue" to "Continuar",
        "Grant permissions" to "Conceder permisos",
        "Compatibility" to "Compatibilidad",
        "Finish" to "Terminar",
        "Check" to "Comprobar",
        "Install" to "Instalar",
        "Installing" to "Instalando",
        "Restart" to "Reiniciar",
        "Retry" to "Reintentar",
        "Download" to "Descargar",
        "Supported" to "Compatible",
        "Not supported" to "No compatible",
        "This device is compatible" to "Este dispositivo es compatible",
        "This device is not compatible" to "Este dispositivo no es compatible",
        "Test network API" to "API de red de prueba",
        "Prefer test networks" to "Preferir redes de prueba",
        "Lets the app create the test network tunnel your hotspot traffic travels through." to
            "Permite a la app crear el túnel de red de prueba por el que pasa el tráfico del punto de acceso.",
        "Lets the app route the hotspot through the test network tunnel. Added in Android 13." to
            "Permite a la app dirigir el punto de acceso por el túnel de red de prueba. Disponible desde Android 13.",
        "Tethering module" to "Módulo de conexión compartida",
        "Restart your phone to finish" to "Reinicia el teléfono para terminar",
        "The module wasn't accepted" to "El módulo no fue aceptado",
        "Installing the module…" to "Instalando el módulo…",
        "The module is ready and will finish installing the next time your phone starts up. " +
            "Come back here afterwards to check compatibility." to
            "El módulo está listo y terminará de instalarse la próxima vez que enciendas el teléfono. " +
            "Vuelve aquí después para comprobar la compatibilidad.",
        "Your phone refused this module, which some manufacturers' builds do. Nothing was changed." to
            "Tu teléfono rechazó este módulo, algo que hacen algunos fabricantes. No se cambió nada.",
        "The module is downloaded and verified, ready to install." to
            "El módulo está descargado y verificado, listo para instalar.",
        "Downloading the module…" to "Descargando el módulo…",
        "Couldn't reach the network to download the module. Reconnect and try again." to
            "No se pudo acceder a la red para descargar el módulo. Vuelve a conectarte e inténtalo de nuevo.",
        "The download couldn't be verified, so nothing was installed." to
            "No se pudo verificar la descarga, así que no se instaló nada.",
        "Your phone needs the newer tethering module to route the hotspot. " +
            "Connect to a network to download it." to
            "Tu teléfono necesita el módulo de conexión compartida más reciente para dirigir el punto de acceso. " +
            "Conéctate a una red para descargarlo.",
        "Your phone can run this app once it has a newer tethering module. " +
            "It's about 3 MB, and installing it needs one restart." to
            "Tu teléfono podrá usar esta app cuando tenga un módulo de conexión compartida más reciente. " +
            "Ocupa unos 3 MB e instalarlo requiere un reinicio.",
        "Restart required" to "Reinicio necesario",
        "Download failed" to "La descarga falló",
        "Install failed" to "La instalación falló",
        "Downloaded and verified" to "Descargado y verificado",
        "Module available" to "Módulo disponible",

        // Notification / tile / service
        "Cleaning up…" to "Limpiando…",
        "Getting ready…" to "Preparando…",
        "Session ended" to "Sesión finalizada",
        "Connected · VPN ignored" to "Conectado · VPN ignorada",
        "Connected · VPN" to "Conectado · VPN",
        "Not sharing" to "Sin compartir",
        "Something went wrong. Check the app for details." to
            "Algo salió mal. Abre la app para ver los detalles.",
        "Turning the hotspot off…" to "Apagando el punto de acceso…",
        "Turning the hotspot on…" to "Encendiendo el punto de acceso…",
        "No devices connected" to "Ningún dispositivo conectado",
        "1 device" to "1 dispositivo",
        "Shizuku stopped, so the session ended. The hotspot was turned off. " +
            "Start again when you are ready." to
            "Shizuku se detuvo, así que la sesión terminó. El punto de acceso se apagó. " +
            "Vuelve a iniciar cuando quieras.",
        "Tethering session" to "Sesión de conexión compartida",
        "Shown while a tethering session is running" to
            "Se muestra mientras se comparte la conexión",
        "Session ended unexpectedly — dropping the hotspot…" to
            "La sesión terminó inesperadamente; apagando el punto de acceso…",
        "Stopping…" to "Deteniendo…",
        "Starting…" to "Iniciando…",
        "Tap to retry" to "Toca para reintentar",
        "Tap to share" to "Toca para compartir",
        "Shizuku not installed" to "Shizuku no instalado",
        "Shizuku not running" to "Shizuku no está activo",
        "No devices" to "Sin dispositivos",
    )
}

/** Returns the Spanish translation of [en] on Spanish devices, or [en] itself otherwise. */
fun tr(en: String): String = if (I18n.isSpanish) I18n.spanish[en] ?: en else en

/** For text built from values: returns [es] on Spanish devices, or [en] otherwise. */
fun tr(en: String, es: String): String = if (I18n.isSpanish) es else en
