package com.wemade.smartnoti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/**
 * 설치기가 돌려주는 결과를 받는다.
 * 확인 화면이 필요하다고 하면(첫 설치 때) 그 화면을 띄우고, 그 뒤로는 조용히 끝난다.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // 확인이 필요하다는 뜻. 앱이 화면에 있으면 그 화면이 뜨고, 배경이면 조용히 막힌다.
                // 어느 쪽이든 "받아 뒀음"으로 되돌려, 앱을 열었을 때 배너로 다시 이을 수 있게 한다
                Updater.state.value = UpdateState.Downloaded("")
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { RunLog.add("업데이트 · 설치 화면을 열지 못함") }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                RunLog.add("업데이트 설치 완료")
                Updater.pendingApk(context)?.delete()
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Updater.state.value = UpdateState.Failed("설치하지 못했습니다 · ${message ?: "알 수 없는 이유"}")
                RunLog.add("업데이트 설치 실패 · ${message ?: "알 수 없는 이유"}")
            }
        }
    }
}
