package com.wemade.smartnoti

import org.junit.Assert.assertEquals
import org.junit.Test

/** 실행 기록에서 엔진 상태 줄만 줄고 결과 줄은 남는지 */
class SquelchTest {

    /** 엔진 재바인딩 두 줄이 번갈아 찍혀도 하루에 한 벌만 남는다 */
    @Test
    fun `번갈아 나오는 엔진 상태 줄을 하루 한 벌로 줄인다`() {
        val lines = listOf(
            "08-21 17:02:18  블루투스 · 지금 붙어 있는 기기 없음",
            "08-21 17:02:18  엔진 시작 · 매크로 13개 · 블루투스 감시 중",
            "08-21 17:00:06  블루투스 · 지금 붙어 있는 기기 없음",
            "08-21 17:00:06  엔진 시작 · 매크로 13개 · 블루투스 감시 중",
            "08-21 16:58:32  블루투스 · 지금 붙어 있는 기기 없음",
            "08-21 16:58:32  엔진 시작 · 매크로 13개 · 블루투스 감시 중"
        )
        assertEquals(lines.take(2), squelch(lines))
    }

    /** 어제 엔진이 언제 떴는지는 따로 알아야 한다 */
    @Test
    fun `날이 바뀌면 같은 상태 줄이 다시 남는다`() {
        val lines = listOf(
            "08-21 08:02:09  엔진 시작 · 매크로 13개",
            "08-20 21:30:12  엔진 시작 · 매크로 13개"
        )
        assertEquals(lines, squelch(lines))
    }

    /** 같은 결과가 두 번 나온 것은 두 번 일어난 것이다 */
    @Test
    fun `결과 줄은 되풀이돼도 남는다`() {
        val lines = listOf(
            "08-21 12:00:02  브로드캐스트 보내지 못함 · 권한 없어 거부됨",
            "08-21 12:00:01  ▶ 차 블루투스 연결",
            "08-21 11:00:02  브로드캐스트 보내지 못함 · 권한 없어 거부됨",
            "08-21 11:00:01  ▶ 차 블루투스 연결",
            "08-20 21:35:06  WireGuard 터널 켜기 · home-server",
            "08-21 11:20:45  WireGuard 터널 켜기 · home-server"
        )
        assertEquals(lines, squelch(lines))
    }
}
