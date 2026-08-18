package com.wemade.smartnoti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 알림 지우기 한 장짜리 화면이 어떤 매크로를 받아 주는지 — 판정이 틀리면 편집 내용이 날아간다 */
class ClearRuleTest {

    private fun macro(trigger: Trigger, vararg actions: Action) =
        Macro(id = 1, name = "t", trigger = trigger, actions = actions.toList())

    @Test
    fun `대기 없는 알림 삭제도 규칙으로 본다`() {
        val m = macro(
            Trigger.Notification("com.x", "X", "야옹"),
            Action.ClearNotification("com.x", "X", "야옹")
        )
        assertEquals(ClearRule("com.x", "X", "야옹", 0, false), m.asClearRule())
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
        val rule = ClearRule("com.x", "X", "재부팅", 60, true)
        val m = macro(Trigger.Notification(), Action.ClearNotification()).withClearRule(rule)

        assertEquals(Trigger.Notification("com.x", "X", "재부팅"), m.trigger)
        assertEquals(listOf(Action.Delay(60), Action.ClearNotification("com.x", "X", "재부팅", true)), m.actions)
        assertEquals(rule, m.asClearRule())
    }

    @Test
    fun `대기가 0이면 대기 단계를 넣지 않는다`() {
        val m = macro(Trigger.Notification(), Action.ClearNotification())
            .withClearRule(ClearRule("com.x", "X", "야옹", 0, false))
        assertEquals(1, m.actions.size)
    }
}
