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

/** 알림 텍스트 매칭 — 조건이 비었으면 통과, 아니면 대소문자 무시 포함 검사 */
fun matchesText(needle: String, vararg haystack: String?): Boolean {
    if (needle.isBlank()) return true
    return haystack.any { it != null && it.contains(needle, ignoreCase = true) }
}

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
