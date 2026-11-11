package app.grabadorllamadas

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget con dos botones: activar/desactivar la escucha automática y grabar/parar a mano. */
class RecorderWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, build(ctx))
    }

    companion object {
        private fun click(ctx: Context, action: String, req: Int): PendingIntent {
            val i = Intent(ctx, ActionActivity::class.java).setAction(action)
            return PendingIntent.getActivity(
                ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val armed = State.armed(ctx)
            val rec = State.recording(ctx)
            v.setTextViewText(R.id.w_arm, if (armed) "Escucha: ON" else "Escucha: OFF")
            v.setTextViewText(R.id.w_rec, if (rec) "■ Parar" else "● Grabar")
            v.setInt(R.id.w_arm, "setBackgroundResource", if (armed) R.drawable.w_on else R.drawable.w_off)
            v.setInt(R.id.w_rec, "setBackgroundResource", if (rec) R.drawable.w_red else R.drawable.w_off)
            v.setOnClickPendingIntent(R.id.w_arm, click(ctx, RecorderService.ACTION_TOGGLE_ARM, 1))
            v.setOnClickPendingIntent(R.id.w_rec, click(ctx, RecorderService.ACTION_TOGGLE_REC, 2))
            return v
        }

        fun update(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, RecorderWidget::class.java))
            if (ids.isNotEmpty()) m.updateAppWidget(ids, build(ctx))
        }
    }
}
