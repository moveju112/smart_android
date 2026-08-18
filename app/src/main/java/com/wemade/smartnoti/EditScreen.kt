package com.wemade.smartnoti

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 매크로 편집. 두 갈래로 나뉜다.
 *
 * 쓰던 매크로 대부분이 "이 알림 뜨면 좀 있다 지우기" 한 가지 모양이라,
 * 그 모양은 칸 세 개짜리 화면으로 따로 다룬다. 나머지만 단계를 직접 엮는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(macro: Macro, onSave: (Macro) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf(macro) }
    var rule by remember { mutableStateOf(macro.asClearRule() ?: ClearRule()) }
    var simple by remember { mutableStateOf(macro.asClearRule() != null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 지금 화면의 내용을 저장할 매크로 한 개로 모은다
    fun collect(): Macro = if (simple) draft.withClearRule(rule) else draft

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
                title = { Text(if (simple) "알림 지우기" else "직접 짜기") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = { TextButton(onClick = { onSave(collect()) }) { Text("저장") } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    HeaderCard(
                        name = draft.name,
                        summary = collect().oneLine(),
                        onName = { draft = draft.copy(name = it) }
                    )
                }

                item {
                    ModeSwitch(
                        simple = simple,
                        // 단계를 직접 엮어 둔 매크로는 한 장짜리 화면으로 접을 수 없다
                        simpleAvailable = simple || draft.asClearRule() != null,
                        onSimple = { rule = draft.asClearRule() ?: rule; simple = true },
                        onAdvanced = { draft = draft.withClearRule(rule); simple = false }
                    )
                }

                if (simple) {
                    clearRuleSections(rule) { rule = it }
                } else {
                    advancedSections(draft) { draft = it }
                }

                item {
                    // 저장한 뒤에 놓아야 위젯이 지금 내용을 가리킨다
                    val context = LocalContextCompat()
                    if (MacroWidget.canPin(context)) {
                        OutlinedButton(
                            onClick = {
                                val saved = collect()
                                MacroStore.upsert(context, saved)
                                MacroWidget.pin(context, saved)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("홈 화면에 버튼으로 놓기") }
                    }
                }

                item {
                    // 저장은 맨 위에 늘 떠 있다. 여기까지 내려와야 하는 것은 지우기뿐이다
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text("이 매크로 지우기", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * 이름과, 지금 설정이 무엇을 하는지 한 줄.
 * 아래에서 무엇을 만지든 결과가 여기에 바로 비쳐서, 저장하기 전에 확인할 수 있다.
 */
@Composable
private fun HeaderCard(name: String, summary: String, onName: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "하는 일",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 앱도 문구도 비어 있으면 눈에 보이는 알림이 전부 사라진다. 그 전에 알려 준다 */
@Composable
private fun ClearAllWarning(packageName: String, text: String) {
    if (packageName.isNotBlank() || text.isNotBlank()) return
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(scheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("주의", style = MonoSmall, color = scheme.onErrorContainer)
        Spacer(Modifier.width(10.dp))
        Text(
            "지금 설정으로는 모든 앱의 알림이 전부 지워집니다.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onErrorContainer
        )
    }
}

/** 어느 방식으로 다룰지. 고른 쪽이 채워져 한눈에 보인다 */
@Composable
private fun ModeSwitch(
    simple: Boolean,
    simpleAvailable: Boolean,
    onSimple: () -> Unit,
    onAdvanced: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("알림 지우기", simple, Modifier.weight(1f), enabled = simpleAvailable, onClick = onSimple)
        ChoiceChip("직접 짜기", !simple, Modifier.weight(1f), onClick = onAdvanced)
    }
}

/**
 * 무엇을 고르는 칩. 고른 것은 청록으로 채운다.
 */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.onPrimaryContainer
        ),
        modifier = modifier
    )
}

// ─────────────────────────── 알림 지우기 (간단) ───────────────────────────

/** 칸 세 개로 끝난다 — 어떤 알림을, 언제, 까다로운 알림까지 건드릴지 */
private fun LazyListScope.clearRuleSections(
    rule: ClearRule,
    onChange: (ClearRule) -> Unit
) {
    item {
        Section(1, "어떤 알림을", "지우려는 알림을 띄워 둔 채로 고르면 가장 정확합니다") {
            LiveNotificationPicker { pkg, title, text, clearable ->
                onChange(
                    rule.copy(
                        packageName = pkg,
                        appLabel = "",
                        text = text.ifBlank { title },
                        // 지울 수 없는 알림을 골랐다면 그걸 지우겠다는 뜻이다
                        includeOngoing = rule.includeOngoing || !clearable
                    )
                )
            }
            AppPicker(rule.packageName, rule.appLabel) { pkg, label ->
                onChange(rule.copy(packageName = pkg, appLabel = label))
            }
            OutlinedTextField(
                value = rule.text,
                onValueChange = { onChange(rule.copy(text = it)) },
                label = { Text("알림에 들어 있는 말") },
                supportingText = { Text("비우면 그 앱의 알림을 전부 지웁니다") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ClearAllWarning(rule.packageName, rule.text)
        }
    }

    item {
        Section(2, "언제 지울지", "너무 빨리 지우면 알림을 보지도 못합니다") {
            SecondsField(rule.seconds) { onChange(rule.copy(seconds = it)) }
        }
    }

    item {
        // 대개는 건드릴 일이 없다. 접어 두고, 켜져 있을 때만 펼쳐 둔다
        var open by remember { mutableStateOf(rule.includeOngoing) }
        FoldableSection(3, "잘 안 지워질 때", open, { open = it }) {
            SwitchRow(
                title = "지울 수 없는 알림도 지우기",
                hint = "진행 중 표시라 손으로도 못 지우는 알림까지 건드립니다.",
                checked = rule.includeOngoing
            ) { onChange(rule.copy(includeOngoing = it)) }
        }
    }
}

@Composable
private fun SecondsField(seconds: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = seconds.toString(),
        onValueChange = { onChange(it.filter(Char::isDigit).take(6).toIntOrNull() ?: 0) },
        label = { Text("몇 초 뒤") },
        supportingText = { Text(humanSeconds(seconds)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────── 직접 짜기 (고급) ───────────────────────────

private fun LazyListScope.advancedSections(
    draft: Macro,
    onChange: (Macro) -> Unit
) {
    item {
        Section(1, "언제", "이 일이 생기면 매크로가 돕니다") {
            TriggerEditor(draft.trigger) { onChange(draft.copy(trigger = it)) }
        }
    }

    item { SectionHeading(2, "무엇을", "위에서 아래로 차례대로 실행합니다") }

    items(draft.actions.size, key = { it }) { index ->
        ActionCard(
            index = index,
            total = draft.actions.size,
            action = draft.actions[index],
            onEdit = { changed ->
                onChange(draft.copy(actions = draft.actions.mapIndexed { i, a -> if (i == index) changed else a }))
            },
            onRemove = {
                onChange(draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index }))
            },
            onMove = { delta ->
                val to = index + delta
                if (to in draft.actions.indices) {
                    val list = draft.actions.toMutableList()
                    list.add(to, list.removeAt(index))
                    onChange(draft.copy(actions = list))
                }
            }
        )
    }

    item {
        if (draft.actions.isEmpty()) {
            Text(
                "아직 하는 일이 없습니다. 아래에서 단계를 더하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        AddStepButton { onChange(draft.copy(actions = draft.actions + it)) }
    }
}

/** 단계는 네 가지뿐이라, 버튼 넉 장을 늘어놓는 대신 한 번 눌러 설명과 함께 고르게 한다 */
@Composable
private fun AddStepButton(onAdd: (Action) -> Unit) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("단계 추가")
    }

    if (open) {
        val choices = listOf(
            Triple("대기", "다음 단계까지 시간을 둡니다", Action.Delay() as Action),
            Triple("알림 삭제", "조건에 맞는 알림을 지웁니다", Action.ClearNotification()),
            Triple("브로드캐스트", "다른 앱에 신호를 보냅니다", Action.Broadcast()),
            Triple("조건부 중단", "기기 상태가 맞으면 여기서 멈춥니다", Action.StopIfBluetooth())
        )
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("어떤 단계를 넣을까요?") },
            text = {
                Column {
                    choices.forEachIndexed { i, (label, hint, action) ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { onAdd(action); open = false }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("닫기") } }
        )
    }
}

/** 단계 한 칸. 순서가 곧 실행 순서라 위아래로 옮길 수 있게 둔다 */
@Composable
private fun ActionCard(
    index: Int,
    total: Int,
    action: Action,
    onEdit: (Action) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val (kind, accent) = when (action.step()) {
        Step.Wait -> "대기" to scheme.secondary
        Step.Gate -> "조건" to scheme.primary
        else -> "실행" to scheme.onSurfaceVariant
    }

    Card(colors = CardDefaults.cardColors(containerColor = scheme.surface)) {
        Row(Modifier.fillMaxWidth()) {
            // 단계의 성격을 왼쪽 색 띠로 — 대기·조건·실행이 목록에서 바로 갈린다
            Box(Modifier.width(4.dp).heightIn(min = 56.dp).background(accent))
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}",
                        style = MonoSmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$kind · ${action.kindLabel()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                        Icon(Icons.Default.ArrowUpward, "위로", Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onMove(1) }, enabled = index < total - 1) {
                        Icon(Icons.Default.ArrowDownward, "아래로", Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, "단계 빼기", Modifier.size(18.dp), tint = scheme.error)
                    }
                }
                Column(
                    Modifier.padding(end = 8.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionEditor(action, onEdit)
                }
            }
        }
    }
}

private fun Action.kindLabel(): String = when (this) {
    is Action.Delay -> "시간 두기"
    is Action.ClearNotification -> "알림 삭제"
    is Action.Broadcast -> "브로드캐스트"
    is Action.StopIfBluetooth -> "조건부 중단"
}

// ─────────────────────────── 공통 뼈대 ───────────────────────────

/**
 * 섹션 한 덩어리. 번호 + 제목 + 카드로 묶어 어디까지가 한 가지 일인지 보이게 한다.
 * 화면이 난잡했던 건 칸은 많은데 무엇끼리 한 묶음인지가 없어서였다.
 */
@Composable
private fun Section(
    number: Int,
    title: String,
    hint: String?,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(number, title, hint)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) { content() }
        }
    }
}

