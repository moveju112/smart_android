package com.wemade.smartnoti

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * MacroDroid 백업 파일(.mdr) 읽기·쓰기.
 * 두 앱이 다루는 범위가 달라서 겹치는 부분만 옮기고, 못 옮긴 것은 이름을 그대로 돌려준다.
 */

/** 가져오기 결과 — 옮긴 매크로와, 옮기지 못한 항목의 이름 */
data class ImportResult(
    val macros: List<Macro>,
    val skippedMacros: List<String>,
    val partialMacros: List<String>
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val prettyJson = Json { prettyPrint = true }

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

/** 이 앱의 매크로를 MacroDroid가 읽는 .mdr 본문으로 만든다 */
fun exportMdr(macros: List<Macro>): String {
    val out = buildJsonObject {
        put("exportFormat", 2)
        put("exportAppVersion", 596500009)
        put("timestamp", System.currentTimeMillis())
        put("macroList", buildJsonArray {
            macros.forEach { add(toMdrMacro(it)) }
        })
    }
    return prettyJson.encodeToString(JsonObject.serializer(), out)
}

private fun sig() = Random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE)

/** MacroDroid는 빠진 필드가 있으면 파일을 통째로 거부한다. 그래서 기본값까지 전부 채운다 */
private fun toMdrMacro(macro: Macro): JsonObject = buildJsonObject {
    put("aiGenerated", 0)
    put("breakpoints", buildJsonArray {})
    put("disabledTimestamp", 0)
    put("exportedActionBlocks", buildJsonArray {})
    put("forceEvenIfNotEnabledTimestamp", 0)
    put("isActionBlock", false)
    put("isExtra", false)
    put("isFavourite", false)
    put("lastEditedTimestamp", System.currentTimeMillis())
    put("localVariables", buildJsonArray {})
    put("localVarsAlphabetical", true)
    put("m_GUID", sig())
    put("m_category", "카테고리 미지정")
    put("m_completed", true)
    put("m_constraintList", buildJsonArray {})
    put("m_description", "")
    put("m_descriptionOpen", false)
    put("m_enabled", macro.enabled)
    put("m_excludeLog", false)
    put("m_headingColor", 0)
    put("m_isOrCondition", false)
    put("m_name", macro.name)
    put("m_triggerList", buildJsonArray { add(toMdrTrigger(macro.trigger)) })
    put("m_actionList", buildJsonArray {
        macro.actions.forEach { action -> toMdrAction(action)?.let { add(it) } }
    })
}

/** 트리거·액션에 공통으로 붙는 뼈대 */
private fun kotlinx.serialization.json.JsonObjectBuilder.common(classType: String) {
    put("disableLogging", false)
    put("m_SIGUID", sig())
    put("m_classType", classType)
    put("m_comment", "")
    put("m_constraintList", buildJsonArray {})
    put("m_isDisabled", false)
    put("m_isOrCondition", false)
}

private fun toMdrTrigger(trigger: Trigger): JsonObject = when (trigger) {
    is Trigger.Notification -> buildJsonObject {
        put("enableRegex", false)
        put("ignoreCase", true)
        put("m_applicationNameList", buildJsonArray { add(trigger.appLabel) })
        put("m_exactMatch", false)
        put("m_excludeApps", false)
        put("m_excludes", false)
        put("m_ignoreOngoing", false)
        put("m_option", 0)
        put("m_packageNameList", buildJsonArray { add(trigger.packageName) })
        put("m_soundOption", 0)
        put("m_supressMultiples", true)
        put("m_textContent", trigger.text)
        put("matchOptionMessage", 0)
        put("matchOptionTitle", 0)
        put("separateTitleAndMessage", false)
        common("NotificationTrigger")
    }
    is Trigger.Bluetooth -> buildJsonObject {
        put("m_anyDevice", trigger.address.isBlank())
        put("m_btState", if (trigger.connected) 2 else 3)
        put("m_deviceAddress", trigger.address)
        put("m_deviceAlias", trigger.deviceName)
        put("m_deviceName", trigger.deviceName)
        common("BluetoothTrigger")
    }
    is Trigger.Wifi -> buildJsonObject {
        put("customAddedSSIDs", buildJsonArray {})
        put("m_SSIDList", buildJsonArray {})
        put("m_wifiState", if (trigger.connected) 0 else 1)
        common("WifiConnectionTrigger")
    }
}

/** 조건부 중단은 MacroDroid에 대응하는 액션이 없어 내보내지 않는다 */
private fun toMdrAction(action: Action): JsonObject? = when (action) {
    is Action.Delay -> buildJsonObject {
        put("m_delayInMilliSeconds", 0)
        put("m_delayInSeconds", action.seconds)
        put("m_useAlarm", true)
        put("unitForVariables", 0)
        common("PauseAction")
    }
    is Action.ClearNotification -> buildJsonObject {
        put("enableRegex", false)
        put("ignoreCase", true)
        put("m_ageInSeconds", 0)
        put("m_applicationNameList", buildJsonArray { add(action.appLabel) })
        put("m_clearPersistent", false)
        put("m_excludes", false)
        put("m_matchOption", if (action.text.isBlank()) 0 else 2)
        put("m_matchText", action.text)
        put("m_option", 1)
        put("m_packageNameList", buildJsonArray { add(action.packageName) })
        put("matchOptionMessage", 0)
        put("matchOptionTitle", 0)
        put("separateTitleAndMessage", false)
        common("ClearNotificationsAction")
    }
    is Action.Broadcast -> buildJsonObject {
        put("EXTRA_TYPE_AUTO", 0); put("EXTRA_TYPE_BOOLEAN", 2); put("EXTRA_TYPE_DOUBLE", 6)
        put("EXTRA_TYPE_FLOAT", 5); put("EXTRA_TYPE_INT", 3); put("EXTRA_TYPE_LONG", 4)
        put("EXTRA_TYPE_STRING", 1); put("EXTRA_TYPE_STRING_ARRAY", 7)
        put("m_action", action.action)
        put("m_className", action.className)
        put("m_data", "")
        put("m_extra1Name", action.extraName)
        put("m_extra1Type", 0)
        put("m_extra1Value", action.extraValue)
        for (i in 2..6) {
            put("m_extra${i}Name", ""); put("m_extra${i}Type", 0); put("m_extra${i}Value", "")
        }
        put("m_flags", 0)
        put("m_mimeType", "")
        put("m_packageName", action.packageName)
        put("m_target", "Broadcast")
        put("useCustomCategory", false)
        common("SendIntentAction")
    }
    is Action.StopIfBluetooth -> null
}
