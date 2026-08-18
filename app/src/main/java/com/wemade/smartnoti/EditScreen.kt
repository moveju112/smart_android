package com.wemade.smartnoti

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

    var menuOpen by remember { mutableStateOf(false) }
    var askName by remember { mutableStateOf(false) }
    val context = LocalContextCompat()
    // 처음 만드는 매크로인지. 이름은 이때 한 번만 묻고, 그 뒤로는 메뉴에서 고친다
    val isNew = remember { MacroStore.find(macro.id) == null }

    fun save() {
        if (isNew) askName = true else onSave(collect())
    }

    if (askName) {
        TextPrompt(
            title = "이름을 정해 주세요",
            hint = "목록에서 이 이름으로 찾습니다",
            initial = draft.name,
            onDone = { onSave(collect().copy(name = it.ifBlank { draft.name })); askName = false },
            onClose = { askName = false }
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
                actions = {
                    TextButton(onClick = { save() }) { Text("저장") }
                    // 자주 쓰지 않는 것은 전부 여기로 모은다. 본문에는 설정만 남는다
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "더 보기")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("이름 바꾸기") },
                            onClick = { menuOpen = false; askName = true }
                        )
                        if (simple) {
                            DropdownMenuItem(
                                text = { Text("단계를 직접 짜기") },
                                onClick = {
                                    menuOpen = false
                                    draft = draft.withClearRule(rule); simple = false
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("알림 지우기로 보기") },
                                enabled = draft.asClearRule() != null,
                                onClick = {
                                    menuOpen = false
                                    rule = draft.asClearRule() ?: rule; simple = true
                                }
                            )
                        }
                        if (MacroWidget.canPin(context)) {
                            DropdownMenuItem(
                                text = { Text("홈 화면에 버튼으로 놓기") },
                                onClick = {
                                    menuOpen = false
                                    // 저장한 뒤에 놓아야 위젯이 지금 내용을 가리킨다
                                    val saved = collect()
                                    MacroStore.upsert(context, saved)
                                    MacroWidget.pin(context, saved)
                                }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text("이 매크로 지우기", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (simple) {
                    clearRuleSections(rule) { rule = it }
                } else {
                    advancedSections(draft) { draft = it }
                }
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
            "이대로 두면 모든 앱의 알림이 전부 지워집니다. 위에서 앱이나 문구를 정해 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onErrorContainer
        )
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

// ─────────────────────────── 알림 지우기 (문장) ───────────────────────────

/**
 * 설정을 칸으로 나누지 않고 한 문장으로 보여준다.
 *
 * 사람이 여기서 하려는 일은 "토스 알림을 5초 뒤 지워줘" 한 마디를 만드는 것이다.
 * 칸을 셋으로 쪼개면 그 한 마디가 보이지 않는다. 그래서 문장을 그대로 두고,
 * 정해야 할 자리만 눌러서 채우게 했다.
 */
private fun LazyListScope.clearRuleSections(
    rule: ClearRule,
    onChange: (ClearRule) -> Unit
) {
    item { ClearSentence(rule, onChange) }

    item {
        LiveNotificationPicker(emphasis = false) { pkg, label, title, text, clearable ->
            onChange(
                rule.copy(
                    packageName = pkg,
                    appLabel = label,
                    text = text.ifBlank { title }
                )
            )
        }
    }

    item { ClearAllWarning(rule.packageName, rule.text) }

}

/** 문장 세 줄. 눌러야 하는 자리는 색으로 도드라진다 */
@Composable
private fun ClearSentence(rule: ClearRule, onChange: (ClearRule) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SentenceRow {
                Slot(
                    text = rule.appLabel.ifBlank { rule.packageName.ifBlank { "모든 앱" } },
                    filled = rule.packageName.isNotBlank()
                ) { picking = "app" }
                Tail("에서")
            }
            SentenceRow {
                Slot(
                    text = if (rule.text.isBlank()) "모든 알림" else "\"${rule.text}\"",
                    filled = rule.text.isNotBlank()
                ) { picking = "text" }
                Tail(if (rule.text.isBlank()) "을" else "포함한 알림을")
            }
            SentenceRow {
                Slot(
                    text = if (rule.seconds > 0) humanSeconds(rule.seconds) else "바로",
                    filled = rule.seconds > 0
                ) { picking = "seconds" }
                Tail(if (rule.seconds > 0) "뒤에 지웁니다" else "지웁니다")
            }
        }
    }

    when (picking) {
        "app" -> AppChooserDialog(
            onPick = { pkg, label -> onChange(rule.copy(packageName = pkg, appLabel = label)); picking = null }
        ) { picking = null }

        "text" -> TextPrompt(
            title = "알림에 들어 있는 말",
            hint = "비우면 그 앱의 알림을 전부 지웁니다",
            initial = rule.text,
            onDone = { onChange(rule.copy(text = it)); picking = null }
        ) { picking = null }

        "seconds" -> NumberPrompt(
            title = "몇 초 뒤에 지울지",
            initial = rule.seconds,
            onDone = { onChange(rule.copy(seconds = it)); picking = null }
        ) { picking = null }
    }
}

