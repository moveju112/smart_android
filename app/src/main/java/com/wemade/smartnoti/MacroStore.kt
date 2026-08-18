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

    fun delete(context: Context, id: Long) {
        save(context, _macros.value.filterNot { it.id == id })
    }

    fun find(id: Long): Macro? = _macros.value.firstOrNull { it.id == id }
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
    val peekNotifications = MutableStateFlow(false)
}

/**
 * 최근 실행 기록. 실기기에서 매크로가 정말 도는지 확인할 창구다.
 * ponytail: 메모리에만 남는다. 앱을 껐다 켜면 사라지고, 필요해지면 파일로 내리면 된다
 */
object RunLog {
    private const val MAX_LINES = 100
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun add(message: String) {
        val stamp = java.time.LocalTime.now().withNano(0).toString()
        _lines.value = (listOf("$stamp  $message") + _lines.value).take(MAX_LINES)
    }
}
