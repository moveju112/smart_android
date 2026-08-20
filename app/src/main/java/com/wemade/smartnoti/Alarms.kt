package com.wemade.smartnoti

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 시스템이 깨워 주기를 부탁하는 자리.
 *
 * 이 앱은 상주 서비스를 두지 않고 알림 리스너에 얹혀 있다. 편한 대신 프로세스가 정리되면
 * 메모리에 있던 것이 모두 사라진다. 오래 걸리는 일은 그래서 알람에 맡긴다.
 */
object Alarms {

    const val ACTION_RESUME = "com.wemade.smartnoti.RESUME_WAIT"
    const val ACTION_WATCH = "com.wemade.smartnoti.WATCH_BLUETOOTH"
    const val EXTRA_MACRO_ID = "macroId"

    /** 이 길이를 넘는 대기는 알람에 넘긴다. 5분·30분처럼 앱이 살아 있으리라 믿기 어려운 길이다 */
    const val HANDOFF_SECONDS = 120

    /** 블루투스 상태를 다시 보는 간격. 도즈 모드에서 알람이 9분보다 촘촘히 오지 않으므로 그에 맞춘다 */
    const val WATCH_MINUTES = 10L

    /** 대기가 끝날 때 깨워 달라고 걸어 둔다 */
    fun scheduleResume(context: Context, macroId: Long, dueAt: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_RESUME)
            .putExtra(EXTRA_MACRO_ID, macroId)
        set(context, dueAt, pending(context, macroId.toInt(), intent))
    }

    /** 다음 블루투스 확인을 걸어 둔다. 한 번 쓰고 사라지는 알람이라 확인할 때마다 다시 건다 */
    fun scheduleWatch(context: Context) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_WATCH)
        val at = System.currentTimeMillis() + WATCH_MINUTES * 60_000L
        set(context, at, pending(context, WATCH_CODE, intent))
    }

    /**
     * 정확한 알람(setExact)은 안드로이드 12부터 따로 허락을 받아야 한다.
     * 몇 분 늦어도 상관없는 일이라 허락이 필요 없는 쪽을 쓴다.
     */
    private fun set(context: Context, at: Long, pi: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    private fun pending(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private const val WATCH_CODE = -1
}

/** 알람이 도착하는 곳. 엔진이 살아 있으면 넘기고, 없으면 적어 둔 채로 다음 시작을 기다린다 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        RunLog.attach(context)
        when (intent.action) {
            Alarms.ACTION_RESUME -> resume(context, intent)
            Alarms.ACTION_WATCH -> watch(context)
        }
    }

    // 1. 대기가 끝났다 — 남은 단계를 엔진에 넘긴다
    private fun resume(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Alarms.EXTRA_MACRO_ID, -1L)
        if (id < 0) return
        MacroStore.load(context)
        val service = MacroService.instance
        if (service == null) {
            // 엔진이 아직 안 붙었다. 적어 둔 것은 그대로 두고, 엔진이 뜨면서 챙긴다
            RunLog.add("대기가 끝났지만 엔진이 아직 꺼져 있음 · 켜지면 이어서 합니다")
            return
        }
        val at = PendingWaits.take(context, id) ?: return
        val macro = MacroStore.find(id) ?: return
        service.resume(macro, at)
    }

    // 2. 블루투스 상태를 다시 본다 — 연결/해제 알림을 놓쳤을 때를 위한 그물이다
    private fun watch(context: Context) {
        MacroService.instance?.recheckBluetooth()
        Alarms.scheduleWatch(context)
    }
}
