package com.wemade.smartnoti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 알림 지우기 한 장짜리 화면이 어떤 매크로를 받아 주는지 — 판정이 틀리면 편집 내용이 날아간다 */
class ClearRuleTest {

    private fun macro(trigger: Trigger, vararg actions: Action) =
        Macro(id = 1, name = "t", triggers = listOf(trigger), actions = actions.toList())

    @Test
    fun `대기 없는 알림 삭제도 규칙으로 본다`() {
        val m = macro(
            Trigger.Notification("com.x", "X", "야옹"),
            Action.ClearNotification("com.x", "X", "야옹")
        )
        assertEquals(ClearRule("com.x", "X", "야옹", 0), m.asClearRule())
    }

    @Test
    fun `대기 하나 앞선 모양도 규칙으로 본다`() {
        val m = macro(
            Trigger.Notification("com.x", "X", "야옹"),
            Action.Delay(30),
            Action.ClearNotification("com.x", "X", "야옹")
        )
        assertEquals(30, m.asClearRule()?.seconds)
    }

    @Test
    fun `트리거 문구가 비어 있던 옛 매크로도 받는다`() {
        val m = macro(
            Trigger.Notification("com.x", "X", ""),
            Action.Delay(60),
            Action.ClearNotification("com.x", "X", "재부팅")
        )
        assertEquals("재부팅", m.asClearRule()?.text)
    }

    @Test
    fun `앱이 다르면 규칙이 아니다`() {
        val m = macro(
            Trigger.Notification("com.x", "X", "야옹"),
            Action.ClearNotification("com.y", "Y", "야옹")
        )
        assertNull(m.asClearRule())
    }

    @Test
    fun `단계가 더 엮여 있으면 규칙이 아니다`() {
        val m = macro(
            Trigger.Notification("com.x", "X", "야옹"),
            Action.Delay(5),
            Action.Broadcast(action = "stop"),
            Action.ClearNotification("com.x", "X", "야옹")
        )
        assertNull(m.asClearRule())
    }

    @Test
    fun `알림 트리거가 아니면 규칙이 아니다`() {
        val m = macro(Trigger.Wifi(true), Action.ClearNotification("com.x", "X", "야옹"))
        assertNull(m.asClearRule())
    }

    @Test
    fun `규칙으로 되편면 트리거 문구가 삭제 문구와 같아진다`() {
        val rule = ClearRule("com.x", "X", "재부팅", 60)
        val m = macro(Trigger.Notification(), Action.ClearNotification()).withClearRule(rule)

        assertEquals(Trigger.Notification("com.x", "X", "재부팅"), m.firstTrigger())
        assertEquals(listOf(Action.Delay(60), Action.ClearNotification("com.x", "X", "재부팅")), m.actions)
        assertEquals(rule, m.asClearRule())
    }

    /**
     * 편집 화면은 "고친 것이 있나"를 지금 모양과 처음 모양을 견주어 판단한다.
     * 그런데 옛 형식 매크로는 화면에 들어오는 것만으로 새 형식으로 펴진다.
     * 그 차이를 사람이 고친 것으로 세면, 아무것도 안 건드렸는데 저장하겠느냐고 묻게 된다.
     */
    @Test
    fun `옛 형식 매크로는 되펴는 것만으로 모양이 달라진다`() {
        val old = Macro(
            id = 1, name = "t",
            trigger = Trigger.Notification("com.x", "X", "야옹"),
            actions = listOf(Action.ClearNotification("com.x", "X", "야옹"))
        )
        val opened = old.withClearRule(old.asClearRule()!!)

        assertNotEquals(old, opened)
        assertEquals(old.asClearRule(), opened.asClearRule())
    }

    @Test
    fun `트리거 문구가 비어 있던 매크로도 되펴면 문구가 채워진다`() {
        val old = macro(
            Trigger.Notification("com.x", "X", ""),
            Action.ClearNotification("com.x", "X", "재부팅")
        )
        val opened = old.withClearRule(old.asClearRule()!!)

        assertNotEquals(old, opened)
        assertEquals("재부팅", (opened.firstTrigger() as Trigger.Notification).text)
    }

    @Test
    fun `대기가 0이면 대기 단계를 넣지 않는다`() {
        val m = macro(Trigger.Notification(), Action.ClearNotification())
            .withClearRule(ClearRule("com.x", "X", "야옹", 0))
        assertEquals(1, m.actions.size)
    }
}

/** 복제는 내용을 그대로 두되, 같은 트리거가 둘이 한꺼번에 돌지 않게 꺼 둔 채로 나온다 */
class DuplicateTest {

