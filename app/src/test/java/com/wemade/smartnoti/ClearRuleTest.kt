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
