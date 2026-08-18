package com.wemade.smartnoti

import kotlinx.serialization.Serializable

/** 매크로 하나 = 트리거 1개 + 액션 여러 개 (위에서 아래로 순서대로 실행) */
@Serializable
data class Macro(
    val id: Long,
    val name: String,
    val enabled: Boolean = true,
    val trigger: Trigger,
    val actions: List<Action> = emptyList()
)

@Serializable
sealed class Trigger {
    /** 알림이 뜰 때. text가 비면 그 앱의 모든 알림에 반응 */
    @Serializable
    data class Notification(
        val packageName: String = "",
        val appLabel: String = "",
        val text: String = ""
    ) : Trigger()

    /** 블루투스 기기가 붙거나 떨어질 때 */
    @Serializable
    data class Bluetooth(
        val address: String = "",
        val deviceName: String = "",
        val connected: Boolean = true
    ) : Trigger()

    /** 와이파이가 붙거나 떨어질 때 */
    @Serializable
    data class Wifi(val connected: Boolean = true) : Trigger()
}

@Serializable
sealed class Action {
    /** 지정한 앱의 알림을 지운다. text가 비면 그 앱 알림 전부 */
    @Serializable
    data class ClearNotification(
        val packageName: String = "",
        val appLabel: String = "",
        val text: String = "",
        /** 진행 중이라 손으로도 못 지우는 알림까지 건드릴지 */
        val includeOngoing: Boolean = false
    ) : Action()

    /** 다른 앱에 브로드캐스트를 쏜다 (AdGuard 켜기/끄기 등) */
    @Serializable
    data class Broadcast(
        val packageName: String = "",
        val className: String = "",
        val action: String = "",
        val extraName: String = "",
        val extraValue: String = ""
    ) : Action()

    /** 다음 액션까지 기다린다 */
    @Serializable
    data class Delay(val seconds: Int = 5) : Action()

    /** 블루투스 기기 상태가 조건과 맞으면 남은 액션을 실행하지 않고 멈춘다 */
    @Serializable
    data class StopIfBluetooth(
        val address: String = "",
        val deviceName: String = "",
        val connected: Boolean = true
    ) : Action()
}

/**
 * 알림 지우기 규칙 — "이 앱에 이 문구가 뜨면 N초 뒤 지운다".
 *
 * 쓰던 매크로 대부분이 이 한 가지 모양이었다. 저장 형태는 그대로 트리거+액션이고,
 * 이건 그 모양을 알아보고 한 장짜리 화면으로 다루기 위한 창구다.
 */
data class ClearRule(
    val packageName: String = "",
    val appLabel: String = "",
    val text: String = "",
    val seconds: Int = 0,
    val includeOngoing: Boolean = false
)

/** 이 매크로가 알림 지우기 한 장으로 다룰 수 있는 모양인지. 아니면 null */
fun Macro.asClearRule(): ClearRule? {
    val trig = trigger as? Trigger.Notification ?: return null
    val clear = actions.lastOrNull() as? Action.ClearNotification ?: return null
    // 앞에 올 수 있는 건 대기 하나뿐. 그 이상 엮여 있으면 직접 짜기로 다룬다
    val head = actions.dropLast(1)
    val seconds = when {
        head.isEmpty() -> 0
        head.size == 1 -> (head[0] as? Action.Delay)?.seconds ?: return null
        else -> return null
    }
    if (trig.packageName != clear.packageName) return null
    // 트리거 문구가 비어 있던 옛 매크로도 받아 준다. 저장할 때 삭제 문구로 맞춰진다
    if (trig.text.isNotBlank() && trig.text != clear.text) return null
    return ClearRule(
        packageName = clear.packageName,
        appLabel = clear.appLabel.ifBlank { trig.appLabel },
        text = clear.text,
        seconds = seconds,
        includeOngoing = clear.includeOngoing
    )
}