    @Test
    fun `복제본은 내용이 같고 꺼져 있다`() {
        val origin = Macro(
            id = 1, name = "토스 지우기", enabled = true, folder = "돈",
            triggers = listOf(Trigger.Notification("com.toss", "토스", "결제")),
            actions = listOf(Action.Delay(5), Action.ClearNotification("com.toss", "토스", "결제"))
        )
        val copy = origin.duplicate(2)

        assertEquals(2, copy.id)
        assertEquals("토스 지우기 복사본", copy.name)
        assertEquals(false, copy.enabled)
        assertEquals(origin.folder, copy.folder)
        assertEquals(origin.triggers, copy.triggers)
        assertEquals(origin.actions, copy.actions)
    }
}

/** 화면 밝기는 메뉴 항목 하나를 눌러 돌린다 — 한 바퀴 돌면 원래 자리로 와야 사람이 되돌릴 수 있다 */
class ThemeModeTest {

    @Test
    fun `기기 설정 다음은 밝게, 그다음은 어둡게`() {
        assertEquals(ThemeMode.Light, ThemeMode.System.next())
        assertEquals(ThemeMode.Dark, ThemeMode.Light.next())
    }

    @Test
    fun `세 번 누르면 처음으로 돌아온다`() {
        assertEquals(ThemeMode.System, ThemeMode.System.next().next().next())
    }
}

/** 폴더 다이얼로그가 매크로 이름 뒤에 붙이는 조사 — 틀리면 문장이 어색해진다 */
class ParticleTest {

    @Test
    fun `받침이 없으면 와, 있으면 과`() {
        assertEquals("와", "와이파이 붙으면 알리기".andParticle())
        assertEquals("과", "토스 알림".andParticle())
    }

    @Test
    fun `한글이 아니면 와로 둔다`() {
        assertEquals("와", "WireGuard".andParticle())
        assertEquals("와", "".andParticle())
    }
}

/** 알람에 맡긴 대기를 적어 둔 줄 — 잘못 읽으면 30분 기다린 일이 엉뚱한 단계에서 이어진다 */
class PendingWaitTest {

    @Test
    fun `남은 단계와 깨울 시각을 읽는다`() {
        assertEquals(3 to 1787227208064L, parse("3:1787227208064"))
    }

    @Test
    fun `형식이 깨졌으면 읽지 않는다`() {
        assertNull(parse("3"))
        assertNull(parse(""))
        assertNull(parse("a:b"))
    }
}

/**
 * 카드 아래 상태 한 줄. 이 앱이 "네가 안 볼 때 일어났다"를 지키는지 사람이 확인하는 유일한 자리다.
 * 시각을 밖에서 넣어 주므로 시계에 기대지 않고 굳힐 수 있다.
 */
class StatusLineTest {

    // 시간대를 고정한다. 안 하면 이 검사가 이 기계에서만 통과한다
    @org.junit.Before
    fun fixZone() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"))
    }

    // 2026-08-20 21:35 KST
    private val now = 1787229300000L
    private val hour = 3600_000L

    @Test
    fun `한 번도 안 돈 매크로는 그렇게 말한다`() {
        assertEquals("아직 안 돎", statusLine(now, null, null, null).plain)
    }

    @Test
    fun `이어질 예정이 마지막 결과보다 앞선다`() {
        val line = statusLine(now, now + hour, now - hour, MacroHistory.Outcome.Ran)
        assertEquals("이어서 함", line.tail)
    }

    @Test
    fun `오늘 것은 시각만, 어제 것은 어제를 붙인다`() {
        assertEquals("20:35 실행", statusLine(now, null, now - hour, MacroHistory.Outcome.Ran).plain)
        assertEquals(
            true,
            statusLine(now, null, now - 24 * hour, MacroHistory.Outcome.Ran).plain.startsWith("어제 ")
        )
    }

    @Test
    fun `실패와 멈춤을 다른 말로 적는다`() {
        assertEquals("20:35 실패", statusLine(now, null, now - hour, MacroHistory.Outcome.Failed).plain)
        assertEquals(
            "20:35 조건 안 맞아 멈춤",
            statusLine(now, null, now - hour, MacroHistory.Outcome.Stopped).plain
        )
    }

    @Test
    fun `시각만 고정폭으로 떼어 놓는다`() {
        val today = statusLine(now, null, now - hour, MacroHistory.Outcome.Ran)
        assertEquals("", today.lead)
        assertEquals("20:35", today.stamp)

        // 「아직 안 돎」은 기계값이 없으므로 통째로 사람 글이어야 한다
        val never = statusLine(now, null, null, null)
        assertEquals("", never.stamp)
        assertEquals("아직 안 돎", never.tail)

        assertEquals("어제", statusLine(now, null, now - 24 * hour, MacroHistory.Outcome.Ran).lead)
    }

    @Test
    fun `적어 둔 이력 한 줄을 되읽는다`() {
        assertEquals(MacroHistory.Entry(1787229300000L, MacroHistory.Outcome.Failed), parseEntry("1787229300000:Failed"))
        assertNull(parseEntry("1787229300000:Unknown"))
        assertNull(parseEntry("Failed"))
    }
}

