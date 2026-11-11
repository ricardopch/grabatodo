package app.grabadorllamadas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Estado compartido entre servicio, widget y pantalla principal. */
object State {
    private const val P = "state"
    private fun sp(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun armed(c: Context) = sp(c).getBoolean("armed", false)
    fun setArmed(c: Context, v: Boolean) = sp(c).edit().putBoolean("armed", v).apply()

    fun recording(c: Context) = sp(c).getBoolean("recording", false)
    fun setRecording(c: Context, v: Boolean) = sp(c).edit().putBoolean("recording", v).apply()

    /** Fuente de audio que funcionó en la última grabación (para diagnóstico). */
    fun source(c: Context) = sp(c).getString("source", "") ?: ""
    fun setSource(c: Context, v: String) = sp(c).edit().putString("source", v).apply()
}

object Permissions {
    fun required(): Array<String> {
        val l = arrayListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.toTypedArray()
    }

    fun missing(c: Context): Array<String> =
        required().filter { c.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    fun canRecord(c: Context) =
        c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
