package com.wemade.smartnoti

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 매크로 엔진. 알림 리스너가 시스템에 상시 물려 있으므로 블루투스·와이파이 감시도 여기에 얹는다.
 * 접근성 서비스는 쓰지 않는다 — 화면 터치·입력 자동화 기능은 이 앱에 없다.
 */
class MacroService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = mutableMapOf<Long, Job>()   // 실행 중인 매크로 (중복 실행 방지)
    private val connectedDevices = mutableSetOf<String>()   // 지금 붙어 있는 블루투스 기기 주소
    private var connectivity: ConnectivityManager? = null

    // 1. 블루투스 연결/해제 브로드캐스트 — 암시적 브로드캐스트 제한 때문에 런타임 등록만 가능
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(
                intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
            )
            val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            // ponytail: 서비스가 뜬 뒤의 연결/해제만 추적한다. 그 전부터 붙어 있던 기기는 모른다
            device?.address?.let { if (connected) connectedDevices += it else connectedDevices -= it }
            // 매크로가 걸리지 않아도 남긴다 — 기기 주소가 맞는지 확인하는 용도
            RunLog.add((if (connected) "블루투스 연결" else "블루투스 해제") + " · ${device?.address ?: "알 수 없음"}")
            fire { trigger ->
                trigger is Trigger.Bluetooth &&
                    trigger.connected == connected &&
                    (trigger.address.isBlank() || trigger.address.equals(device?.address, ignoreCase = true))
            }
        }
    }

    // 2. 와이파이 연결/해제.
    // 콜백을 걸면 이미 붙어 있는 와이파이에도 onAvailable이 한 번 오므로,
    // 시작 시점의 상태를 미리 넣어두고 상태가 실제로 바뀔 때만 매크로를 돌린다
    private var wifiUp = false
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (wifiUp) return
            wifiUp = true
            fire { it is Trigger.Wifi && it.connected }
        }

        override fun onLost(network: Network) {
            if (!wifiUp) return
            wifiUp = false
            fire { it is Trigger.Wifi && !it.connected }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        MacroStore.load(this)
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, bluetoothReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        connectivity = getSystemService(ConnectivityManager::class.java)?.also { cm ->
            wifiUp = cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, wifiCallback)
        }
        instance = this
        EngineState.connected.value = true
        Log.i(TAG, "엔진 시작 — 매크로 ${MacroStore.macros.value.size}개")
        RunLog.add("엔진 시작")
        // 하루에 한 번, 새 버전이 올라왔으면 알아서 받아 깔아둔다
        scope.launch { runCatching { Updater.checkAutomatically(this@MacroService) } }
    }

    override fun onListenerDisconnected() {
        EngineState.connected.value = false
        runCatching { unregisterReceiver(bluetoothReceiver) }
        runCatching { connectivity?.unregisterNetworkCallback(wifiCallback) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        EngineState.connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    // 3. 알림이 뜰 때 — 알림 트리거 매칭
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getCharSequence("android.title")?.toString()
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString()
        val bigText = sbn.notification.extras.getCharSequence("android.bigText")?.toString()
        fire { trigger ->
            trigger is Trigger.Notification &&
                (trigger.packageName.isBlank() || trigger.packageName == sbn.packageName) &&
                matchesText(trigger.text, title, text, bigText)
        }
    }

    /** 조건에 맞는 매크로를 모두 실행한다. 이미 돌고 있는 매크로는 건너뛴다 */
    private fun fire(matches: (Trigger) -> Boolean) {
        MacroStore.macros.value
            .filter { it.enabled && matches(it.trigger) }
            .forEach { macro -> launchMacro(macro, force = false) }
    }

    /**
     * 지금 바로 실행. 트리거를 기다리지 않고, 대기와 조건도 건너뛴다.
     * 매크로를 손보고 결과를 곧바로 확인하려는 용도다
     */
    fun runNow(macro: Macro) = launchMacro(macro, force = true)

    private fun launchMacro(macro: Macro, force: Boolean) {
        if (running[macro.id]?.isActive == true) return
        running[macro.id] = scope.launch {
            EngineState.markRunning(macro.id, true)
            Log.i(TAG, "실행: ${macro.name}")
            RunLog.add(if (force) "▶ ${macro.name} · 강제 실행" else "▶ ${macro.name}")
            try {
                for (action in macro.actions) {
                    if (!runAction(action, force)) {
                        Log.i(TAG, "중단: ${macro.name}")
                        RunLog.add("■ 조건이 맞지 않아 멈춤 · ${macro.name}")
                        break
                    }
                }
            } finally {
                EngineState.markRunning(macro.id, false)
            }
        }
    }

    /** 액션 하나 실행. false를 주면 남은 액션을 실행하지 않는다 */
    private suspend fun runAction(action: Action, force: Boolean): Boolean {
        when (action) {
            // ponytail: 긴 대기는 도즈 모드에서 늘어질 수 있다. 분 단위 정확도가 필요해지면 AlarmManager로 교체
            is Action.Delay -> if (!force) delay(action.seconds * 1000L)

            is Action.StopIfBluetooth -> {
                if (force) return true
                val hit = if (action.address.isBlank()) connectedDevices.isNotEmpty()
                else connectedDevices.any { it.equals(action.address, ignoreCase = true) }
                if (hit == action.connected) return false
            }

            is Action.ClearNotification -> {
                val active = runCatching { activeNotifications }.getOrNull() ?: return true
                active.filter { sbn ->
                    // 상시 알림(진행 중·지울 수 없음)은 건드리지 않는다 — MacroDroid 기본값과 같다
                    sbn.isClearable &&
                        (action.packageName.isBlank() || action.packageName == sbn.packageName) &&
                        matchesText(
                            action.text,
                            sbn.notification.extras.getCharSequence("android.title")?.toString(),
                            sbn.notification.extras.getCharSequence("android.text")?.toString(),
                            sbn.notification.extras.getCharSequence("android.bigText")?.toString()
                        )
                }.also { hits ->
                    hits.forEach { cancelNotification(it.key) }
                    RunLog.add("알림 ${hits.size}개 삭제")
                }
            }

            is Action.Broadcast -> {
                val intent = Intent(action.action.ifBlank { null })
                if (action.packageName.isNotBlank() && action.className.isNotBlank()) {
                    intent.setClassName(action.packageName, action.className)
                } else if (action.packageName.isNotBlank()) {
                    intent.setPackage(action.packageName)
                }
                if (action.extraName.isNotBlank()) intent.putExtra(action.extraName, action.extraValue)
                runCatching { sendBroadcast(intent) }
                    .onSuccess { RunLog.add("브로드캐스트 전송 · ${action.action} → ${action.packageName}") }
                    .onFailure {
                        Log.w(TAG, "브로드캐스트 실패: ${it.message}")
                        RunLog.add("브로드캐스트 실패 · ${it.message}")
                    }
            }
        }
        return true
    }

    companion object {
        private const val TAG = "SmartNoti"

        // 화면에서 "지금 실행"을 눌렀을 때 붙잡을 손잡이. 서비스가 죽으면 null이 된다
        @Volatile
        var instance: MacroService? = null
            private set
    }
}
