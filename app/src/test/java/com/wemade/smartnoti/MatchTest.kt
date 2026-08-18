package com.wemade.smartnoti

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchTest {

    @Test
    fun `조건이 비면 무조건 통과`() {
        assertTrue(matchesText("", "아무거나", null))
    }

    @Test
    fun `제목이나 본문 어디든 들어있으면 통과`() {
        assertTrue(matchesText("MSTU", "토스", "MSTU 알림"))
        assertTrue(matchesText("rclone", "rclone 동기화 중", null))
        assertFalse(matchesText("MSTU", "토스", "결제 알림"))
    }

    @Test
    fun `대소문자는 무시한다`() {
        assertTrue(matchesText("rclone", "RClone", null))
    }

    @Test
    fun `연결됨 조건이 연결 해제됨 알림을 잡으면 안 된다`() {
        assertTrue(matchesText("연결됨", "Tesla", "내 차 연결됨"))
        assertFalse(matchesText("연결됨", "Tesla", "내 차 연결 해제됨"))
    }

    @Test
    fun `기본 매크로는 JSON 왕복 후에도 같다`() {
        val json = Json { ignoreUnknownKeys = true }
        val before = MacroStore.macros.value  // 비어 있어도 되지만 실제 모델로도 확인한다
        assertEquals(emptyList<Macro>(), before)

        val sample = listOf(
            Macro(
                id = 1, name = "테스트",
                trigger = Trigger.Notification("com.a", "A", "hi"),
                actions = listOf(Action.Delay(5), Action.ClearNotification("com.a", "A", "hi"), Action.Broadcast("p", "c", "start", "k", "v"))
            ),
            Macro(id = 2, name = "BT", trigger = Trigger.Bluetooth("AA:BB", "차", false)),
            Macro(id = 3, name = "WIFI", trigger = Trigger.Wifi(true))
        )
        val round = json.decodeFromString<List<Macro>>(json.encodeToString<List<Macro>>(sample))
        assertEquals(sample, round)
    }
}
