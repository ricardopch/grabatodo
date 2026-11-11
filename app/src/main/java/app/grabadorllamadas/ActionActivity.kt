package app.grabadorllamadas

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Actividad transparente que abre el widget: comprueba permisos y, estando visible,
 * lanza el servicio en primer plano (Android no permite arrancar uno de micrófono desde segundo plano).
 */
class ActionActivity : Activity() {

    private var action: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        action = intent?.action
        val missing = Permissions.missing(this)
        if (missing.isEmpty()) go() else requestPermissions(missing, 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        go()
    }

    private fun go() {
        if (Permissions.canRecord(this) && action != null) {
            startForegroundService(RecorderService.intent(this, action!!))
        } else {
            Toast.makeText(this, "Falta el permiso de micrófono", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