/**
 * 브로드캐스트 프리셋과 비밀값 가리기.
 * 이 앱이 하는 일의 절반이 브로드캐스트인데 한 글자만 달라도 조용히 버려진다.
 */
class BroadcastPresetTest {

    @Test
    fun `프리셋이 네 칸을 채운다`() {
        val empty = Action.Broadcast()
        val up = empty.withPreset(broadcastPresets.first { it.label == "WireGuard 터널 켜기" })

        assertEquals(WIREGUARD_PACKAGE, up.packageName)
        assertEquals(WIREGUARD_RECEIVER, up.className)
        assertEquals("com.wireguard.android.action.SET_TUNNEL_UP", up.action)
        assertEquals("tunnel", up.extraName)
        assertEquals("", up.extraValue)
    }

    @Test
    fun `켜기에서 끄기로 바꿔도 터널 이름은 남는다`() {
        val up = Action.Broadcast()
            .withPreset(broadcastPresets.first { it.label == "WireGuard 터널 켜기" })
            .copy(extraValue = "home-server")
        val down = up.withPreset(broadcastPresets.first { it.label == "WireGuard 터널 끄기" })

        assertEquals("home-server", down.extraValue)
        assertEquals("com.wireguard.android.action.SET_TUNNEL_DOWN", down.action)
    }

    @Test
    fun `추가값 이름이 다르면 값을 옮기지 않는다`() {
        val wireGuard = Action.Broadcast()
            .withPreset(broadcastPresets.first { it.label == "WireGuard 터널 켜기" })
            .copy(extraValue = "home-server")
        val adGuard = wireGuard.withPreset(broadcastPresets.first { it.label == "AdGuard 보호 켜기" })

        assertEquals("", adGuard.extraValue)
    }

    @Test
    fun `비밀번호로 보이는 이름을 알아본다`() {
        assertEquals(true, isSecretExtra("password"))
        assertEquals(true, isSecretExtra("PASSWORD"))
        assertEquals(true, isSecretExtra("api_token"))
        assertEquals(false, isSecretExtra("tunnel"))
        assertEquals(false, isSecretExtra(""))
    }

    @Test
    fun `아는 브로드캐스트는 사람 말로 요약한다`() {
        val up = Action.Broadcast()
            .withPreset(broadcastPresets.first { it.label == "WireGuard 터널 켜기" })
            .copy(extraValue = "home-server")
        assertEquals("WireGuard 터널 켜기 · home-server", up.summary())
    }

    @Test
    fun `비밀값은 요약에도 내보내지 않는다`() {
        val adGuard = Action.Broadcast()
            .withPreset(broadcastPresets.first { it.label == "AdGuard 보호 끄기" })
            .copy(extraValue = "q3yXS")
        assertEquals("AdGuard 보호 끄기", adGuard.summary())
    }

    @Test
    fun `모르는 브로드캐스트는 액션 이름으로 남는다`() {
        val other = Action.Broadcast(packageName = "com.example.x", action = "GO")
        assertEquals("브로드캐스트 GO", other.summary())
    }
}

/** 저장을 막아야 하는 매크로 — 경고만 하고 통과시키면 영원히 멈추는 매크로가 남는다 */
class SaveProblemTest {

    private fun withCondition(condition: Condition) = Macro(
        id = 1, name = "t",
        triggers = listOf(Trigger.Wifi()),
        actions = listOf(Action.StopUnless(condition), Action.Broadcast(action = "GO"))
    )

    @Test
    fun `배터리 최소가 최대보다 크면 저장을 막는다`() {
        val problem = withCondition(Condition.Battery(atLeast = 90, atMost = 10)).saveProblem()
        assertEquals(true, problem?.contains("배터리"))
    }