@Composable
private fun SentenceRow(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) { content() }
}

/** 문장에서 눌러 고치는 자리. 정해진 값은 청록으로 차오르고, 비면 흐리게 남는다 */
@Composable
private fun Slot(text: String, filled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        style = SentenceStyle,
        color = if (filled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                if (filled) scheme.primaryContainer else scheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

/** 문장에서 고칠 수 없는 부분 — 조사와 서술어 */
@Composable
private fun Tail(text: String) {
    Text(text, style = SentenceStyle, color = MaterialTheme.colorScheme.onSurface)
}

/** 한 줄만 받는 입력창 */
@Composable
fun TextPrompt(
    title: String,
    hint: String,
    initial: String,
    onDone: (String) -> Unit,
    onClose: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                supportingText = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onDone(value) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } }
    )
}

/** 숫자만 받는 입력창 */
@Composable
private fun NumberPrompt(title: String, initial: Int, onDone: (Int) -> Unit, onClose: () -> Unit) {
    var value by remember { mutableStateOf(initial.toString()) }
    val seconds = value.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(6) },
                supportingText = { Text(humanSeconds(seconds)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onDone(seconds) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } }
    )
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
        val triggers = draft.allTriggers()
        Section(
            1, "언제",
            if (triggers.size > 1) "이 중 하나라도 생기면 매크로가 돕니다" else "이 일이 생기면 매크로가 돕니다"
        ) {
            triggers.forEachIndexed { index, trigger ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "또는",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (triggers.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "트리거 ${index + 1}",
                            style = MonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            onChange(draft.withTriggers(triggers.filterIndexed { i, _ -> i != index }))
                        }) {
                            Icon(
                                Icons.Default.Close, "트리거 빼기",
                                Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                TriggerEditor(trigger) { changed ->
                    onChange(draft.withTriggers(triggers.mapIndexed { i, t -> if (i == index) changed else t }))
                }
            }
            OutlinedButton(
                onClick = { onChange(draft.withTriggers(triggers + Trigger.Notification())) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("트리거 추가")
            }
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
            Triple("이럴 때만 계속", "지금 상태가 조건과 다르면 여기서 멈춥니다", Action.StopUnless())
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
    is Action.StopUnless -> "이럴 때만 계속"
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
    title: String,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(!open) },
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            LiveNotificationPicker { pkg, label, title, text, _ ->
                // 제목보다 본문이 알림마다 잘 달라진다. 본문이 있으면 그쪽을 조건으로 삼는다
                onChange(trigger.copy(packageName = pkg, appLabel = label, text = text.ifBlank { title }))
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
            LiveNotificationPicker(emphasis = false) { pkg, label, title, text, clearable ->
                onChange(
                    action.copy(
                        packageName = pkg,
                        appLabel = label,
                        text = text.ifBlank { title }
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
        }

        is Action.StopUnless -> {
            ConditionEditor(action.condition) { onChange(action.copy(condition = it)) }
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

/**
 * "이럴 때만 계속" 조건을 고르고 채운다.
 *
 * 트리거가 일이 벌어진 순간을 잡고, 여기서 그 순간의 상태를 본다.
 * 둘을 합쳐야 "차에서 내렸고, 집이 아닐 때만" 같은 말이 된다.
 */
@Composable
private fun ConditionEditor(condition: Condition, onChange: (Condition) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ChoiceChip("와이파이", condition is Condition.Wifi, Modifier.weight(1f)) {
            if (condition !is Condition.Wifi) onChange(Condition.Wifi())
        }
        ChoiceChip("블루투스", condition is Condition.Bluetooth, Modifier.weight(1f)) {
            if (condition !is Condition.Bluetooth) onChange(Condition.Bluetooth())
        }
        ChoiceChip("시간대", condition is Condition.TimeRange, Modifier.weight(1f)) {
            if (condition !is Condition.TimeRange) onChange(Condition.TimeRange(23 * 60, 7 * 60))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ChoiceChip("배터리", condition is Condition.Battery, Modifier.weight(1f)) {
            if (condition !is Condition.Battery) onChange(Condition.Battery())
        }
        ChoiceChip("위치", condition is Condition.Place, Modifier.weight(1f)) {
            if (condition !is Condition.Place) onChange(Condition.Place())
        }
        Spacer(Modifier.weight(1f))
    }

    when (condition) {
        is Condition.Wifi -> StateSwitch(
            "와이파이에 붙어 있을 때만", "와이파이에 붙어 있지 않을 때만", condition.connected
        ) { onChange(condition.copy(connected = it)) }

        is Condition.Bluetooth -> {
            DevicePicker(condition.address, condition.deviceName) { addr, name ->
                onChange(condition.copy(address = addr, deviceName = name))
            }
            StateSwitch(
                "이 기기가 붙어 있을 때만", "이 기기가 끊겨 있을 때만", condition.connected
            ) { onChange(condition.copy(connected = it)) }
        }

        is Condition.TimeRange -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockField("부터", condition.fromMinute, Modifier.weight(1f)) {
                    onChange(condition.copy(fromMinute = it))
                }
                ClockField("까지", condition.toMinute, Modifier.weight(1f)) {
                    onChange(condition.copy(toMinute = it))
                }
            }
            // 자정을 넘기는 구간도 그대로 받는다 (23:00~07:00)
            Text(
                condition.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StateSwitch("이 시간대일 때만", "이 시간대를 벗어났을 때만", condition.inside) {
                onChange(condition.copy(inside = it))
            }
        }

        is Condition.Battery -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PercentField("최소 %", condition.atLeast, Modifier.weight(1f)) {
                    onChange(condition.copy(atLeast = it))
                }
                PercentField("최대 %", condition.atMost, Modifier.weight(1f)) {
                    onChange(condition.copy(atMost = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip("충전 상관없음", condition.charging == null, Modifier.weight(1f)) {
                    onChange(condition.copy(charging = null))
                }
                ChoiceChip("충전 중", condition.charging == true, Modifier.weight(1f)) {
                    onChange(condition.copy(charging = true))
                }
                ChoiceChip("충전 아님", condition.charging == false, Modifier.weight(1f)) {
                    onChange(condition.copy(charging = false))
                }
            }
        }

        is Condition.Place -> PlaceEditor(condition, onChange)
    }
}

/** 시:분을 받는 칸 */
@Composable
private fun ClockField(label: String, minuteOfDay: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = clockText(minuteOfDay),
        onValueChange = { raw ->
            // 숫자만 남겨 HHMM으로 읽는다. 콜론을 손으로 넣지 않아도 된다
            val digits = raw.filter(Char::isDigit).take(4)
            if (digits.length == 4) {
                val h = digits.take(2).toInt().coerceAtMost(23)
                val m = digits.drop(2).toInt().coerceAtMost(59)
                onChange(h * 60 + m)
            }
        },
        label = { Text(label) },
        supportingText = { Text("시분 네 자리") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun PercentField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onChange((it.filter(Char::isDigit).take(3).toIntOrNull() ?: 0).coerceIn(0, 100)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/**
 * 자리를 정한다.
 *
 * 좌표를 손으로 적을 사람은 없으니 지금 있는 자리를 그대로 담는다.
 * 위치는 새로 잡지 않고 다른 앱이 이미 받아 둔 마지막 값을 읽어, 배터리를 쓰지 않는다.
 */
@Composable
private fun PlaceEditor(place: Condition.Place, onChange: (Condition.Place) -> Unit) {
    val context = LocalContextCompat()
    var problem by remember { mutableStateOf<String?>(null) }

    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            val here = lastKnownPlace(context)
            if (here == null) problem = "아직 위치를 모릅니다. 지도 앱을 한 번 열어 위치를 잡은 뒤 다시 눌러 주세요."
            else { onChange(place.copy(latitude = here.first, longitude = here.second)); problem = null }
        } else {
            problem = "위치 권한이 없으면 이 조건을 쓸 수 없습니다."
        }
    }

    OutlinedButton(
        onClick = {
            val here = lastKnownPlace(context)
            if (here != null) {
                onChange(place.copy(latitude = here.first, longitude = here.second)); problem = null
            } else {
                askLocation.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("지금 있는 자리로 정하기") }

    OutlinedTextField(
        value = place.label,
        onValueChange = { onChange(place.copy(label = it)) },
        label = { Text("자리 이름") },
        supportingText = { Text("집, 회사처럼 알아볼 이름") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (place.latitude == 0.0 && place.longitude == 0.0) "아직 자리를 정하지 않았습니다"
            else "%.5f, %.5f".format(place.latitude, place.longitude),
            style = MonoSmall,
            color = if (place.latitude == 0.0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }

    OutlinedTextField(
        value = place.radiusMeters.toString(),
        onValueChange = { onChange(place.copy(radiusMeters = (it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0).coerceAtLeast(20))) },
        label = { Text("반경 (m)") },
        supportingText = { Text("마지막으로 알려진 위치를 쓰므로 넉넉하게 두세요") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )

    StateSwitch("이 자리 안일 때만", "이 자리 밖일 때만", place.inside) {
        onChange(place.copy(inside = it))
    }

    // 매크로는 배경에서 돈다. 위치는 "앱 사용 중에만" 허용이면 그때 읽지 못한다
    Text(
        "설정 → 앱 → 위치를 \"항상 허용\"으로 두어야 배경에서도 이 조건을 볼 수 있습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    problem?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** 마지막으로 알려진 위치. 권한이 없거나 아직 잡힌 적이 없으면 null */
private fun lastKnownPlace(context: Context): Pair<Double, Double>? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) return null
    val manager = context.getSystemService(android.location.LocationManager::class.java) ?: return null
    return runCatching {
        manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }.getOrNull()
}

// ─────────────────────────── 고르기 창 ───────────────────────────

/**
 * 지금 떠 있는 알림에서 골라 앱과 문구를 한 번에 채운다.
 * 문구를 손으로 맞추다 틀리는 일이 가장 흔한 실패라서 둔 길이다.
 */
@Composable
private fun LiveNotificationPicker(
    emphasis: Boolean = true,
    onPick: (pkg: String, label: String, title: String, text: String, clearable: Boolean) -> Unit
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
            Text("지금 떠 있는 알림에서 고르기")
        }
    }

    if (open) {
        LiveNotificationDialog(
            onPick = { p, l, t, x, c -> onPick(p, l, t, x, c); open = false },
            onClose = { open = false }
        )
    }
}

/**
 * 지금 떠 있는 알림에서 골라 앱과 문구를 한 번에 채운다.
 * 문구를 손으로 맞추다 틀리는 일이 가장 흔한 실패라서 둔 길이다.
 */
@Composable
private fun LiveNotificationDialog(
    onPick: (pkg: String, label: String, title: String, text: String, clearable: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val items = remember { MacroService.instance?.snapshot().orEmpty() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("지금 떠 있는 알림") },
        text = {
            if (items.isEmpty()) {
                Text("떠 있는 알림이 없거나 엔진이 꺼져 있습니다.\n지우려는 알림을 띄운 상태에서 다시 열어 보세요.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(items) { peek ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { onPick(peek.packageName, peek.appLabel, peek.title, peek.text, peek.clearable) }
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
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } }
    )
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
    var open by remember { mutableStateOf(false) }

    PickerField(
        label = "앱",
        value = appLabel.ifBlank { if (packageName.isBlank()) "모든 앱" else packageName },
        detail = packageName.takeIf { it.isNotBlank() && appLabel.isNotBlank() },
        onClick = { open = true }
    )
    if (open) AppChooserDialog(onPick = { pkg, label -> onPick(pkg, label); open = false }) { open = false }
}

/** 설치된 앱에서 하나 고르는 창. 버튼이 열든 문장 속 조각이 열든 같은 창이다 */
@Composable
private fun AppChooserDialog(onPick: (String, String) -> Unit, onClose: () -> Unit) {
    val context = LocalContextCompat()
    val apps = remember { installedApps(context) }
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
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
                                .clickable { onPick(pkg, label) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(pkg, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick("", "") }) { Text("모든 앱으로") } },
        dismissButton = { TextButton(onClick = onClose) { Text("닫기") } }
    )
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
