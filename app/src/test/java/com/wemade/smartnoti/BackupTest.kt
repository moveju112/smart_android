package com.wemade.smartnoti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백업은 되돌아와야 쓸모가 있다. 형식을 갈아탄 뒤로 이게 유일한 안전망이다 */
class BackupTest {

    private val sample = listOf(
        Macro(
            id = 1, name = "토스 알림 삭제",
            trigger = Trigger.Notification("viva.republica.toss", "토스", "MSTU"),
            actions = listOf(Action.Delay(5), Action.ClearNotification("viva.republica.toss", "토스", "MSTU"))
        ),
        Macro(
            id = 2, name = "차 타면 끄기", enabled = false,
            trigger = Trigger.Bluetooth("AA:BB:CC:DD:EE:FF", "내 차", connected = true),
            actions = listOf(
                Action.StopIfBluetooth("AA:BB:CC:DD:EE:FF", "내 차", connected = true),
                Action.Broadcast("com.example.guard", "com.example.guard.Receiver", "stop", "password", "secret")
            )
        ),
        Macro(id = 3, name = "와이파이", trigger = Trigger.Wifi(connected = false))
    )

    @Test
    fun `내보낸 그대로 돌아온다`() {
        assertEquals(sample, importBackup(exportBackup(sample)).macros)
    }

    @Test
    fun `조건부 중단도 살아남는다`() {
        // MacroDroid 형식에서는 버려지던 액션이다
        val back = importBackup(exportBackup(sample)).macros[1]
        assertEquals(sample[1].actions, back.actions)
    }

    @Test
    fun `MacroDroid 백업도 알아본다`() {
        val mdr = """{"exportFormat":2,"macroList":[{"m_name":"옛 매크로","m_enabled":true,
            "m_triggerList":[{"m_classType":"WifiConnectionTrigger","m_wifiState":0}],
            "m_actionList":[]}]}"""
        val result = importBackup(mdr)

        assertEquals(1, result.macros.size)
        assertEquals("옛 매크로", result.macros[0].name)
    }

    @Test
    fun `빈 목록도 오간다`() {
        assertTrue(importBackup(exportBackup(emptyList())).macros.isEmpty())
    }
}
