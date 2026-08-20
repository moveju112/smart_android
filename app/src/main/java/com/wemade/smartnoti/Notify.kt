package com.wemade.smartnoti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 매크로가 조용히 실패했을 때 사람에게 가는 유일한 신호.
 *
 * 이 앱의 약속은 「네가 안 보는 동안 일어났다」다. 그런데 약속이 깨진 사실은 지금까지
 * 실행 기록에만 적혔다 — 앱을 열 이유는 이미 뭔가 이상하다고 느낀 뒤에야 생기고,
 * 실패는 대개 운전 중에 일어난다. 터널이 안 붙은 것을 다른 앱에서 먼저 알게 되는 것은
 * 이 제품의 실패다.
 *
 * 조용한 알림 하나만 띄운다. 소리도 진동도 없다 — 실패를 알리는 일이 그 자체로
 * 성가신 알림이 되면 이 앱이 하려던 일과 반대가 된다.
 */
object Notify {

    private const val CHANNEL = "macro_failed"
    private const val GROUP = "com.wemade.smartnoti.FAILED"

    /** 매크로 하나가 실패했다고 알린다. 같은 매크로는 같은 자리를 덮어쓴다 */
    fun failed(context: Context, macro: Macro, reason: String?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        // 눌렀을 때 그 매크로를 볼 수 있게 앱을 연다
        val open = PendingIntent.getActivity(
            context,
            macro.id.toInt(),
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = reason?.takeIf { it.isNotBlank() } ?: "실행 기록에서 무엇이 막혔는지 볼 수 있습니다"
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_failed)
            .setContentTitle("${macro.name} · 실패")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP)
            .build()

        // id를 매크로마다 다르게 둬서 같은 매크로가 여러 줄로 쌓이지 않게 한다
        runCatching { manager.notify(macro.id.toInt(), notification) }
    }

    /** 그 매크로의 실패 알림을 거둔다. 다시 제대로 돌았으면 남겨 둘 이유가 없다 */
    fun clearFailed(context: Context, macro: Macro) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.cancel(macro.id.toInt()) }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL,
            "매크로 실패",
            // 조용히. 소리나 진동으로 알리면 이 앱이 없애려던 것과 같은 것이 된다
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "켜 둔 매크로가 돌지 못했을 때 알립니다"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
