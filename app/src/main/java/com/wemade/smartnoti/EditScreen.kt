package com.wemade.smartnoti

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(macro: Macro, onSave: (Macro) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf(macro) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 매크로를 지울까요?") },
            text = { Text("\"${draft.name}\"이 사라집니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("지우기", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("그대로 두기") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("매크로 편집") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    TextButton(onClick = { onSave(draft) }) { Text("저장") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { SectionHeader("언제", "이 일이 생기면 매크로가 돕니다") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TriggerEditor(draft.trigger) { draft = draft.copy(trigger = it) }
                    }
                }
            }

            item { SectionHeader("무엇을", "위에서 아래로 차례대로 실행합니다") }
            items(draft.actions.size, key = { it }) { index ->
                ActionCard(
                    index = index,
                    total = draft.actions.size,
                    action = draft.actions[index],
                    onChange = { changed ->
                        draft = draft.copy(actions = draft.actions.mapIndexed { i, a -> if (i == index) changed else a })
                    },
                    onRemove = {
                        draft = draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index })
                    },
                    onMove = { delta ->
                        val to = index + delta
                        if (to in draft.actions.indices) {
                            val list = draft.actions.toMutableList()
                            list.add(to, list.removeAt(index))
                            draft = draft.copy(actions = list)
                        }
                    }
                )
            }

            item {
                fun add(action: Action) { draft = draft.copy(actions = draft.actions + action) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { add(Action.Delay()) }, modifier = Modifier.weight(1f)) { Text("대기") }
                        OutlinedButton(onClick = { add(Action.ClearNotification()) }, modifier = Modifier.weight(1f)) { Text("알림 삭제") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { add(Action.Broadcast()) }, modifier = Modifier.weight(1f)) { Text("브로드캐스트") }
                        OutlinedButton(onClick = { add(Action.StopIfBluetooth()) }, modifier = Modifier.weight(1f)) { Text("조건부 중단") }
                    }
                }
            }

            item {
                Spacer(Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) { Text("저장") }
                    OutlinedButton(onClick = { confirmDelete = true }) {
                        Text("지우기", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
    Column(Modifier.padding(top = 10.dp, start = 4.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 액션 한 칸. 순서가 곧 실행 순서라 위아래로 옮길 수 있게 둔다 */
@Composable
private fun ActionCard(
    index: Int,
    total: Int,
    action: Action,
    onChange: (Action) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge(action.step())
                Spacer(Modifier.width(8.dp))
                Text(
                    "${index + 1} / $total",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "위로", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onMove(1) }, enabled = index < total - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "아래로", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "액션 빼기",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Column(
                Modifier.padding(end = 8.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionEditor(action, onChange)
            }
        }
    }
}

/** 액션의 성격을 한눈에 — 시간은 앰버, 조건은 테두리만, 나머지는 채운 사각 */
@Composable
private fun StepBadge(step: Step) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when (step) {
        Step.Wait -> "대기" to scheme.secondary
        Step.Gate -> "조건" to scheme.primary
        else -> "실행" to scheme.onSurfaceVariant
    }
    Text(label, style = MonoSmall.copy(fontFamily = FontFamily.Monospace), color = color)
}

@Composable
private fun TriggerEditor(trigger: Trigger, onChange: (Trigger) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = trigger is Trigger.Notification,
            onClick = { if (trigger !is Trigger.Notification) onChange(Trigger.Notification()) },
            label = { Text("알림") }
        )
        FilterChip(
            selected = trigger is Trigger.Bluetooth,
            onClick = { if (trigger !is Trigger.Bluetooth) onChange(Trigger.Bluetooth()) },
            label = { Text("블루투스") }
        )
        FilterChip(
            selected = trigger is Trigger.Wifi,
            onClick = { if (trigger !is Trigger.Wifi) onChange(Trigger.Wifi()) },
            label = { Text("와이파이") }
        )
    }

    when (trigger) {
        is Trigger.Notification -> {
            AppPicker(trigger.packageName, trigger.appLabel) { pkg, label ->
                onChange(trigger.copy(packageName = pkg, appLabel = label))
            }
            OutlinedTextField(
                value = trigger.text,
                onValueChange = { onChange(trigger.copy(text = it)) },
                label = { Text("알림에 이 말이 들어 있을 때") },
                supportingText = { Text("비우면 그 앱의 알림 전부") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        is Trigger.Bluetooth -> {
            DevicePicker(trigger.address, trigger.deviceName) { addr, name ->
                onChange(trigger.copy(address = addr, deviceName = name))
            }
            StateSwitch(
                onLabel = "기기가 연결될 때",
                offLabel = "기기가 끊길 때",
                value = trigger.connected
            ) { onChange(trigger.copy(connected = it)) }
        }
        is Trigger.Wifi -> StateSwitch(
            onLabel = "와이파이가 연결될 때",
            offLabel = "와이파이가 끊길 때",
            value = trigger.connected
        ) { onChange(trigger.copy(connected = it)) }
    }
}

@Composable
private fun StateSwitch(onLabel: String, offLabel: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (value) onLabel else offLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionEditor(action: Action, onChange: (Action) -> Unit) {
    when (action) {
        is Action.Delay -> OutlinedTextField(
            value = action.seconds.toString(),
            onValueChange = { onChange(action.copy(seconds = it.filter { c -> c.isDigit() }.take(6).toIntOrNull() ?: 0)) },
            label = { Text("몇 초 기다릴지") },
            supportingText = { Text(humanSeconds(action.seconds)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        is Action.ClearNotification -> {
            AppPicker(action.packageName, action.appLabel) { pkg, label ->
                onChange(action.copy(packageName = pkg, appLabel = label))
            }
            OutlinedTextField(
                value = action.text,
                onValueChange = { onChange(action.copy(text = it)) },
                label = { Text("이 말이 들어 있는 알림을 지움") },
                supportingText = { Text("비우면 그 앱의 알림 전부") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is Action.StopIfBluetooth -> {
            DevicePicker(action.address, action.deviceName) { addr, name ->
                onChange(action.copy(address = addr, deviceName = name))
            }
            StateSwitch(
                onLabel = "이 기기가 연결돼 있으면 여기서 멈춤",
                offLabel = "이 기기가 끊겨 있으면 여기서 멈춤",
                value = action.connected
            ) { onChange(action.copy(connected = it)) }
        }

        is Action.Broadcast -> {
            OutlinedTextField(
                value = action.action,
                onValueChange = { onChange(action.copy(action = it)) },
                label = { Text("액션") },
                supportingText = { Text("AdGuard는 start 또는 stop") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = action.packageName,
                onValueChange = { onChange(action.copy(packageName = it)) },
                label = { Text("받을 앱 패키지명") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = action.className,
                onValueChange = { onChange(action.copy(className = it)) },
                label = { Text("리시버 클래스") },
                supportingText = { Text("비우면 패키지 전체로 보냄") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = action.extraName,
                    onValueChange = { onChange(action.copy(extraName = it)) },
                    label = { Text("추가값 이름") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = action.extraValue,
                    onValueChange = { onChange(action.copy(extraValue = it)) },
                    label = { Text("추가값") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 1800초가 몇 분인지 사람이 세지 않게 한다 */
private fun humanSeconds(seconds: Int): String = when {
    seconds <= 0 -> "기다리지 않음"
    seconds < 60 -> "${seconds}초"
    seconds % 3600 == 0 -> "${seconds / 3600}시간"
    seconds % 60 == 0 -> "${seconds / 60}분"
    else -> "${seconds / 60}분 ${seconds % 60}초"
}

@Composable
private fun AppPicker(packageName: String, appLabel: String, onPick: (String, String) -> Unit) {
    val context = LocalContextCompat()
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(appLabel.ifBlank { if (packageName.isBlank()) "앱 고르기 — 지금은 모든 앱" else packageName })
            if (packageName.isNotBlank()) {
                Text(packageName, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (open) {
        val apps = remember { installedApps(context) }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("앱 고르기") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("이름이나 패키지로 찾기") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val shown = apps.filter {
                        query.isBlank() || it.first.contains(query, true) || it.second.contains(query, true)
                    }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(shown) { (label, pkg) ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { onPick(pkg, label); open = false }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(pkg, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onPick("", ""); open = false }) { Text("모든 앱으로") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("닫기") } }
        )
    }
}

@Composable
private fun DevicePicker(address: String, deviceName: String, onPick: (String, String) -> Unit) {
    val context = LocalContextCompat()
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(deviceName.ifBlank { if (address.isBlank()) "기기 고르기 — 지금은 모든 기기" else address })
            if (address.isNotBlank()) {
                Text(address, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (open) {
        val devices = remember { bondedDevices(context) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("짝지어 둔 기기") },
            text = {
                if (devices.isEmpty()) {
                    Text("짝지어 둔 기기가 없거나 블루투스 권한이 없습니다.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(devices) { (name, addr) ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { onPick(addr, name); open = false }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                Text(addr, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onPick("", ""); open = false }) { Text("모든 기기로") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("닫기") } }
        )
    }
}

@Composable
private fun LocalContextCompat(): Context = androidx.compose.ui.platform.LocalContext.current

/** 설치된 앱을 (표시이름, 패키지명)으로 뽑아 이름순 정렬 */
private fun installedApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .map { (pm.getApplicationLabel(it).toString()) to it.packageName }
        .sortedBy { it.first.lowercase() }
}

/** 짝지어 둔 기기를 (이름, MAC)으로 뽑는다. 권한이 없으면 빈 목록 */
private fun bondedDevices(context: Context): List<Pair<String, String>> {
    if (android.os.Build.VERSION.SDK_INT >= 31 &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching {
        adapter.bondedDevices.map { (it.name ?: it.address) to it.address }.sortedBy { it.first.lowercase() }
    }.getOrDefault(emptyList())
}
