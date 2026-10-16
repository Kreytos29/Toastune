package app.toastune

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import app.toastune.core.Track
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Widget de la pantalla de inicio: portada, título y controles. */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as ToastuneApp
        val s = app.player.state.value
        app.appScope.launch { refresh(app, s.track, s.isPlaying) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as ToastuneApp
        val player = app.player
        when (intent.action) {
            ACTION_TOGGLE -> player.togglePlay()
            ACTION_NEXT -> player.next()
            ACTION_PREVIOUS -> player.previous()
            else -> return
        }
        // Si el proceso estaba dormido, hay que darle unos segundos para conectar con el reproductor.
        val pending = goAsync()
        app.appScope.launch {
            delay(2_500)
            pending.finish()
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "app.toastune.widget.TOGGLE"
        private const val ACTION_NEXT = "app.toastune.widget.NEXT"
        private const val ACTION_PREVIOUS = "app.toastune.widget.PREVIOUS"

        private var lastCoverUrl: String? = null
        private var lastCover: Bitmap? = null

        private fun action(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context, code,
            Intent(context, PlayerWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Redibuja todos los widgets con la canción actual. */
        suspend fun refresh(context: Context, track: Track?, playing: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PlayerWidget::class.java)
            if (manager.getAppWidgetIds(component).isEmpty()) return

            val url = track?.thumbnailUrl
            if (url != lastCoverUrl) {
                lastCoverUrl = url
                lastCover = if (url == null) null else withContext(Dispatchers.IO) {
                    runCatching {
                        val request = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
                        Coil.imageLoader(context).execute(request).drawable?.toBitmap(256, 256)
                    }.getOrNull()
                }
            }

            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.w_title, track?.title ?: "Toastune")
            views.setTextViewText(R.id.w_artist, track?.artist ?: "Toca para abrir")
            val cover = lastCover
            if (cover != null) views.setImageViewBitmap(R.id.w_cover, cover)
            else views.setImageViewResource(R.id.w_cover, R.drawable.ic_logo)
            views.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            views.setOnClickPendingIntent(R.id.w_play, action(context, ACTION_TOGGLE, 1))
            views.setOnClickPendingIntent(R.id.w_next, action(context, ACTION_NEXT, 2))
            views.setOnClickPendingIntent(R.id.w_prev, action(context, ACTION_PREVIOUS, 3))
            views.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(
                    context, 4, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(component, views)
        }
    }
}
