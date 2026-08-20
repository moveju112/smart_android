package com.wemade.smartnoti

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger

/**
 * 매크로 엔진. 알림 리스너가 시스템에 상시 물려 있으므로 블루투스·와이파이 감시도 여기에 얹는다.
 * 접근성 서비스는 쓰지 않는다 — 화면 터치·입력 자동화 기능은 이 앱에 없다.
 */
class MacroService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = mutableMapOf<Long, Job>()   // 실행 중인 매크로 (중복 실행 방지)
    private val connectedDevices = mutableSetOf<String>()   // 지금 붙어 있는 블루투스 기기 주소
    private var connectivity: ConnectivityManager? = null
    private var seeded = false   // 붙어 있는 기기를 한 번이라도 물어봤는지

    // 1. 블루투스 연결/해제 브로드캐스트 — 암시적 브로드캐스트 제한 때문에 런타임 등록만 가능
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(
                intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
            )
            val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            val address = device?.address
            if (address != null) {
                if (connected) connectedDevices += address else connectedDevices -= address
            }
            // 매크로가 걸리지 않아도 남긴다 — 기기 주소가 맞는지 확인하는 용도
            RunLog.add((if (connected) "블루투스 연결" else "블루투스 해제") + " · ${address ?: "알 수 없음"}")
            fireBluetooth(address, connected)
        }
    }

    /** 주소가 맞는 블루투스 매크로를 돌린다. 알림으로 알았든 다시 물어서 알았든 같은 길을 탄다 */
    private fun fireBluetooth(address: String?, connected: Boolean) {
        fire { trigger ->
            trigger is Trigger.Bluetooth &&
                trigger.connected == connected &&
                (trigger.address.isBlank() || trigger.address.equals(address, ignoreCase = true))
        }
    }

    /**
     * 지금 실제로 붙어 있는 기기를 시스템에 직접 물어본다.
     *
     * 연결/해제 알림만 믿으면 놓치는 자리가 있다. 앱이 정리된 사이에 일어난 일은 오지 않고,
     * 알림이 오더라도 이 앱이 그때 살아 있어야 받는다. 차에서 내린 그 한 번을 놓치면
     * 30분 뒤에 할 일도 함께 사라진다. 그래서 알람으로 주기마다 다시 물어 그물을 하나 더 둔다.
     *
     * 오디오(A2DP)와 통화(HEADSET) 두 갈래를 본다 — 차량은 둘 중 어느 쪽으로든 붙는다.
     */
    private fun askConnectedDevices(then: (Set<String>) -> Unit) {
        if (!hasBluetoothPermission()) {
            then(emptySet())
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            then(emptySet())
            return
        }
        val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        val found = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val left = AtomicInteger(profiles.size)

        // 프로필마다 프록시를 얻어 목록을 읽고 곧 닫는다. 마지막 것이 끝나면 한 번만 넘긴다
        fun done() { if (left.decrementAndGet() == 0) then(found.toSet()) }

        profiles.forEach { profile ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(which: Int, proxy: BluetoothProfile) {
                    runCatching { proxy.connectedDevices.forEach { found += it.address } }
                    runCatching { adapter.closeProfileProxy(which, proxy) }
                    done()
                }

                override fun onServiceDisconnected(which: Int) = done()
            }
            val asked = runCatching { adapter.getProfileProxy(this, listener, profile) }.getOrDefault(false)
            if (!asked) done()
        }
    }

    /**
     * 물어본 결과를 기억하던 것과 견주어, 달라진 만큼 매크로를 돌린다.
     * 엔진이 막 떴을 때는 견줄 과거가 없으므로 채우기만 하고 아무것도 돌리지 않는다.
     */
    fun recheckBluetooth() {
        askConnectedDevices { now ->
            if (!seeded) {
                seeded = true
                connectedDevices.clear()
                connectedDevices += now
                RunLog.add(
                    if (now.isEmpty()) "블루투스 · 지금 붙어 있는 기기 없음"
                    else "블루투스 · 지금 붙어 있는 기기 " + now.joinToString(", ")
                )
                return@askConnectedDevices
            }
            val gone = connectedDevices - now
            val fresh = now - connectedDevices
            connectedDevices.clear()
            connectedDevices += now
            gone.forEach { address ->
                RunLog.add("블루투스 해제 확인 · $address")
                fireBluetooth(address, connected = false)
            }
            fresh.forEach { address ->
                RunLog.add("블루투스 연결 확인 · $address")
                fireBluetooth(address, connected = true)
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

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
        RunLog.attach(this)
        MacroStore.load(this)
        MacroHistory.load(this)
        PendingWaits.load(this)
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

        // 무엇을 감시하고 있는지 남긴다. 안 도는 이유를 나중에 짚으려면 이 줄이 있어야 한다
        val watching = buildString {
            append("엔진 시작 · 매크로 ").append(MacroStore.macros.value.size).append("개 · 블루투스 ")
            append(if (hasBluetoothPermission()) "감시 중" else "권한 없음(근처 기기)")
            append(" · 와이파이 ").append(if (wifiUp) "붙어 있음" else "끊김")
        }
        RunLog.add(watching)

        // 붙어 있는 기기를 물어 채우고, 주기 확인을 걸어 둔다
        recheckBluetooth()
        Alarms.scheduleWatch(this)

        // 알람을 놓친 사이 앱이 죽었을 수 있다. 시각이 지난 대기는 지금 이어서 한다
        val late = PendingWaits.overdue(this, System.currentTimeMillis())
        late.forEach { (id, at) ->
            val macro = MacroStore.find(id)
            if (macro == null) {
                PendingWaits.take(this, id)
            } else {
                PendingWaits.take(this, id)
                RunLog.add("밀린 대기를 이어서 함 · ${macro.name}")
                resume(macro, at)
            }
        }

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
        val texts = sbn.allTexts()
        if (Diagnostics.peekNotifications.value) {
            RunLog.add("알림 들어옴 · ${sbn.packageName} · " + texts.joinToString(" / ") { "\"$it\"" })
        }
        fire { trigger ->
            trigger is Trigger.Notification &&
                (trigger.packageName.isBlank() || trigger.packageName == sbn.packageName) &&
                matchesText(trigger.text, texts)
        }
    }

    /**
     * 조건에 맞는 매크로를 모두 실행한다. 이미 돌고 있는 매크로는 건너뛴다.
     * 트리거가 여럿이면 그중 하나만 걸려도 돈다.
     */
    private fun fire(matches: (Trigger) -> Boolean) {
        MacroStore.macros.value
            .filter { macro -> macro.enabled && macro.allTriggers().any(matches) }
            .forEach { macro -> launchMacro(macro, force = false) }
    }

    /**
     * 조건 하나를 지금 상태에 견준다.
     * 위치는 새로 잡지 않고 다른 앱이 받아 둔 마지막 값을 읽는다 — 배터리를 쓰지 않으려는 것이다.
     */
    private fun evaluate(condition: Condition): Boolean = when (condition) {
        is Condition.Bluetooth -> {
            val hit = if (condition.address.isBlank()) connectedDevices.isNotEmpty()
            else connectedDevices.any { it.equals(condition.address, ignoreCase = true) }
            hit == condition.connected
        }

        is Condition.Wifi -> wifiUp == condition.connected

        is Condition.TimeRange -> {
            val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            val from = condition.fromMinute
            val to = condition.toMinute
            // from이 to보다 크면 자정을 넘기는 구간이다
            val inside = if (from <= to) now in from..to else now >= from || now <= to
            inside == condition.inside
        }

        is Condition.Battery -> {
            val manager = getSystemService(android.os.BatteryManager::class.java)
            val level = manager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val charging = manager?.isCharging ?: false
            level in condition.atLeast..condition.atMost &&
                (condition.charging == null || condition.charging == charging)
        }

        is Condition.Place -> {
            val here = lastKnownPlace()
            if (here == null) {
                RunLog.add("위치를 알 수 없어 조건을 넘기지 못함 · 위치 권한을 확인하세요")
                false
            } else {
                val away = FloatArray(1)
                android.location.Location.distanceBetween(
                    here.first, here.second, condition.latitude, condition.longitude, away
                )
                (away[0] <= condition.radiusMeters) == condition.inside
            }
        }
    }

    /** 마지막으로 알려진 위치. 없거나 권한이 없으면 null */
    private fun lastKnownPlace(): Pair<Double, Double>? {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return null

        val manager = getSystemService(android.location.LocationManager::class.java) ?: return null
        return runCatching {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

    /**
     * 지금 화면에 떠 있는 알림을 (앱, 패키지, 제목, 본문, 지울 수 있는지)로 넘긴다.
     * 문구를 손으로 적어 맞추는 대신 눈으로 보고 고르게 하려는 것이다
     */
    fun snapshot(): List<NotificationPeek> {
        val active = runCatching { activeNotifications }.getOrNull() ?: return emptyList()
        return active.map { sbn ->
            val extras = sbn.notification.extras
            NotificationPeek(
                packageName = sbn.packageName,
                // 패키지명은 정확하지만 읽히지 않는다. 화면에는 사람이 부르는 이름을 쓴다
                appLabel = runCatching {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(sbn.packageName, 0)
                    ).toString()
                }.getOrDefault(""),
                title = extras.getCharSequence("android.title")?.toString().orEmpty(),
                text = sbn.allTexts().drop(1).joinToString(" / "),
                clearable = sbn.isClearable
            )
        }
    }

    /**
     * 지금 바로 실행. 트리거를 기다리지 않고, 대기와 조건도 건너뛴다.
     * 매크로를 손보고 결과를 곧바로 확인하려는 용도다
     */
    fun runNow(macro: Macro): Job? = launchMacro(macro, force = true)

    /**
     * 알람이 깨웠을 때, 적어 둔 단계부터 이어서 한다.
     *
     * 30분이 지났다. "아직 붙어 있으면 중단" 같은 조건을 낡은 기억으로 견주면 안 되므로,
     * 이어가기 전에 지금 붙어 있는 것이 무엇인지 먼저 다시 묻는다.
     */
    fun resume(macro: Macro, fromIndex: Int) {
        scope.launch {
            syncConnectedQuietly()
            launchMacro(macro, force = false, fromIndex = fromIndex)
        }
    }

    /** 붙어 있는 기기 목록만 조용히 맞춘다. 매크로는 돌리지 않는다 */
    private suspend fun syncConnectedQuietly() {
        val now = suspendCancellableCoroutine<Set<String>> { slot ->
            askConnectedDevices { found -> if (slot.isActive) slot.resume(found) {} }
        }
        connectedDevices.clear()
        connectedDevices += now
        seeded = true
    }

    // 브로드캐스트가 막히면 남은 단계는 계속 돌아야 하지만 결과는 실패로 남아야 한다
    @Volatile
    private var lastActionFailed = false

    private fun launchMacro(macro: Macro, force: Boolean, fromIndex: Int = 0): Job? {
        if (running[macro.id]?.isActive == true) return null
        val job = scope.launch {
            EngineState.markRunning(macro.id, true)
            Log.i(TAG, "실행: ${macro.name}")
            RunLog.add(
                when {
                    force -> "▶ ${macro.name} · 지금 실행"
                    fromIndex > 0 -> "▶ ${macro.name} · 대기 뒤 이어서"
                    else -> "▶ ${macro.name}"
                }
            )
            // 이번 실행이 무엇으로 끝났는지. 목록이 이 값을 한 줄로 보여 준다
            var outcome = MacroHistory.Outcome.Ran
            var handedOff = false
            try {
                var at = fromIndex
                while (at < macro.actions.size) {
                    val action = macro.actions[at]

                    // 긴 대기는 알람에 맡기고 이 실행은 여기서 끝낸다.
                    // 프로세스가 정리돼도 시각이 되면 시스템이 깨워 남은 단계를 이어간다
                    if (!force && action is Action.Delay && action.seconds >= Alarms.HANDOFF_SECONDS) {
                        handOff(macro, at + 1, action.seconds)
                        handedOff = true
                        break
                    }

                    if (!runAction(action, force)) {
                        Log.i(TAG, "중단: ${macro.name}")
                        RunLog.add("■ 조건이 맞지 않아 멈춤 · ${macro.name}")
                        outcome = MacroHistory.Outcome.Stopped
                        break
                    }
                    if (lastActionFailed) outcome = MacroHistory.Outcome.Failed
                    at++
                }
            } finally {
                EngineState.markRunning(macro.id, false)
                // 알람에 넘긴 것은 아직 끝난 것이 아니다. 그때는 대기 표시가 이 자리를 대신한다
                if (!handedOff) MacroHistory.record(this@MacroService, macro.id, outcome)
            }
        }
        running[macro.id] = job
        return job
    }

    /** 남은 단계를 적어 두고 알람을 건다 */
    private fun handOff(macro: Macro, nextIndex: Int, seconds: Int) {
        val dueAt = System.currentTimeMillis() + seconds * 1000L
        PendingWaits.put(this, macro.id, nextIndex, dueAt)
        Alarms.scheduleResume(this, macro.id, dueAt)
        RunLog.add("${humanSeconds(seconds)} 뒤에 이어서 함 · ${macro.name}")
    }

    /** 액션 하나 실행. false를 주면 남은 액션을 실행하지 않는다 */
    private suspend fun runAction(action: Action, force: Boolean): Boolean {
        lastActionFailed = false
        when (action) {
            // ponytail: 긴 대기는 도즈 모드에서 늘어질 수 있다. 분 단위 정확도가 필요해지면 AlarmManager로 교체
            is Action.Delay -> if (!force) delay(action.seconds * 1000L)

            // 옛 매크로에만 남아 있다. "그 상태면 멈춤"이므로 뒤집어 넘긴다
            is Action.StopIfBluetooth -> {
                if (force) return true
                val ok = evaluate(
                    Condition.Bluetooth(action.address, action.deviceName, !action.connected)
                )
                if (!ok) return false
            }

            is Action.StopUnless -> {
                if (force) return true
                if (!evaluate(action.condition)) return false
            }

            is Action.ClearNotification -> {
                val active = runCatching { activeNotifications }.getOrNull() ?: return true

                // 1. 그 앱 알림을 먼저 추려 둔다. 못 지웠을 때 무엇이 있었는지 알려주기 위함이다
                val fromApp = active.filter {
                    action.packageName.isBlank() || action.packageName == it.packageName
                }
                // 2. 문구까지 맞는 것만 고른다
                // 진행 중이라 손으로는 못 지우는 알림도 지운다. 그러라고 있는 매크로다
                val hits = fromApp.filter { sbn -> matchesText(action.text, sbn.allTexts()) }
                hits.forEach { cancelNotification(it.key) }

                when {
                    hits.isNotEmpty() -> RunLog.add("알림 ${hits.size}개 삭제")
                    fromApp.isEmpty() -> RunLog.add("지울 알림 없음 · 그 앱 알림이 하나도 없음")
                    else -> {
                        // 문구가 안 맞거나 지울 수 없는 알림이다. 실제 문구를 그대로 보여준다
                        val sample = fromApp.first()
                        val shown = sample.allTexts().joinToString(" / ") { "\"$it\"" }
                        RunLog.add("지울 알림 없음 · 그 앱 알림은 $shown")
                    }
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

                // 무엇을 보내는지 그대로 남긴다. 터널 이름 한 글자가 달라도 아무 일이 일어나지 않는다
                val what = buildString {
                    append(action.action)
                    if (action.extraName.isNotBlank()) {
                        append(" · ").append(action.extraName).append("=").append(action.extraValue)
                    }
                }

                // 보내기 전에 막힐 자리를 먼저 본다. 보내고 나면 실패해도 알 길이 없다
                val blocked = broadcastBlockedReason(action, intent)
                if (blocked != null) {
                    RunLog.add("브로드캐스트 보내지 못함 · $blocked")
                    Log.w(TAG, "브로드캐스트 막힘: $blocked")
                    lastActionFailed = true
                    return true
                }

                runCatching { sendBroadcast(intent) }
                    .onSuccess { RunLog.add("브로드캐스트 전송 · $what → ${action.packageName}") }
                    .onFailure {
                        Log.w(TAG, "브로드캐스트 실패: ${it.message}")
                        RunLog.add("브로드캐스트 실패 · ${it.message}")
                        lastActionFailed = true
                    }
            }
        }
        return true
    }

    /**
     * 보내도 아무 일이 없을 자리를 미리 짚는다.
     *
     * 브로드캐스트는 받는 쪽 사정으로 조용히 버려진다 — 리시버 이름이 틀렸거나, 그 쪽이 걸어 둔
     * 권한이 없거나. 어느 쪽이든 예외가 나지 않아서 "보냈다"는 기록만 남고 끝난다.
     */
    private fun broadcastBlockedReason(action: Action.Broadcast, intent: Intent): String? {
        // 1. 받을 리시버가 실제로 있는지
        val targets = runCatching { packageManager.queryBroadcastReceivers(intent, 0) }.getOrDefault(emptyList())
        if (targets.isEmpty()) {
            return if (action.className.isNotBlank())
                "받을 곳이 없습니다 · ${action.packageName}/${action.className} 이 맞는지 확인하세요"
            else
                "받을 곳이 없습니다 · ${action.packageName} 이 깔려 있는지 확인하세요"
        }

        // 2. 그 리시버가 요구하는 권한을 우리가 받았는지
        val needed = targets.firstNotNullOfOrNull { it.activityInfo?.permission }
        if (needed != null && checkSelfPermission(needed) != PackageManager.PERMISSION_GRANTED) {
            return "권한이 없습니다 · $needed · 앱을 열면 물어봅니다. 이미 거부했다면 안드로이드 설정 → 앱 → 권한에서 켜세요"
        }
        return null
    }

    companion object {
        private const val TAG = "SmartNoti"

        // 화면에서 "지금 실행"을 눌렀을 때 붙잡을 손잡이. 서비스가 죽으면 null이 된다
        @Volatile
        var instance: MacroService? = null
            private set
    }
}

/**
 * 알림에서 글자가 들어갈 수 있는 칸을 모두 모은다.
 *
 * 제목·본문만 보다가 놓친 일이 있었다. 앱은 부제목, 요약, 여러 줄 목록 같은 곳에도
 * 글자를 나눠 담는다. 조건을 맞출 때는 이 전부를 봐야 한다.
 */
private fun StatusBarNotification.allTexts(): List<String> {
    val e = notification.extras
    val singles = listOf(
        "android.title", "android.title.big", "android.text", "android.bigText",
        "android.subText", "android.summaryText", "android.infoText", "android.conversationTitle"
    ).mapNotNull { e.getCharSequence(it)?.toString() }
    val lines = e.getCharSequenceArray("android.textLines")?.map { it.toString() }.orEmpty()
    val ticker = notification.tickerText?.toString()
    return (singles + lines + listOfNotNull(ticker))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}
