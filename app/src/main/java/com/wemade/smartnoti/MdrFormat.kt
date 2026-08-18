package com.wemade.smartnoti

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * MacroDroid 백업 파일(.mdr) 읽기.
 * 예전에 쓰던 백업을 받아 주기 위한 것이다. 내보내기는 이 앱의 JSON([exportBackup])으로 한다.
 * 두 앱이 다루는 범위가 달라서 겹치는 부분만 옮기고, 못 옮긴 것은 이름을 그대로 돌려준다.
 */

/** 가져오기 결과 — 옮긴 매크로와, 옮기지 못한 항목의 이름 */
data class ImportResult(
    val macros: List<Macro>,
    val skippedMacros: List<String>,
    val partialMacros: List<String>
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

// JSON에서 값을 꺼내는 짧은 도우미들 — 없으면 기본값
private fun JsonObject.str(key: String, fallback: String = ""): String =
    runCatching { this[key]!!.jsonPrimitive.content }.getOrDefault(fallback)

private fun JsonObject.num(key: String, fallback: Int = 0): Int =
    runCatching { this[key]!!.jsonPrimitive.int }.getOrDefault(fallback)

private fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean =
    runCatching { this[key]!!.jsonPrimitive.boolean }.getOrDefault(fallback)

private fun JsonObject.firstOfList(key: String): String =
    runCatching { this[key]!!.jsonArray.firstOrNull()?.jsonPrimitive?.content ?: "" }.getOrDefault("")

/** .mdr 파일 본문을 읽어 이 앱의 매크로로 바꾼다 */
fun importMdr(text: String): ImportResult {
    val root = lenientJson.parseToJsonElement(text).jsonObject
    val list = root["macroList"]?.jsonArray ?: return ImportResult(emptyList(), emptyList(), emptyList())

    val macros = mutableListOf<Macro>()
    val skipped = mutableListOf<String>()
    val partial = mutableListOf<String>()
    var seq = System.currentTimeMillis()

    for (element in list) {
        val obj = element.jsonObject
        val name = obj.str("m_name", "이름 없음")

        // 1. 트리거 — 첫 번째 것만 쓴다. 이 앱은 매크로당 트리거 하나다
        val triggers = obj["m_triggerList"]?.jsonArray ?: JsonArray(emptyList())
        val trigger = triggers.firstNotNullOfOrNull { toTrigger(it.jsonObject) }
        if (trigger == null) {
            skipped += name
            continue
        }
        if (triggers.size > 1) partial += "$name (트리거 ${triggers.size}개 중 1개만)"

        // 2. 액션 — 옮길 수 있는 것만 순서대로
        val rawActions = obj["m_actionList"]?.jsonArray ?: JsonArray(emptyList())
        val actions = mutableListOf<Action>()
        var dropped = 0
        for (raw in rawActions) {
            val action = toAction(raw.jsonObject)
            if (action != null) actions += action else dropped++
        }
        if (dropped > 0) partial += "$name (액션 ${dropped}개 못 옮김)"

        macros += Macro(
            id = seq++,
            name = name,
            enabled = obj.bool("m_enabled", true),
            trigger = trigger,
            actions = actions
        )
    }
    return ImportResult(macros, skipped, partial)
}

/** MacroDroid 트리거 → 이 앱의 트리거. 모르는 종류면 null */
private fun toTrigger(obj: JsonObject): Trigger? = when (obj.str("m_classType")) {
    "NotificationTrigger" -> Trigger.Notification(
        packageName = obj.firstOfList("m_packageNameList"),
        appLabel = obj.firstOfList("m_applicationNameList"),
        text = obj.str("m_textContent")
    )
    // m_btState 2=연결, 3=해제 (사용자 실제 매크로로 확인한 값)
    "BluetoothTrigger" -> Trigger.Bluetooth(
        address = obj.str("m_deviceAddress"),
        deviceName = obj.str("m_deviceName"),
        connected = obj.num("m_btState") == 2
    )
    "WifiConnectionTrigger" -> Trigger.Wifi(connected = obj.num("m_wifiState") == 0)
    else -> null
}

/** MacroDroid 액션 → 이 앱의 액션. 조건 분기(If/Else)는 옮기지 않는다 */
private fun toAction(obj: JsonObject): Action? = when (obj.str("m_classType")) {
    "PauseAction" -> Action.Delay(
        obj.num("m_delayInSeconds") + obj.num("m_delayInMilliSeconds") / 1000
    )
    "ClearNotificationsAction" -> Action.ClearNotification(
        packageName = obj.firstOfList("m_packageNameList"),
        appLabel = obj.firstOfList("m_applicationNameList"),
        text = obj.str("m_matchText")
    )
    "SendIntentAction" -> Action.Broadcast(
        packageName = obj.str("m_packageName"),
        className = obj.str("m_className"),
        action = obj.str("m_action"),
        extraName = obj.str("m_extra1Name"),
        extraValue = obj.str("m_extra1Value")
    )
    else -> null
}