/** 자주 건드리지 않는 칸. 제목만 두고 접어 둔다 */
@Composable
private fun FoldableSection(
    number: Int,
    title: String,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 구분선은 줄 전체에 그어야 하므로 제목 묶음 밖에서 따로 그린다
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(!open) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberBadge(number)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!open) {
                    Text(
                        "눌러서 펼치기",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(
                if (open) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (open) "접기" else "펼치기",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (open) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) { content() }
            }
        }
    }
}

@Composable
private fun SectionHeading(number: Int, title: String, hint: String? = null) {
    Column {
        // 앞 칸과 눈으로 끊어 준다. 첫 칸 위에는 끊을 것이 없다
        if (number > 1) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(number)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberBadge(number: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(22.dp).background(scheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            number.toString(),
            style = MonoSmall.copy(fontFamily = FontFamily.Monospace),
            color = scheme.onPrimary
        )
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StateSwitch(onLabel: String, offLabel: String, value: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(title = if (value) onLabel else offLabel, hint = null, checked = value, onChange = onChange)
}

// ─────────────────────────── 트리거·액션 편집 ───────────────────────────

@Composable
private fun TriggerEditor(trigger: Trigger, onChange: (Trigger) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ChoiceChip("알림", trigger is Trigger.Notification, Modifier.weight(1f)) {
            if (trigger !is Trigger.Notification) onChange(Trigger.Notification())
        }
        ChoiceChip("블루투스", trigger is Trigger.Bluetooth, Modifier.weight(1f)) {
            if (trigger !is Trigger.Bluetooth) onChange(Trigger.Bluetooth())
        }
        ChoiceChip("와이파이", trigger is Trigger.Wifi, Modifier.weight(1f)) {
            if (trigger !is Trigger.Wifi) onChange(Trigger.Wifi())
        }
    }

    when (trigger) {
        is Trigger.Notification -> {
            LiveNotificationPicker { pkg, title, text, _ ->
                // 제목보다 본문이 알림마다 잘 달라진다. 본문이 있으면 그쪽을 조건으로 삼는다
                onChange(trigger.copy(packageName = pkg, appLabel = "", text = text.ifBlank { title }))
            }
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
            StateSwitch("기기가 연결될 때", "기기가 끊길 때", trigger.connected) {
                onChange(trigger.copy(connected = it))
            }
        }
        is Trigger.Wifi -> StateSwitch("와이파이가 연결될 때", "와이파이가 끊길 때", trigger.connected) {
            onChange(trigger.copy(connected = it))
        }
    }
}

@Composable
private fun ActionEditor(action: Action, onChange: (Action) -> Unit) {
    when (action) {
        is Action.Delay -> {
            SecondsField(action.seconds) { onChange(action.copy(seconds = it)) }
        }

        is Action.ClearNotification -> {
            LiveNotificationPicker(emphasis = false) { pkg, title, text, clearable ->
                onChange(
                    action.copy(
                        packageName = pkg,
                        appLabel = "",
                        text = text.ifBlank { title },
                        includeOngoing = action.includeOngoing || !clearable
                    )
                )
            }
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
            ClearAllWarning(action.packageName, action.text)
            SwitchRow(
                title = "지울 수 없는 알림도 지우기",
                hint = "진행 중 표시라 손으로도 못 지우는 알림까지 건드립니다.",
                checked = action.includeOngoing
            ) { onChange(action.copy(includeOngoing = it)) }
        }

        is Action.StopIfBluetooth -> {
            DevicePicker(action.address, action.deviceName) { addr, name ->
                onChange(action.copy(address = addr, deviceName = name))
            }
            StateSwitch(
                "이 기기가 연결돼 있으면 여기서 멈춤",
                "이 기기가 끊겨 있으면 여기서 멈춤",
                action.connected
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

// ─────────────────────────── 고르기 창 ───────────────────────────

/**
 * 지금 떠 있는 알림에서 골라 앱과 문구를 한 번에 채운다.
 * 문구를 손으로 맞추다 틀리는 일이 가장 흔한 실패라서 둔 길이다.
 */
@Composable
private fun LiveNotificationPicker(
    emphasis: Boolean = true,
    onPick: (pkg: String, title: String, text: String, clearable: Boolean) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    // 한 화면에 두 번 나올 수 있다. 주 동작일 때만 채우고, 곁다리일 때는 테두리만 둔다
    if (emphasis) {
        FilledTonalButton(
            onClick = { open = true },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer
            ),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { Text("지금 떠 있는 알림에서 고르기") }
    } else {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("알림에서 고르기")
        }
    }

    if (open) {
        val items = remember { MacroService.instance?.snapshot().orEmpty() }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("지금 떠 있는 알림") },
            text = {
                if (items.isEmpty()) {
                    Text("떠 있는 알림이 없거나 엔진이 꺼져 있습니다.\n지우려는 알림을 띄운 상태에서 다시 열어 보세요.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(items) { peek ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        onPick(peek.packageName, peek.title, peek.text, peek.clearable)
                                        open = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(peek.title.ifBlank { "(제목 없음)" }, style = MaterialTheme.typography.bodyMedium)
                                if (peek.text.isNotBlank()) {
                                    Text(peek.text, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    peek.packageName + if (!peek.clearable) "  · 지울 수 없는 알림" else "",
                                    style = MonoSmall,
                                    color = if (peek.clearable) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("닫기") } }
        )
    }
}

/** 고른 값을 그 자리에 보여주는 칸. 눌러야 뭐가 들었는지 아는 버튼은 두지 않는다 */
@Composable
private fun PickerField(label: String, value: String, detail: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, scheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = MonoSmall, color = scheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        if (!detail.isNullOrBlank()) {
            Text(detail, style = MonoSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppPicker(packageName: String, appLabel: String, onPick: (String, String) -> Unit) {
    val context = LocalContextCompat()
    var open by remember { mutableStateOf(false) }

    PickerField(
        label = "앱",
        value = appLabel.ifBlank { if (packageName.isBlank()) "모든 앱" else packageName },
        detail = packageName.takeIf { it.isNotBlank() && appLabel.isNotBlank() },
        onClick = { open = true }
    )

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

    PickerField(
        label = "기기",
        value = deviceName.ifBlank { if (address.isBlank()) "모든 기기" else address },
        detail = address.takeIf { it.isNotBlank() && deviceName.isNotBlank() },
        onClick = { open = true }
    )

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
