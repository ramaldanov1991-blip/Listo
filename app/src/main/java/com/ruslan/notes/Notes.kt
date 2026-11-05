package com.ruslan.notes

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

// ---------- стили текста ----------

const val BOLD = 1
const val ITALIC = 2
const val UNDERLINE = 4
const val STRIKE = 8

data class StyleRun(val start: Int, val end: Int, val mask: Int)

/** Цвета заметок (шапка). */
val NOTE_COLORS = longArrayOf(
    0xFFF5D34A, // жёлтый
    0xFF86CF73, // зелёный
    0xFFE58AC4, // розовый
    0xFFB993F0, // фиолетовый
    0xFF62BCE6, // голубой
    0xFFA3A3A3, // серый
    0xFF5E5E5E, // графит
)

data class Note(
    val id: String,
    val text: String,
    val styles: List<StyleRun>,
    val color: Int,
    val updated: Long,
)

// ---------- маски стилей: по одному int на символ ----------

fun runsToMasks(length: Int, runs: List<StyleRun>): IntArray {
    val m = IntArray(length)
    for (r in runs) for (i in r.start.coerceAtLeast(0) until r.end.coerceAtMost(length)) m[i] = m[i] or r.mask
    return m
}

fun masksToRuns(masks: IntArray): List<StyleRun> {
    val out = ArrayList<StyleRun>()
    var i = 0
    while (i < masks.size) {
        val m = masks[i]
        var j = i + 1
        while (j < masks.size && masks[j] == m) j++
        if (m != 0) out += StyleRun(i, j, m)
        i = j
    }
    return out
}

fun spanStyleOf(mask: Int): SpanStyle {
    val deco = buildList {
        if (mask and UNDERLINE != 0) add(TextDecoration.Underline)
        if (mask and STRIKE != 0) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        fontWeight = if (mask and BOLD != 0) FontWeight.Bold else null,
        fontStyle = if (mask and ITALIC != 0) FontStyle.Italic else null,
        textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
    )
}

fun styledString(text: String, runs: List<StyleRun>): AnnotatedString {
    val b = AnnotatedString.Builder(text)
    for (r in runs) {
        val s = r.start.coerceIn(0, text.length)
        val e = r.end.coerceIn(0, text.length)
        if (e > s) b.addStyle(spanStyleOf(r.mask), s, e)
    }
    return b.toAnnotatedString()
}

/** Тот же текст для виджета (RemoteViews понимают стандартные span'ы Android). */
fun spannedString(text: String, runs: List<StyleRun>): CharSequence {
    val sb = SpannableStringBuilder(text)
    for (r in runs) {
        val s = r.start.coerceIn(0, text.length)
        val e = r.end.coerceIn(0, text.length)
        if (e <= s) continue
        val f = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        val bold = r.mask and BOLD != 0
        val italic = r.mask and ITALIC != 0
        if (bold || italic) {
            val st = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                else -> Typeface.ITALIC
            }
            sb.setSpan(StyleSpan(st), s, e, f)
        }
        if (r.mask and UNDERLINE != 0) sb.setSpan(UnderlineSpan(), s, e, f)
        if (r.mask and STRIKE != 0) sb.setSpan(StrikethroughSpan(), s, e, f)
    }
    return sb
}

// ---------- хранилище ----------

/** Заметки в JSON-файле во внутренней памяти приложения. */
object NotesRepo {
    val notes = mutableStateListOf<Note>()
    private var file: File? = null
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        val f = File(appContext.filesDir, "notes.json")
        file = f
        notes.clear()
        runCatching {
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) notes += fromJson(arr.getJSONObject(i))
            }
        }
        if (notes.isEmpty() && !f.exists()) {
            notes += Note(
                UUID.randomUUID().toString(),
                "Добро пожаловать!\n• Пишите что угодно\n• Выделите текст и нажмите B, I, U или ab\n• Кнопка ≡ делает список\n• Через «⋯» можно сменить цвет и прикрепить заметку на главный экран",
                listOf(StyleRun(0, 17, BOLD)),
                2,
                System.currentTimeMillis(),
            )
            save()
        }
    }

    fun get(id: String): Note? = notes.firstOrNull { it.id == id }

    fun create(color: Int = 0): Note {
        val n = Note(UUID.randomUUID().toString(), "", emptyList(), color, System.currentTimeMillis())
        notes.add(0, n)
        save()
        return n
    }

    fun update(note: Note) {
        val i = notes.indexOfFirst { it.id == note.id }
        val n = note.copy(updated = System.currentTimeMillis())
        if (i >= 0) {
            notes.removeAt(i)
        }
        notes.add(0, n)
        save()
        NoteWidget.updateForNote(appContext, n.id)
    }

    fun delete(id: String) {
        notes.removeAll { it.id == id }
        save()
        NoteWidget.updateForNote(appContext, id)
    }

    private fun save() {
        val f = file ?: return
        val arr = JSONArray()
        for (n in notes) arr.put(toJson(n))
        val data = arr.toString()
        io.execute {
            runCatching {
                val tmp = File(f.parentFile, "notes.json.tmp")
                tmp.writeText(data)
                if (!tmp.renameTo(f)) {
                    f.writeText(data)
                    tmp.delete()
                }
            }
        }
    }

    private fun toJson(n: Note) = JSONObject().apply {
        put("id", n.id)
        put("text", n.text)
        put("color", n.color)
        put("updated", n.updated)
        put("styles", JSONArray().apply {
            for (r in n.styles) put(JSONArray().put(r.start).put(r.end).put(r.mask))
        })
    }

    private fun fromJson(o: JSONObject): Note {
        val st = o.optJSONArray("styles") ?: JSONArray()
        val runs = (0 until st.length()).map {
            val a = st.getJSONArray(it)
            StyleRun(a.getInt(0), a.getInt(1), a.getInt(2))
        }
        return Note(
            o.getString("id"),
            o.optString("text"),
            runs,
            o.optInt("color", 0).coerceIn(0, NOTE_COLORS.lastIndex),
            o.optLong("updated", 0L),
        )
    }
}
