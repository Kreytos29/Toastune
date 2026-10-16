package app.toastune.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Informe para entender por qué la app se cerró o dejó de responder. Android guarda los últimos
 * cierres de cada app (incluida la pila del hilo principal cuando fue un "no responde"); aquí se
 * juntan en un texto que el usuario puede guardar y enviar.
 */
object Diagnostics {

    fun report(context: Context): String = buildString {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        appendLine("Toastune $version")
        appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        if (Build.VERSION.SDK_INT < 30) {
            appendLine("Este Android no guarda el historial de cierres (hace falta Android 11 o más).")
            return@buildString
        }
        val am = context.getSystemService(ActivityManager::class.java)
        val exits = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 10) }.getOrDefault(emptyList())
        if (exits.isEmpty()) appendLine("Sin cierres registrados.")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        exits.forEach { e ->
            appendLine("— ${fmt.format(Date(e.timestamp))} · ${reasonName(e.reason)} · proceso ${e.processName} · ${e.description.orEmpty()}")
        }
        // La pila completa solo existe para "no responde"; con eso se ve qué tenía ocupado el hilo principal.
        exits.firstOrNull { it.reason == ApplicationExitInfo.REASON_ANR }?.let { anr ->
            appendLine()
            appendLine("===== Pila del último \"no responde\" =====")
            val trace = runCatching {
                anr.traceInputStream?.bufferedReader()?.use { it.readText().take(300_000) }
            }.getOrNull()
            appendLine(trace ?: "(Android no guardó la pila)")
        }
    }

    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_ANR -> "NO RESPONDE (ANR)"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH NATIVO"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "POCA MEMORIA"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "CERRADA POR EL USUARIO"
        ApplicationExitInfo.REASON_USER_STOPPED -> "DETENIDA POR EL USUARIO"
        ApplicationExitInfo.REASON_SIGNALED -> "MATADA POR EL SISTEMA (señal)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "USO EXCESIVO DE RECURSOS"
        ApplicationExitInfo.REASON_EXIT_SELF -> "SE CERRÓ SOLA"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCIA MUERTA"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "FALLO AL INICIAR"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "CAMBIO DE PERMISOS"
        else -> "OTRO ($r)"
    }
}
