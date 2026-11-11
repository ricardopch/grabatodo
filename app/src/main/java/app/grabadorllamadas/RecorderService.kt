package app.grabadorllamadas

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Servicio en primer plano (micrófono). Cuando está "armado" escucha el estado del teléfono
 * y empieza a grabar al descolgar / terminar la grabación al colgar. También permite grabar a mano.
 */
class RecorderService : Service() {

    companion object {
        const val ACTION_TOGGLE_ARM = "app.grabadorllamadas.TOGGLE_ARM"
        const val ACTION_TOGGLE_REC = "app.grabadorllamadas.TOGGLE_REC"
        private const val CHANNEL = "rec"
        private const val NOTIF_ID = 1
        const val REL_PATH = "Music/Grabaciones de llamadas"

        fun intent(ctx: Context, action: String): Intent =
            Intent(ctx, RecorderService::class.java).setAction(action)
    }

    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var autoStarted = false
    private var receiverRegistered = false

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_OFFHOOK ->
                    if (State.armed(c) && recorder == null) {
                        autoStarted = startRecording()
                    }
                TelephonyManager.EXTRA_STATE_IDLE ->
                    if (recorder != null && autoStarted) stopRecording()
            }
            afterChange()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        when (intent?.action) {
            ACTION_TOGGLE_ARM -> arm(!State.armed(this))
            ACTION_TOGGLE_REC -> {
                if (recorder == null) {
                    autoStarted = false
                    startRecording()
                } else {
                    stopRecording()
                }
            }
        }
        afterChange()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        unregisterPhone()
        State.setArmed(this, false)
        State.setRecording(this, false)
        RecorderWidget.update(this)
        super.onDestroy()
    }

    // ---------- escucha de llamadas ----------

    private fun arm(v: Boolean) {
        State.setArmed(this, v)
        if (v) registerPhone() else unregisterPhone()
    }

    private fun registerPhone() {
        if (receiverRegistered) return
        val f = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(phoneReceiver, f, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(phoneReceiver, f)
        }
        receiverRegistered = true
    }

    private fun unregisterPhone() {
        if (!receiverRegistered) return
        try { unregisterReceiver(phoneReceiver) } catch (_: Exception) {}
        receiverRegistered = false
    }

    // ---------- grabación ----------

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()

    private fun startRecording(): Boolean {
        if (!Permissions.canRecord(this)) return false
        val name = "Llamada_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, REL_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val u = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val fd = contentResolver.openFileDescriptor(u, "w")
        if (fd == null) {
            contentResolver.delete(u, null, null)
            return false
        }

        // Se prueba de mejor a peor: audio de la llamada completa (solo algunos móviles/ROMs),
        // luego reconocimiento de voz y por último micrófono normal (útil con altavoz).
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL (ambos lados)",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.MIC to "MIC"
        )
        for ((src, label) in sources) {
            val r = newRecorder()
            try {
                r.setAudioSource(src)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(128000)
                r.setAudioSamplingRate(44100)
                r.setOutputFile(fd.fileDescriptor)
                r.prepare()
                r.start()
                recorder = r
                pfd = fd
                uri = u
                State.setRecording(this, true)
                State.setSource(this, label)
                return true
            } catch (e: Exception) {
                try { r.release() } catch (_: Exception) {}
            }
        }
        try { fd.close() } catch (_: Exception) {}
        contentResolver.delete(u, null, null)
        return false
    }

    private fun stopRecording() {
        val r = recorder ?: return
        try { r.stop() } catch (_: Exception) {}
        try { r.release() } catch (_: Exception) {}
        recorder = null
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
        uri?.let {
            val v = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            contentResolver.update(it, v, null, null)
        }
        uri = null
        autoStarted = false
        State.setRecording(this, false)
    }

    // ---------- notificación / widget ----------

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Grabación de llamadas", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    private fun buildNotification(): Notification {
        val text = if (recorder != null) "Grabando…" else "Esperando llamadas"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Grabador de llamadas")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun afterChange() {
        RecorderWidget.update(this)
        if (!State.armed(this) && recorder == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        }
    }
}
