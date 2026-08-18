package com.wemade.smartnoti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MacroDroid 백업을 실제 파일과 같은 모양으로 줄여 놓은 표본 */
private val SAMPLE_MDR = """
{
  "exportFormat": 2,
  "exportAppVersion": 596500009,
  "macroList": [
    {
      "m_name": "알림 지우기",
      "m_enabled": true,
      "m_triggerList": [{
        "m_classType": "NotificationTrigger",
        "m_applicationNameList": ["예시앱"],
        "m_packageNameList": ["com.example.app"],
        "m_textContent": "결제"
      }],
      "m_actionList": [
        {"m_classType": "PauseAction", "m_delayInSeconds": 5, "m_delayInMilliSeconds": 0},
        {"m_classType": "ClearNotificationsAction",
         "m_applicationNameList": ["예시앱"],
         "m_packageNameList": ["com.example.app"],
         "m_matchText": "결제"}
      ]
    },
    {
      "m_name": "차 타면 끄기",
      "m_enabled": true,
      "m_triggerList": [{
        "m_classType": "BluetoothTrigger",
        "m_btState": 2,
        "m_deviceAddress": "AA:BB:CC:DD:EE:FF",
        "m_deviceName": "내 차"
      }],
      "m_actionList": [
        {"m_classType": "SendIntentAction",
         "m_action": "stop",
         "m_packageName": "com.example.guard",
         "m_className": "com.example.guard.Receiver",
         "m_extra1Name": "password",
         "m_extra1Value": "secret"}
      ]
    },
    {
      "m_name": "차 내리면 켜기",
      "m_enabled": false,
      "m_triggerList": [{
        "m_classType": "BluetoothTrigger",
        "m_btState": 3,
        "m_deviceAddress": "AA:BB:CC:DD:EE:FF",
        "m_deviceName": "내 차"
      }],
      "m_actionList": [
        {"m_classType": "PauseAction", "m_delayInSeconds": 1800, "m_delayInMilliSeconds": 0},
        {"m_classType": "IfConditionAction"},
        {"m_classType": "ElseAction"},
        {"m_classType": "EndIfAction"}
      ]
    },
    {
      "m_name": "이 앱이 모르는 트리거",
      "m_enabled": true,
      "m_triggerList": [{"m_classType": "ShakeDeviceTrigger"}],
      "m_actionList": []
    }
  ]
}
""".trimIndent()

class MdrFormatTest {

    @Test
    fun `옮길 수 있는 매크로만 가져오고 나머지는 이름을 돌려준다`() {
        val result = importMdr(SAMPLE_MDR)

        assertEquals(3, result.macros.size)
        assertEquals(listOf("이 앱이 모르는 트리거"), result.skippedMacros)
        // If/Else/EndIf 3개를 못 옮겼다고 알려야 한다
        assertTrue(result.partialMacros.any { it.startsWith("차 내리면 켜기") })
    }

    @Test
    fun `알림 트리거와 액션이 그대로 옮겨진다`() {
        val macro = importMdr(SAMPLE_MDR).macros[0]

        assertEquals("알림 지우기", macro.name)
        assertEquals(Trigger.Notification("com.example.app", "예시앱", "결제"), macro.trigger)
        assertEquals(Action.Delay(5), macro.actions[0])
        assertEquals(Action.ClearNotification("com.example.app", "예시앱", "결제"), macro.actions[1])
    }

    @Test
    fun `블루투스 상태값 2는 연결 3은 해제다`() {
        val macros = importMdr(SAMPLE_MDR).macros

        assertEquals(Trigger.Bluetooth("AA:BB:CC:DD:EE:FF", "내 차", connected = true), macros[1].trigger)
        assertEquals(Trigger.Bluetooth("AA:BB:CC:DD:EE:FF", "내 차", connected = false), macros[2].trigger)
    }

    @Test
    fun `꺼둔 매크로는 꺼진 채로 온다`() {
        assertEquals(false, importMdr(SAMPLE_MDR).macros[2].enabled)
    }

    @Test
    fun `브로드캐스트의 추가값까지 옮겨진다`() {
        val action = importMdr(SAMPLE_MDR).macros[1].actions[0]
        assertEquals(
            Action.Broadcast("com.example.guard", "com.example.guard.Receiver", "stop", "password", "secret"),
            action
        )
    }

    @Test
    fun `내보낸 파일을 다시 가져오면 내용이 같다`() {
        val original = importMdr(SAMPLE_MDR).macros
        val roundTrip = importMdr(exportMdr(original)).macros

        assertEquals(original.size, roundTrip.size)
        original.forEachIndexed { index, macro ->
            assertEquals(macro.name, roundTrip[index].name)
            assertEquals(macro.enabled, roundTrip[index].enabled)
            assertEquals(macro.trigger, roundTrip[index].trigger)
            assertEquals(macro.actions, roundTrip[index].actions)
        }
    }

    @Test
    fun `내보낸 파일에 MacroDroid가 요구하는 필드가 들어 있다`() {
        val text = exportMdr(importMdr(SAMPLE_MDR).macros)

        listOf(
            "exportFormat", "macroList", "m_GUID", "m_SIGUID", "m_classType",
            "m_triggerList", "m_actionList", "m_constraintList", "m_category", "m_completed"
        ).forEach { field ->
            assertTrue("내보낸 파일에 $field 가 없다", text.contains("\"$field\""))
        }
    }

    @Test
    fun `조건부 중단은 MacroDroid에 대응이 없어 빠진다`() {
        val macro = Macro(
            id = 1, name = "조건 있는 매크로",
            trigger = Trigger.Wifi(true),
            actions = listOf(Action.StopIfBluetooth("AA:BB", "차", true), Action.Delay(3))
        )
        val back = importMdr(exportMdr(listOf(macro))).macros.single()

        assertEquals(listOf(Action.Delay(3)), back.actions)
    }

    @Test
    fun `매크로가 없는 파일은 빈 결과를 준다`() {
        val result = importMdr("""{"exportFormat":2}""")
        assertTrue(result.macros.isEmpty())
        assertTrue(result.skippedMacros.isEmpty())
    }
}
