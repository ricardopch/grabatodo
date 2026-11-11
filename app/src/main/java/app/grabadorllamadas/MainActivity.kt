package app.grabadorllamadas

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private data class Rec(val uri: Uri, val label: String)

    private lateinit var status: TextView
    private lateinit var armBtn: Button
    private lateinit var recBtn: Button
    private lateinit var list: ListView
    private val recs = ArrayList<Rec>()
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        status = TextView(this).apply { textSize = 16f }
        armBtn = Button(this)
        recBtn = Button(this)
        val hint = TextView(this).apply {
            textSize = 12f
            text = "Consejo: durante la llamada activa el altavoz; en muchos móviles Android solo deja " +
                "grabar tu voz con el micrófono normal. Avisa siempre a tu interlocutor de que grabas."
            setPadding(0, dp(8), 0, dp(8))
        }
        val title = TextView(this).apply {
            text = "Grabaciones (Música/Grabaciones de llamadas)"
            textSize = 14f
            gravity = Gravity.START
        }
        list = ListView(this)

        root.addView(status)
        root.addView(armBtn)
        root.addView(recBtn)
        root.addView(hint)
        root.addView(title)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        armBtn.setOnClickListener { send(RecorderService.ACTION_TOGGLE_ARM) }
        recBtn.setOnClickListener { send(RecorderService.ACTION_TOGGLE_REC) }
        list.setOnItemClickListener { _, _, pos, _ -> togglePlay(recs[pos].uri) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setMessage("¿Borrar esta grabación?")
                .setPositiveButton("Borrar") { _, _ ->
                    contentResolver.delete(recs[pos].uri, null, null)
                    refresh()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }

        val missing = Permissions.missing(this)
        if (missing.isNotEmpty()) requestPermissions(missing, 1)
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        super.onPause()
    }

    private fun send(action: String) {
        if (!Permissions.canRecord(this)) {
            Toast.makeText(this, "Concede el permiso de micrófono", Toast.LENGTH_LONG).show()
            requestPermissions(Permissions.missing(this), 1)
            return
        }
        startForegroundService(RecorderService.intent(this, action))
        handler.postDelayed({ refresh() }, 400)
    }

    private fun refresh() {
        val armed = State.armed(this)
        val rec = State.recording(this)
        val src = State.source(this)
        status.text = (if (armed) "Escucha de llamadas: ACTIVADA" else "Escucha de llamadas: desactivada") +
            (if (rec) "\n● Grabando ahora" else "") +
            (if (src.isNotEmpty()) "\nÚltima fuente de audio: $src" else "")
        armBtn.text = if (armed) "Desactivar escucha automática" else "Activar escucha automática"
        recBtn.text = if (rec) "■ Parar grabación" else "● Grabar ahora"

        recs.clear()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION
        )
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("$REL%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val secs = c.getLong(2) / 1000
                recs.add(
                    Rec(
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        "${c.getString(1)}  (${secs / 60}:${"%02d".format(secs % 60)})"
                    )
                )
            }
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, recs.map { it.label })
    }

    private fun togglePlay(uri: Uri) {
        if (player != null) {
            player?.release()
            player = null
            return
        }
        try {
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepare()
                start()
                setOnCompletionListener { release(); player = null }
            }
        } catch (e: Exception) {
            player = null
            Toast.makeText(this, "No se pudo reproducir", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val REL = RecorderService.REL_PATH
    }
}
