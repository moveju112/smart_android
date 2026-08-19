package com.wemade.smartnoti

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 화면을 돌릴 때 편집 중인 매크로를 잠깐 접어 두는 데 쓴다 */
internal val macroJson = Json { ignoreUnknownKeys = true }

/**
 * 매크로 하나 = 트리거 여러 개 + 액션 여러 개.
 * 트리거는 그중 아무거나 걸리면 돈다. 액션은 위에서 아래로 차례대로 실행한다.
 */
@Serializable
data class Macro(
    val id: Long,
    val name: String,
    val enabled: Boolean = true,
    /** 트리거 하나만 있던 옛 파일을 읽기 위해 남겨 둔다. 저장할 때는 [triggers]만 채운다 */
    val trigger: Trigger? = null,
    val triggers: List<Trigger> = emptyList(),
    val actions: List<Action> = emptyList()
)

/** 옛 형식과 새 형식을 한 줄로 합친다 */
fun Macro.allTriggers(): List<Trigger> = triggers.ifEmpty { listOfNotNull(trigger) }

/** 첫 트리거. 화면에 한 줄로 적을 때 쓴다 */
fun Macro.firstTrigger(): Trigger? = allTriggers().firstOrNull()

/** 저장 형태를 새 형식으로 맞춘다 */
fun Macro.withTriggers(list: List<Trigger>): Macro = copy(trigger = null, triggers = list)

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
        val text: String = ""
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

    /** 블루투스 기기 상태가 조건과 맞으면 멈춘다. 옛 매크로에만 남아 있다 ([StopUnless]로 대신한다) */
    @Serializable
    data class StopIfBluetooth(
        val address: String = "",
        val deviceName: String = "",
        val connected: Boolean = true
    ) : Action()

    /** 조건이 맞지 않으면 남은 액션을 실행하지 않고 멈춘다 */
    @Serializable
    data class StopUnless(val condition: Condition = Condition.Wifi()) : Action()
}

/**
 * "이럴 때만 계속한다"를 적는 곳.
 *
 * 트리거는 일이 벌어진 순간을 잡고, 조건은 그 순간의 상태를 본다.
 * 둘을 합치면 "차에서 내렸고, 집이 아닐 때만" 같은 말이 된다.
 */
@Serializable
sealed class Condition {

    /** 그 기기가 붙어 있을 때만 (connected=false면 끊겨 있을 때만) */
    @Serializable
    data class Bluetooth(
        val address: String = "",
        val deviceName: String = "",
        val connected: Boolean = true
    ) : Condition()

    /** 와이파이에 붙어 있을 때만 */
    @Serializable
    data class Wifi(val connected: Boolean = true) : Condition()

    /**
     * 하루 중 이 시간대일 때만. 분 단위로 0(자정)부터 1439까지.
     * from이 to보다 크면 자정을 넘기는 구간이다 (23:00~07:00).
     */
    @Serializable
    data class TimeRange(
        val fromMinute: Int = 0,
        val toMinute: Int = 0,
        val inside: Boolean = true
    ) : Condition()

    /** 배터리가 이 범위일 때만. 충전 중인지도 함께 볼 수 있다 */
    @Serializable
    data class Battery(
        val atLeast: Int = 0,
        val atMost: Int = 100,
        val charging: Boolean? = null
    ) : Condition()

    /**
     * 이 자리 근처일 때만 (inside=false면 벗어나 있을 때만).
     *
     * 위치는 새로 잡지 않고 다른 앱이 이미 받아 둔 마지막 값을 읽는다.
     * 그래서 배터리를 쓰지 않지만, 값이 조금 묵을 수 있다.
     */
    @Serializable
    data class Place(
        val label: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val radiusMeters: Int = 200,
        val inside: Boolean = true
    ) : Condition()
}

/** 조건 한 줄 요약 */
fun Condition.summary(): String = when (this) {
    is Condition.Bluetooth -> {
        val device = deviceName.ifBlank { address.ifBlank { "기기" } }
        "$device 가 " + (if (connected) "붙어 있을 때만" else "끊겨 있을 때만")
    }
    is Condition.Wifi -> "와이파이에 " + (if (connected) "붙어 있을 때만" else "붙어 있지 않을 때만")
    is Condition.TimeRange ->
        "${clockText(fromMinute)}~${clockText(toMinute)}" + (if (inside) " 사이일 때만" else " 를 벗어났을 때만")
    is Condition.Battery -> buildString {
        append("배터리 $atLeast~$atMost%")
        when (charging) {
            true -> append(", 충전 중")
            false -> append(", 충전 중이 아닐 때")
            null -> {}
        }
        append("일 때만")
    }
    is Condition.Place -> {
        val place = label.ifBlank { "정한 자리" }
        "$place 에서 ${radiusMeters}m " + (if (inside) "안일 때만" else "밖일 때만")
    }
}

/** 분을 시:분으로 */
fun clockText(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
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
    val seconds: Int = 0
)

/** 이 매크로가 알림 지우기 한 장으로 다룰 수 있는 모양인지. 아니면 null */
fun Macro.asClearRule(): ClearRule? {
    // 트리거가 여럿이면 한 문장으로 접을 수 없다
    if (allTriggers().size > 1) return null
    val trig = firstTrigger() as? Trigger.Notification ?: return null
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
        seconds = seconds
    )
}

/**
 * 규칙을 트리거+액션으로 되편다.
 * 트리거 문구를 삭제 문구와 같게 맞춰, 상관없는 알림에 매크로가 헛도는 일을 없앤다.
 */
fun Macro.withClearRule(rule: ClearRule): Macro = withTriggers(
    listOf(Trigger.Notification(rule.packageName, rule.appLabel, rule.text))
).copy(
    actions = buildList {
        if (rule.seconds > 0) add(Action.Delay(rule.seconds))
        add(
            Action.ClearNotification(
                packageName = rule.packageName,
                appLabel = rule.appLabel,
                text = rule.text
            )
        )
    }
)

/** 목록에 뿌릴 한 줄 — 어느 앱의 무슨 문구를 언제 지우는지 */
fun ClearRule.summary(): String {
    val app = appLabel.ifBlank { packageName.ifBlank { "모든 앱" } }
    val what = if (text.isBlank()) "알림 전부" else "\u201C$text\u201D"
    val when_ = if (seconds > 0) "${humanSeconds(seconds)} 뒤" else "바로"
    return "$app · $what · $when_ 지움"
}

/**
 * 이 매크로가 무엇을 하는지 한 줄로.
 * 설정을 만지는 동안 결과가 바로 보이게 하려는 것이다 — 저장하고 목록에 가서야 알 일이 아니다.
 */
fun Macro.oneLine(): String = asClearRule()?.summary()
    ?: (allTriggers().joinToString(" 또는 ") { it.summary() }.ifBlank { "언제인지 안 정함" } +
        if (actions.isEmpty()) " · 하는 일 없음" else " · " + actions.joinToString(", ") { it.summary() })

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
    val appLabel: String,
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
        if (text.isBlank()) "$app 알림" else "$app 알림 \u201C$text\u201D"
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
        if (text.isBlank()) "$app 알림 삭제" else "$app 알림 삭제 \u201C$text\u201D"
    }
    is Action.Broadcast -> "브로드캐스트 " + action.ifBlank { className.ifBlank { packageName } }
    is Action.Delay -> "${seconds}초 대기"
    is Action.StopIfBluetooth -> {
        val device = deviceName.ifBlank { address.ifBlank { "기기" } }
        "$device " + (if (connected) "연결됐으면" else "끊겼으면") + " 중단"
    }
    is Action.StopUnless -> condition.summary()
}
