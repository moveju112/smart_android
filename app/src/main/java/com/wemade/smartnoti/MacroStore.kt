package com.wemade.smartnoti

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 매크로 목록의 단일 보관소. 액티비티와 서비스가 같은 프로세스라 object 하나로 공유한다 */
object MacroStore {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros
    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "macros.json")

    /**
     * 최초 1회 파일에서 읽어온다.
     * 미리 깔아 두는 매크로는 없다 — 무엇을 자동화할지는 쓰는 사람이 정한다.
     */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        _macros.value = if (f.exists()) {
            runCatching { json.decodeFromString<List<Macro>>(f.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    /** 목록 전체를 파일에 쓰고 메모리 상태도 갱신한다 */
    @Synchronized
    fun save(context: Context, list: List<Macro>) {
        _macros.value = list
        file(context).writeText(json.encodeToString<List<Macro>>(list))
    }

    /** 매크로 하나를 추가하거나 같은 id를 덮어쓴다 */
    fun upsert(context: Context, macro: Macro) {
        val list = _macros.value.toMutableList()
        val at = list.indexOfFirst { it.id == macro.id }
        if (at >= 0) list[at] = macro else list += macro
        save(context, list)
    }

    /** 여러 매크로를 한 폴더로 한꺼번에 옮긴다. 파일은 한 번만 쓴다 */
    fun moveToFolder(context: Context, ids: Set<Long>, folder: String) {
        save(context, _macros.value.map { if (it.id in ids) it.copy(folder = folder) else it })
    }

    fun delete(context: Context, id: Long) {
        save(context, _macros.value.filterNot { it.id == id })
    }

    fun find(id: Long): Macro? = _macros.value.firstOrNull { it.id == id }
}

/**
 * 펼쳐 둔 폴더 이름.
 *
 * 폴더는 목록을 줄이려고 만든 것이니 처음에는 다 접혀 있다. 사람이 펼친 것만 기억한다.
 * 펼치고 접는 것은 화면 상태가 아니라 사람의 결정이다. 앱을 껐다 켜도 그대로 있어야 한다.
 */
object FolderState {
    private const val PREFS = "folders"
    private const val KEY_EXPANDED = "expanded"

    fun expanded(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_EXPANDED, emptySet()) ?: emptySet()

    fun setExpanded(context: Context, folder: String, expanded: Boolean) {
        val now = expanded(context).toMutableSet()
        if (expanded) now += folder else now -= folder
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_EXPANDED, now).apply()
    }
}

/**
 * 매크로가 마지막으로 무엇을 했는지.
 *
 * 목록이 이 앱의 유일한 약속을 지키려면 — 네가 안 보는 동안 일어났다 — 무장과 미실행을
 * 구별해 보여야 한다. 실행 기록은 사람이 읽는 글이라 화면이 되읽을 수 없으므로 결과만 따로 적는다.
 */
object MacroHistory {
    private const val PREFS = "history"

    enum class Outcome { Ran, Stopped, Failed }

    data class Entry(val at: Long, val outcome: Outcome)

    private val _entries = MutableStateFlow<Map<Long, Entry>>(emptyMap())
    val entries: StateFlow<Map<Long, Entry>> = _entries

    fun load(context: Context) {
        _entries.value = prefs(context).all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val entry = parseEntry(value as? String ?: return@mapNotNull null) ?: return@mapNotNull null
            id to entry
        }.toMap()
    }

    fun record(context: Context, macroId: Long, outcome: Outcome) {
        val entry = Entry(System.currentTimeMillis(), outcome)
        prefs(context).edit().putString(macroId.toString(), "${entry.at}:${outcome.name}").apply()
        _entries.value = _entries.value + (macroId to entry)
    }

    /** 매크로를 지울 때 이력도 함께 지운다 */
    fun forget(context: Context, macroId: Long) {
        prefs(context).edit().remove(macroId.toString()).apply()
        _entries.value = _entries.value - macroId
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** 적어 둔 이력 한 줄을 읽는다. 모르는 결과 이름이 섞여 있어도 앱이 죽지 않게 한다 */
internal fun parseEntry(raw: String): MacroHistory.Entry? {
    val at = raw.substringBefore(':').toLongOrNull() ?: return null
    val name = raw.substringAfter(':', "")
    val outcome = MacroHistory.Outcome.entries.firstOrNull { it.name == name } ?: return null
    return MacroHistory.Entry(at, outcome)
}

/** 엔진이 지금 살아 있는지, 어떤 매크로가 돌고 있는지 — 화면이 상태를 그대로 비추게 한다 */
object EngineState {
    val connected = MutableStateFlow(false)
    val running = MutableStateFlow<Set<Long>>(emptySet())

    fun markRunning(id: Long, isRunning: Boolean) {
        running.value = if (isRunning) running.value + id else running.value - id
    }
}

/**
 * 매크로가 왜 안 걸리는지 알아내려면 알림의 실제 문구를 봐야 한다.
 * 켜 두면 들어오는 알림을 전부 실행 기록에 남긴다. 시끄러우니 평소엔 꺼 둔다.
 */
object Diagnostics {
    private const val PREFS = "diagnostics"
    private const val KEY_PEEK = "peek"

    val peekNotifications = MutableStateFlow(false)

    /**
     * 켜 둔 것을 기억한다.
     *
     * 이 진단이 필요한 상황이 바로 앱이 정리되는 상황이다. 프로세스와 함께 꺼지면
     * 정작 보려던 것을 못 본다.
     */
    fun load(context: Context) {
        peekNotifications.value = prefs(context).getBoolean(KEY_PEEK, false)
    }

    fun setPeek(context: Context, on: Boolean) {
        peekNotifications.value = on
        prefs(context).edit().putBoolean(KEY_PEEK, on).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * 최근 실행 기록. 실기기에서 매크로가 정말 도는지 확인할 창구다.
 *
 * 파일에도 적는다. 안드로이드가 앱을 정리하면 메모리에 있던 기록이 통째로 사라져서,
 * 정작 무엇이 잘못됐는지 알아야 할 때 아무것도 남지 않는 일이 있었다.
 * 날짜까지 적는 것도 그래서다 — 어제 일인지 오늘 일인지 알아야 짚을 수 있다.
 */
object RunLog {
    private const val MAX_LINES = 400
    private const val MAX_BYTES = 96 * 1024
    private const val FILE = "runlog.txt"

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    @Volatile
    private var home: Context? = null

    private val stampFormat = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    /**
     * 파일에 적기 시작한다. 화면과 엔진 어느 쪽이 먼저 떠도 좋다.
     * 처음 붙을 때 지난 기록을 읽어 오므로, 앱이 죽었다 살아나도 어제 일이 보인다.
     */
    @Synchronized
    fun attach(context: Context) {
        if (home != null) return
        home = context.applicationContext
        val f = file() ?: return
        if (!f.exists()) return
        val past = runCatching { f.readLines() }.getOrDefault(emptyList())
        _lines.value = past.takeLast(MAX_LINES).asReversed()
    }

    fun add(message: String) {
        val line = java.time.LocalDateTime.now().format(stampFormat) + "  " + message
        _lines.value = (listOf(line) + _lines.value).take(MAX_LINES)
        val f = file() ?: return
        runCatching {
            f.appendText(line + "\n")
            // 커지면 뒤쪽 절반만 남긴다. 매번 세지 않고 크기로만 판단한다
            if (f.length() > MAX_BYTES) {
                f.writeText(f.readLines().takeLast(MAX_LINES / 2).joinToString("\n", postfix = "\n"))
            }
        }
    }

    private fun file(): File? = home?.let { File(it.filesDir, FILE) }
}

/**
 * 알람에 맡겨 둔 대기.
 *
 * 30분을 기다리는 동안 안드로이드가 앱을 정리하면 메모리에 있던 대기는 그대로 사라진다.
 * 남은 단계가 몇 번째인지와 깨울 시각을 적어 두고, 알람이 오면 거기서 이어간다.
 */
object PendingWaits {
    private const val PREFS = "waits"

    /** 어느 매크로가 언제 이어질 예정인지. 목록이 「22:06 이어서 함」을 그리려면 이 값이 필요하다 */
    private val _waiting = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val waiting: StateFlow<Map<Long, Long>> = _waiting

    fun load(context: Context) {
        _waiting.value = prefs(context).all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val (_, due) = parse(value as? String ?: return@mapNotNull null) ?: return@mapNotNull null
            id to due
        }.toMap()
    }

    fun put(context: Context, macroId: Long, nextIndex: Int, dueAt: Long) {
        prefs(context).edit().putString(macroId.toString(), "$nextIndex:$dueAt").apply()
        _waiting.value = _waiting.value + (macroId to dueAt)
    }

    /** 꺼내면서 지운다. 같은 대기를 두 번 이어가지 않으려는 것이다 */
    fun take(context: Context, macroId: Long): Int? {
        val raw = prefs(context).getString(macroId.toString(), null) ?: return null
        prefs(context).edit().remove(macroId.toString()).apply()
        _waiting.value = _waiting.value - macroId
        return parse(raw)?.first
    }

    /** 깨울 시각이 이미 지난 것들. 알람을 놓친 사이 앱이 죽었다면 엔진이 뜨면서 챙긴다 */
    fun overdue(context: Context, now: Long): Map<Long, Int> =
        prefs(context).all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val (at, due) = parse(value as? String ?: return@mapNotNull null) ?: return@mapNotNull null
            if (due <= now) id to at else null
        }.toMap()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * 적어 둔 대기 한 줄을 (남은 단계, 깨울 시각)으로 읽는다.
 * 형식이 깨졌으면 null — 옛 형식이 남아 있어도 앱이 죽지 않게 한다.
 */
internal fun parse(raw: String): Pair<Int, Long>? {
    val at = raw.substringBefore(':').toIntOrNull() ?: return null
    val due = raw.substringAfter(':', "").toLongOrNull() ?: return null
    return at to due
}
