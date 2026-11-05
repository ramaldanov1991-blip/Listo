package com.ruslan.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Размеры виджета: у каждого свой провайдер, поэтому в списке виджетов они идут отдельными пунктами. */
enum class WidgetSize(
    val title: String,
    val cls: Class<out NoteWidget>,
    val textSp: Float,
    val headerDp: Int,
    val cellsW: Int,
    val cellsH: Int,
) {
    SMALL("Маленькая", NoteWidgetSmall::class.java, 12f, 6, 2, 1),
    MEDIUM("Средняя", NoteWidget::class.java, 13f, 9, 2, 2),
    WIDE("Широкая", NoteWidgetWide::class.java, 14f, 9, 4, 2),
    LARGE("Большая", NoteWidgetLarge::class.java, 15f, 11, 4, 4),
}

class NoteWidgetSmall : NoteWidget()
class NoteWidgetWide : NoteWidget()
class NoteWidgetLarge : NoteWidget()

/** Виджет-листочек на главном экране: показывает одну заметку. */
open class NoteWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        NotesRepo.init(context)
        for (id in ids) render(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val p = prefs(context).edit()
        for (id in ids) p.remove("w_$id")
        p.apply()
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"

        private fun prefs(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        fun noteFor(c: Context, widgetId: Int): String? = prefs(c).getString("w_$widgetId", null)

        fun bind(c: Context, widgetId: Int, noteId: String) {
            prefs(c).edit().putString("w_$widgetId", noteId).apply()
            NotesRepo.init(c)
            render(c, AppWidgetManager.getInstance(c), widgetId)
        }

        fun updateForNote(c: Context, noteId: String) {
            val m = AppWidgetManager.getInstance(c)
            for (size in WidgetSize.entries) {
                for (id in m.getAppWidgetIds(ComponentName(c, size.cls))) {
                    if (noteFor(c, id) == noteId) render(c, m, id)
                }
            }
        }

        private fun sizeOf(m: AppWidgetManager, widgetId: Int): WidgetSize {
            val cls = m.getAppWidgetInfo(widgetId)?.provider?.className
            return WidgetSize.entries.firstOrNull { it.cls.name == cls } ?: WidgetSize.MEDIUM
        }

        fun render(c: Context, m: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(c.packageName, R.layout.widget_note)
            val size = sizeOf(m, widgetId)
            views.setTextViewTextSize(R.id.text, TypedValue.COMPLEX_UNIT_SP, size.textSp)
            val headerPx = (size.headerDp * c.resources.displayMetrics.density).toInt()
            if (Build.VERSION.SDK_INT >= 31) {
                views.setViewLayoutHeight(R.id.header, headerPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            }
            if (size == WidgetSize.SMALL) {
                views.setViewPadding(R.id.text, headerPx * 2, headerPx, headerPx * 2, headerPx)
            }
            val noteId = noteFor(c, widgetId)
            val note = noteId?.let { NotesRepo.get(it) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            if (note == null) {
                views.setTextViewText(
                    R.id.text,
                    if (noteId == null) "Нажмите, чтобы выбрать заметку" else "Заметка удалена.\nНажмите, чтобы выбрать другую",
                )
                views.setInt(R.id.header, "setColorFilter", NOTE_COLORS[5].toInt())
                val pick = Intent(c, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, widgetId, pick, flags))
            } else {
                views.setTextViewText(
                    R.id.text,
                    if (note.text.isBlank()) "Пустая заметка" else spannedString(note.text, note.styles),
                )
                views.setInt(R.id.header, "setColorFilter", NOTE_COLORS[note.color].toInt())
                val open = Intent(c, MainActivity::class.java)
                    .putExtra(EXTRA_NOTE_ID, note.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, widgetId, open, flags))
            }
            m.updateAppWidget(widgetId, views)
        }

        /** Попросить лаунчер прикрепить виджет с этой заметкой. */
        fun requestPin(c: Context, noteId: String, size: WidgetSize): Boolean {
            if (Build.VERSION.SDK_INT < 26) return false
            val m = c.getSystemService(AppWidgetManager::class.java) ?: return false
            if (!m.isRequestPinAppWidgetSupported) return false
            val cb = Intent(c, PinReceiver::class.java).putExtra(EXTRA_NOTE_ID, noteId)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(c, noteId.hashCode() * 31 + size.ordinal, cb, flags)
            return m.requestPinAppWidget(ComponentName(c, size.cls), null, pi)
        }
    }
}

/** Получает id нового виджета после закрепления из приложения. */
class PinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val noteId = intent.getStringExtra(NoteWidget.EXTRA_NOTE_ID) ?: return
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) NoteWidget.bind(context, widgetId, noteId)
    }
}

/** Выбор заметки для виджета (при добавлении из меню виджетов или по нажатию на пустой виджет). */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        NotesRepo.init(this)
        setContent {
            PickNoteScreen(
                onPick = { noteId ->
                    NoteWidget.bind(this, widgetId, noteId)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    // Новую (пустую) заметку сразу открываем для ввода текста.
                    if (NotesRepo.get(noteId)?.text.isNullOrBlank()) {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(NoteWidget.EXTRA_NOTE_ID, noteId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                    }
                    finish()
                },
                onCancel = { finish() },
            )
        }
    }
}
