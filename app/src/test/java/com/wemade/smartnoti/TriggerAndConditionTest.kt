package com.wemade.smartnoti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 트리거를 여러 개 두는 형식과, 옛 파일 한 개 형식이 같이 살아야 한다 */
class TriggerAndConditionTest {

    @Test
    fun `트리거 하나만 있던 옛 형식도 읽힌다`() {
        val old = Macro(id = 1, name = "옛것", trigger = Trigger.Wifi(true))
        assertEquals(listOf(Trigger.Wifi(true)), old.allTriggers())
    }

    @Test
    fun `새 형식이 있으면 그것만 본다`() {
        val both = Macro(
            id = 1, name = "둘 다",
            trigger = Trigger.Wifi(true),
            triggers = listOf(Trigger.Notification("com.x"))
        )
        assertEquals(listOf(Trigger.Notification("com.x")), both.allTriggers())
    }

    @Test
    fun `저장하면 옛 칸은 비워 둔다`() {
        val m = Macro(id = 1, name = "x", trigger = Trigger.Wifi(true))
            .withTriggers(listOf(Trigger.Wifi(false)))
        assertNull(m.trigger)
        assertEquals(listOf(Trigger.Wifi(false)), m.triggers)
    }

    @Test
    fun `트리거가 여럿이면 한 문장으로 접지 않는다`() {
        val m = Macro(
            id = 1, name = "둘",
            triggers = listOf(Trigger.Notification("com.x"), Trigger.Notification("com.y")),
            actions = listOf(Action.ClearNotification("com.x"))
        )
        assertNull(m.asClearRule())
    }

    @Test
    fun `여러 트리거는 또는으로 읽힌다`() {
        val m = Macro(
            id = 1, name = "둘",
            triggers = listOf(Trigger.Wifi(true), Trigger.Wifi(false)),
            actions = listOf(Action.Broadcast(action = "go"))
        )
        assertTrue(m.oneLine().contains("또는"))
    }

    @Test
    fun `백업을 오가도 트리거와 조건이 그대로다`() {
        val m = Macro(
            id = 7, name = "조건 있는 매크로",
            triggers = listOf(Trigger.Wifi(true), Trigger.Bluetooth("AA:BB", "차", true)),
            actions = listOf(
                Action.StopUnless(Condition.TimeRange(23 * 60, 7 * 60)),
                Action.StopUnless(Condition.Place("집", 37.5, 127.0, 300, true)),
                Action.ClearNotification("com.x", "X", "야옹")
            )
        )
        assertEquals(listOf(m), importBackup(exportBackup(listOf(m))).macros)
    }

    @Test
    fun `자정을 넘기는 시간대도 글로 적힌다`() {
        assertEquals("23:00~07:00 사이일 때만", Condition.TimeRange(23 * 60, 7 * 60).summary())
    }
}
