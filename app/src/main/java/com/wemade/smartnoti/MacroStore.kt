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

    /** 최초 1회 파일에서 읽어온다. 파일이 없으면 기본 매크로를 깔아준다 */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        _macros.value = if (f.exists()) {
            runCatching { json.decodeFromString<List<Macro>>(f.readText()) }.getOrDefault(emptyList())
        } else {
            defaultMacros().also { save(context, it) }
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

/**
 * 처음 켰을 때 깔리는 매크로 뼈대.
 *
 * 차량 주소와 AdGuard 비밀번호는 사람마다 다르고 남에게 보일 값이라 비워 둔다.
 * 앱에서 기기를 고르고 비밀번호를 넣으면 그때부터 동작한다.
 */
private fun defaultMacros(): List<Macro> {
    val carMac = ""
    val carName = ""
    // AdGuard 자동화 브로드캐스트는 그 앱 설정에 적힌 비밀번호를 extra로 같이 보낸다
    fun adguard(action: String) = Action.Broadcast(
        packageName = "com.adguard.android",
        className = "com.adguard.android.receiver.AutomationReceiver",
        action = action,
        extraName = "password",
        extraValue = ""
    )
    var seq = 1L
    fun nextId() = seq++

    return listOf(
        Macro(
            id = nextId(), name = "토스 알림 삭제",
            trigger = Trigger.Notification("viva.republica.toss", "토스"),
            actions = listOf(
                Action.Delay(5),
                Action.ClearNotification("viva.republica.toss", "토스", "MSTU")
            )
        ),
        Macro(
            id = nextId(), name = "테슬라 연결 알림 삭제",
            trigger = Trigger.Notification("com.teslamotors.tesla", "Tesla", "연결됨"),
            actions = listOf(
                Action.Delay(30),
                Action.ClearNotification("com.teslamotors.tesla", "Tesla", "연결됨")
            )
        ),
        Macro(
            id = nextId(), name = "테슬라 연결 해제 알림 삭제",
            trigger = Trigger.Notification("com.teslamotors.tesla", "Tesla", "연결 해제됨"),
            actions = listOf(
                Action.Delay(15),
                Action.ClearNotification("com.teslamotors.tesla", "Tesla", "연결 해제됨")
            )
        ),
        Macro(
            id = nextId(), name = "헤이홈 알림 삭제",
            trigger = Trigger.Notification("com.goqual", "헤이홈"),
            actions = listOf(
                Action.Delay(60),
                Action.ClearNotification("com.goqual", "헤이홈", "재부팅")
            )
        ),
        Macro(
            id = nextId(), name = "네이버 지도 도착 알림 삭제",
            trigger = Trigger.Notification("com.nhn.android.nmap", "네이버 지도"),
            actions = listOf(
                Action.Delay(60),
                Action.ClearNotification("com.nhn.android.nmap", "네이버 지도", "길안내를 종료합니다")
            )
        ),
        Macro(
            id = nextId(), name = "rclone 알림 삭제",
            trigger = Trigger.Notification("com.google.android.gms", "Google Play 서비스", "rclone"),
            actions = listOf(
                Action.ClearNotification("com.google.android.gms", "Google Play 서비스", "rclone")
            )
        ),
        Macro(
            id = nextId(), name = "Windows 연결 알림 삭제", enabled = false,
            trigger = Trigger.Notification("com.microsoft.appmanager", "Windows와 연결"),
            actions = listOf(
                Action.ClearNotification("com.microsoft.appmanager", "Windows와 연결")
            )
        ),
        Macro(
            id = nextId(), name = "AdGuard 시작 (와이파이 연결)",
            trigger = Trigger.Wifi(connected = true),
            actions = listOf(adguard("start"))
        ),
        Macro(
            id = nextId(), name = "AdGuard 종료 (차 탈 때)",
            trigger = Trigger.Bluetooth(carMac, carName, connected = true),
            actions = listOf(adguard("stop"))
        ),
        Macro(
            id = nextId(), name = "AdGuard 시작 (차 내리고 30분 뒤)",
            trigger = Trigger.Bluetooth(carMac, carName, connected = false),
            actions = listOf(
                Action.Delay(1800),
                // 30분 사이에 차를 다시 탔으면 켜지 않는다
                Action.StopIfBluetooth(carMac, carName, connected = true),
                adguard("start"),
                Action.Delay(300),
                adguard("start")
            )
        )
    )
}
