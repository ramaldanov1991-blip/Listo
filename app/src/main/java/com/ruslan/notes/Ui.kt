package com.ruslan.notes

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val Bg = Color(0xFF1E1E1E)
private val Card = Color(0xFF2B2B2B)
private val Ink = Color(0xFFE8E8E8)
private val Faint = Color(0xFF8C8C8C)
private val Line = Color(0xFF3A3A3A)

private fun noteColor(i: Int) = Color(NOTE_COLORS[i.coerceIn(0, NOTE_COLORS.lastIndex)].toInt())

private fun formatDate(ts: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val ru = Locale.forLanguageTag("ru")
    val sameDay = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (sameDay) "HH:mm" else "d MMM", ru).format(Date(ts))
}

// =====================================================================

@Composable
fun NotesApp(openNote: String?, onOpen: (String?) -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (openNote != null && NotesRepo.get(openNote) != null) {
            EditorScreen(
                noteId = openNote,
                onBack = { onOpen(null) },
                onNew = { onOpen(NotesRepo.create().id) },
            )
        } else {
            ListScreen(onOpen = { onOpen(it) }, onNew = { onOpen(NotesRepo.create().id) })
        }
    }
}

// ---------- список ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListScreen(onOpen: (String) -> Unit, onNew: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Note?>(null) }
    val list = NotesRepo.notes.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Listo", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(noteColor(0)).clickable(onClick = onNew),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = Color(0xFF1E1E1E), fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
                .background(Card).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) Text("Поиск", color = Faint, fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "Пока пусто.\nНажмите «+», чтобы написать заметку." else "Ничего не найдено",
                    color = Faint, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { n ->
                    NoteCard(
                        n,
                        Modifier.combinedClickable(onClick = { onOpen(n.id) }, onLongClick = { toDelete = n }),
                    )
                }
            }
        }
    }

    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить заметку?") },
            text = { Text(n.text.take(80).ifBlank { "Пустая заметка" }) },
            confirmButton = { TextButton(onClick = { NotesRepo.delete(n.id); toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun NoteCard(n: Note, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Card)) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(noteColor(n.color)))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                if (n.text.isBlank()) AnnotatedString("Пустая заметка") else styledString(n.text, n.styles),
                color = if (n.text.isBlank()) Faint else Ink,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 9,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(formatDate(n.updated), color = Faint, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

// ---------- редактор ----------

private const val BULLET = "• "

/** Текст + стиль каждого символа + курсор. */
private class Doc(val text: String, val masks: IntArray, val selStart: Int, val selEnd: Int)

private fun insert(d: Doc, pos: Int, s: String): Doc {
    val t = d.text.substring(0, pos) + s + d.text.substring(pos)
    val m = IntArray(t.length)
    d.masks.copyInto(m, 0, 0, pos)
    d.masks.copyInto(m, pos + s.length, pos, d.masks.size)
    fun adj(o: Int) = if (o >= pos) o + s.length else o
    return Doc(t, m, adj(d.selStart), adj(d.selEnd))
}

private fun remove(d: Doc, pos: Int, len: Int): Doc {
    val t = d.text.substring(0, pos) + d.text.substring(pos + len)
    val m = IntArray(t.length)
    d.masks.copyInto(m, 0, 0, pos)
    d.masks.copyInto(m, pos, pos + len, d.masks.size)
    fun adj(o: Int) = when {
        o >= pos + len -> o - len
        o > pos -> pos
        else -> o
    }
    return Doc(t, m, adj(d.selStart), adj(d.selEnd))
}

private fun lineStarts(text: String, a: Int, b: Int): List<Int> {
    val out = ArrayList<Int>()
    var ls = if (a == 0) 0 else text.lastIndexOf('\n', a - 1) + 1
    out += ls
    while (true) {
        val nl = text.indexOf('\n', ls)
        if (nl == -1 || nl >= b) break
        ls = nl + 1
        out += ls
    }
    return out
}

@Composable
private fun EditorScreen(noteId: String, onBack: () -> Unit, onNew: () -> Unit) {
    val ctx = LocalContext.current
    val initial = remember(noteId) { NotesRepo.get(noteId) } ?: return
    var value by remember(noteId) { mutableStateOf(TextFieldValue(initial.text, TextRange(initial.text.length))) }
    var masks by remember(noteId) { mutableStateOf(runsToMasks(initial.text.length, initial.styles)) }
    var typing by remember(noteId) { mutableIntStateOf(0) }
    var color by remember(noteId) { mutableIntStateOf(initial.color) }
    var menu by remember(noteId) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun persist() {
        val n = NotesRepo.get(noteId) ?: return
        val runs = masksToRuns(masks)
        if (n.text != value.text || n.styles != runs || n.color != color) {
            NotesRepo.update(n.copy(text = value.text, styles = runs, color = color))
        }
    }

    LaunchedEffect(value.text, masks, color) {
        delay(400)
        persist()
    }
    DisposableEffect(noteId) {
        onDispose {
            persist()
            if (NotesRepo.get(noteId)?.text?.isBlank() == true) NotesRepo.delete(noteId)
        }
    }
    LaunchedEffect(noteId) {
        if (initial.text.isEmpty()) runCatching { focus.requestFocus() }
    }
    BackHandler {
        if (menu) menu = false else onBack()
    }

    fun apply(d: Doc, keepComposition: TextFieldValue? = null) {
        masks = d.masks
        value = keepComposition ?: TextFieldValue(d.text, TextRange(d.selStart, d.selEnd))
    }

    fun onChange(new: TextFieldValue) {
        val old = value
        if (new.text == old.text) {
            if (new.selection != old.selection) {
                val c = new.selection.min
                typing = when {
                    c > 0 && c <= masks.size -> masks[c - 1]
                    masks.isNotEmpty() -> masks[0]
                    else -> 0
                }
            }
            value = new
            return
        }
        val o = old.text
        val n = new.text
        var p = 0
        val maxP = min(o.length, n.length)
        while (p < maxP && o[p] == n[p]) p++
        var s = 0
        while (s < o.length - p && s < n.length - p && o[o.length - 1 - s] == n[n.length - 1 - s]) s++
        val insLen = n.length - p - s
        val m = IntArray(n.length)
        masks.copyInto(m, 0, 0, min(p, masks.size))
        for (i in p until p + insLen) m[i] = typing
        if (s > 0) masks.copyInto(m, p + insLen, o.length - s, o.length)

        var d = Doc(n, m, new.selection.start, new.selection.end)
        var changed = false
        // Список: Enter продолжает маркер, Enter на пустом пункте завершает список.
        if (insLen == 1 && n[p] == '\n') {
            val ls = if (p == 0) 0 else n.lastIndexOf('\n', p - 1) + 1
            val line = n.substring(ls, p)
            if (line.startsWith(BULLET)) {
                d = if (line == BULLET) remove(d, ls, BULLET.length + 1) else insert(d, p + 1, BULLET)
                changed = true
            }
        }
        if (changed) apply(d) else apply(d, new)
    }

    fun active(bit: Int): Boolean {
        val sel = value.selection
        if (sel.collapsed) return typing and bit != 0
        val a = sel.min
        val b = min(sel.max, masks.size)
        return b > a && (a until b).all { masks[it] and bit != 0 }
    }

    fun toggle(bit: Int) {
        val sel = value.selection
        if (sel.collapsed) {
            typing = typing xor bit
            return
        }
        val on = !active(bit)
        val m = masks.copyOf()
        for (i in sel.min until min(sel.max, m.size)) m[i] = if (on) m[i] or bit else m[i] and bit.inv()
        masks = m
    }

    fun toggleList() {
        val sel = value.selection
        val t = value.text
        val starts = lineStarts(t, sel.min, max(sel.max, sel.min))
        val all = starts.all { t.startsWith(BULLET, it) }
        var d = Doc(t, masks, sel.start, sel.end)
        for (ls in starts.reversed()) {
            d = if (all) remove(d, ls, BULLET.length) else if (!t.startsWith(BULLET, ls)) insert(d, ls, BULLET) else d
        }
        apply(d)
    }

    val accent = noteColor(color)
    val transformation = remember(masks) {
        VisualTransformation { t ->
            val runs = masksToRuns(if (masks.size == t.text.length) masks else masks.copyOf(t.text.length))
            TransformedText(styledString(t.text, runs), OffsetMapping.Identity)
        }
    }

    Column(Modifier.fillMaxSize().background(Card).safeDrawingPadding().imePadding()) {
        // Шапка цвета заметки
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(accent).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderButton("‹", 30) { onBack() }
            Spacer(Modifier.weight(1f))
            HeaderButton("+", 26) { persist(); onNew() }
            HeaderButton("⋯", 24) { menu = !menu }
        }

        if (menu) {
            Column(Modifier.fillMaxWidth().background(Color(0xFF353535)).padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in NOTE_COLORS.indices) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(noteColor(i))
                                .border(if (i == color) 3.dp else 0.dp, Ink, CircleShape)
                                .clickable { color = i },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.height(14.dp))
                Text("📌  Прикрепить на главный экран", color = Ink, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    for (size in WidgetSize.entries) {
                        SizeChip(size, accent, Modifier.weight(1f)) {
                            persist()
                            menu = false
                            if (!NoteWidget.requestPin(ctx, noteId, size)) {
                                Toast.makeText(
                                    ctx,
                                    "Лаунчер не умеет прикреплять сам. Зажмите пустое место на главном экране → Виджеты → «Listo».",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                MenuRow("🗑  Удалить заметку") {
                    menu = false
                    NotesRepo.delete(noteId)
                    onBack()
                }
            }
        }

        // Текст
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            if (value.text.isEmpty()) Text("Напишите заметку...", color = Faint, fontSize = 17.sp)
            BasicTextField(
                value = value,
                onValueChange = { onChange(it) },
                textStyle = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 25.sp),
                cursorBrush = SolidColor(accent),
                visualTransformation = transformation,
                modifier = Modifier.fillMaxSize().focusRequester(focus),
            )
        }

        // Панель форматирования
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(active(BOLD), { toggle(BOLD) }) { Text("B", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            ToolButton(active(ITALIC), { toggle(ITALIC) }) { Text("I", color = Ink, fontSize = 18.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium) }
            ToolButton(active(UNDERLINE), { toggle(UNDERLINE) }) { Text("U", color = Ink, fontSize = 18.sp, textDecoration = TextDecoration.Underline) }
            ToolButton(active(STRIKE), { toggle(STRIKE) }) { Text("ab", color = Ink, fontSize = 18.sp, textDecoration = TextDecoration.LineThrough) }
            ToolButton(false, { toggleList() }) { Text("≡", color = Ink, fontSize = 24.sp) }
            Spacer(Modifier.weight(1f))
            Text(formatDate(NotesRepo.get(noteId)?.updated ?: initial.updated), color = Faint, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun HeaderButton(text: String, size: Int, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF1E1E1E), fontSize = size.sp, fontWeight = FontWeight.Bold)
    }
}

/** Кнопка размера: мини-схема листочка в пропорциях сетки главного экрана. */
@Composable
private fun SizeChip(size: WidgetSize, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2B2B2B)).clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            val cell = 10.dp
            Column(
                Modifier.width(cell * size.cellsW).height(cell * size.cellsH)
                    .clip(RoundedCornerShape(3.dp)).background(Color(0xFF4A4A4A)),
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
            }
        }
        Text(size.title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text("${size.cellsW}×${size.cellsH}", color = Faint, fontSize = 11.sp)
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(text, color = Ink, fontSize = 16.sp)
    }
}

@Composable
private fun ToolButton(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF454545) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------- выбор заметки для виджета ----------

@Composable
fun PickNoteScreen(onPick: (String) -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding().padding(16.dp)) {
        Text("Какую заметку показать на главном экране?", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(10.dp)).background(noteColor(0))
                .clickable { onPick(NotesRepo.create().id) },
            contentAlignment = Alignment.Center,
        ) { Text("+ Новая заметка", color = Color(0xFF1E1E1E), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(NotesRepo.notes.toList(), key = { it.id }) { n ->
                NoteCard(n, Modifier.fillMaxWidth().clickable { onPick(n.id) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(Card).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Text("Отмена", color = Ink, fontSize = 16.sp) }
    }
}