/**
 * 규칙을 트리거+액션으로 되편다.
 * 트리거 문구를 삭제 문구와 같게 맞춰, 상관없는 알림에 매크로가 헛도는 일을 없앤다.
 */
fun Macro.withClearRule(rule: ClearRule): Macro = copy(
    trigger = Trigger.Notification(rule.packageName, rule.appLabel, rule.text),
    actions = buildList {
        if (rule.seconds > 0) add(Action.Delay(rule.seconds))
        add(
            Action.ClearNotification(
                packageName = rule.packageName,
                appLabel = rule.appLabel,
                text = rule.text,
                includeOngoing = rule.includeOngoing
            )
        )
    }
)

/** 목록에 뿌릴 한 줄 — 어느 앱의 무슨 문구를 언제 지우는지 */
fun ClearRule.summary(): String {
    val app = appLabel.ifBlank { packageName.ifBlank { "모든 앱" } }
    val what = if (text.isBlank()) "알림 전부" else "\"$text\""
    val when_ = if (seconds > 0) "${humanSeconds(seconds)} 뒤" else "바로"
    return "$app · $what · $when_ 지움"
}

/**
 * 이 매크로가 무엇을 하는지 한 줄로.
 * 설정을 만지는 동안 결과가 바로 보이게 하려는 것이다 — 저장하고 목록에 가서야 알 일이 아니다.
 */
fun Macro.oneLine(): String = asClearRule()?.summary()
    ?: (trigger.summary() + if (actions.isEmpty()) " · 하는 일 없음" else " · " + actions.joinToString(", ") { it.summary() })

/** 1800초가 몇 분인지 사람이 세지 않게 한다 */
fun humanSeconds(seconds: Int): String = when {
    seconds <= 0 -> "기다리지 않음"
    seconds < 60 -> "${seconds}초"
    seconds % 3600 == 0 -> "${seconds / 3600}시간"
    seconds % 60 == 0 -> "${seconds / 60}분"
    else -> "${seconds / 60}분 ${seconds % 60}초"
}

/** 지금 떠 있는 알림 한 줄 */
data class NotificationPeek(
    val packageName: String,
    val title: String,
    val text: String,
    val clearable: Boolean
)

/** 알림 텍스트 매칭 — 조건이 비었으면 통과, 아니면 대소문자 무시 포함 검사 */
fun matchesText(needle: String, haystack: List<String?>): Boolean {
    if (needle.isBlank()) return true
    return haystack.any { it != null && it.contains(needle, ignoreCase = true) }
}

fun matchesText(needle: String, vararg haystack: String?): Boolean =
    matchesText(needle, haystack.toList())

/** 매크로 목록 화면에 뿌릴 한 줄 요약 */
fun Trigger.summary(): String = when (this) {
    is Trigger.Notification -> {
        val app = appLabel.ifBlank { packageName.ifBlank { "모든 앱" } }
        if (text.isBlank()) "$app 알림" else "$app 알림 \"$text\""
    }
    is Trigger.Bluetooth -> {
        val device = deviceName.ifBlank { address.ifBlank { "모든 기기" } }
        "$device 블루투스 " + if (connected) "연결" else "해제"
    }
    is Trigger.Wifi -> "와이파이 " + if (connected) "연결" else "해제"
}

fun Action.summary(): String = when (this) {
    is Action.ClearNotification -> {
        val app = appLabel.ifBlank { packageName.ifBlank { "모든 앱" } }
        if (text.isBlank()) "$app 알림 삭제" else "$app 알림 삭제 \"$text\""
    }
    is Action.Broadcast -> "브로드캐스트 " + action.ifBlank { className.ifBlank { packageName } }
    is Action.Delay -> "${seconds}초 대기"
    is Action.StopIfBluetooth -> {
        val device = deviceName.ifBlank { address.ifBlank { "기기" } }
        "$device " + (if (connected) "연결됐으면" else "끊겼으면") + " 중단"
    }
}