    @Test
    fun `제대로 된 범위는 그냥 통과한다`() {
        assertNull(withCondition(Condition.Battery(atLeast = 10, atMost = 90)).saveProblem())
        assertNull(withCondition(Condition.Battery(atLeast = 50, atMost = 50)).saveProblem())
    }

    @Test
    fun `조건이 없는 매크로도 통과한다`() {
        val plain = Macro(
            id = 1, name = "t",
            triggers = listOf(Trigger.Wifi()),
            actions = listOf(Action.Broadcast(action = "GO"))
        )
        assertNull(plain.saveProblem())
    }
}

/** 브로드캐스트 요약 — 목록에서 두 WireGuard 매크로를 구별하는 유일한 글이다 */
class BroadcastSummaryTest {

    @Test
    fun `아는 것은 사람 말로, 값이 붙는다`() {
        val up = Action.Broadcast()
            .withPreset(broadcastPresets.first { it.label == "WireGuard 터널 켜기" })
            .copy(extraValue = "home-server")
        assertEquals("WireGuard 터널 켜기 · home-server", up.summary())
    }

    @Test
    fun `비밀값은 요약에서 빠진다`() {
        val ad = Action.Broadcast()
            .withPreset(broadcastPresets.first { it.label == "AdGuard 보호 끄기" })
            .copy(extraValue = "hunter2")
        assertEquals("AdGuard 보호 끄기", ad.summary())
    }

    @Test
    fun `모르는 것은 액션 이름으로 남는다`() {
        assertEquals(
            "브로드캐스트 GO",
            Action.Broadcast(packageName = "com.example.x", action = "GO").summary()
        )
    }

    @Test
    fun `값이 비면 꼬리를 붙이지 않는다`() {
        val up = Action.Broadcast().withPreset(broadcastPresets.first { it.label == "WireGuard 터널 켜기" })
        assertEquals("WireGuard 터널 켜기", up.summary())
    }
}

/**
 * 요약을 토막으로 나누는 일.
 * 사람이 고른 값과 앱이 붙인 문법을 가르는 기준이 흔들리면 화면에서 강조가 엉뚱해진다.
 */
class PartsTest {

    private fun values(parts: List<Part>) = parts.filterIsInstance<Part.Value>().map { it.text }

    @Test
    fun `이어 붙이면 예전 요약과 같다`() {
        val trigger = Trigger.Bluetooth("0C:29:8F:73:C7:F5", "Tesla Model Y Why", connected = false)
        assertEquals("Tesla Model Y Why 블루투스 해제", trigger.summary())
        assertEquals("Tesla Model Y Why 블루투스 해제", trigger.parts().flat())
    }

    @Test
    fun `기기 이름만 값이다`() {
        val trigger = Trigger.Bluetooth("0C:29:8F:73:C7:F5", "Tesla Model Y Why", connected = false)
        assertEquals(listOf("Tesla Model Y Why"), values(trigger.parts()))
    }

    @Test
    fun `와이파이는 고른 값이 없다`() {
        assertEquals(emptyList<String>(), values(Trigger.Wifi().parts()))
    }

    @Test
    fun `알림 트리거는 앱과 문구가 값이다`() {
        val trigger = Trigger.Notification("viva.republica.toss", "토스", "결제")
        assertEquals(listOf("토스", "“결제”"), values(trigger.parts()))
    }

    @Test
    fun `대기 시간은 값이고 대기라는 말은 문법이다`() {
        val parts = Action.Delay(1800).parts()
        assertEquals(listOf("30분"), values(parts))
        assertEquals("30분 대기", parts.flat())
    }

    @Test
    fun `프리셋 이름은 앱의 말, 터널 이름만 값이다`() {
        val up = Action.Broadcast()
            .withPreset(broadcastPresets.first { it.label == "WireGuard 터널 켜기" })
            .copy(extraValue = "home-server")
        assertEquals(listOf("home-server"), values(up.parts()))
    }

    @Test
    fun `비밀값은 토막에도 나오지 않는다`() {
        val ad = Action.Broadcast()
            .withPreset(broadcastPresets.first { it.label == "AdGuard 보호 끄기" })
            .copy(extraValue = "hunter2")
        assertEquals(emptyList<String>(), values(ad.parts()))
        assertEquals("AdGuard 보호 끄기", ad.parts().flat())
    }

    @Test
    fun `모르는 브로드캐스트는 액션 이름이 값이다`() {
        val other = Action.Broadcast(packageName = "com.example.x", action = "GO")
        assertEquals(listOf("GO"), values(other.parts()))
    }
}
