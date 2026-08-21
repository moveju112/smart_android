package com.wemade.smartnoti

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** 편집 중인 매크로를 문자열 한 줄로 접었다 편다 */
private val MacroSaver = Saver<Macro, String>(
    save = { macroJson.encodeToString(it) },
    restore = { runCatching { macroJson.decodeFromString<Macro>(it) }.getOrNull() }
)

/** 알림 지우기 규칙은 값 네 개가 전부다 */
private val ClearRuleSaver = listSaver<ClearRule, Any>(
    save = { listOf(it.packageName, it.appLabel, it.text, it.seconds) },
    restore = { ClearRule(it[0] as String, it[1] as String, it[2] as String, it[3] as Int) }
)

/**
 * 매크로 편집. 두 갈래로 나뉜다.
 *
 * 쓰던 매크로 대부분이 "이 알림 뜨면 좀 있다 지우기" 한 가지 모양이라,
 * 그 모양은 칸 세 개짜리 화면으로 따로 다룬다. 나머지만 단계를 직접 엮는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(macro: Macro, onSave: (Macro) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var draft by rememberSaveable(stateSaver = MacroSaver) { mutableStateOf(macro) }
    var rule by rememberSaveable(stateSaver = ClearRuleSaver) { mutableStateOf(macro.asClearRule() ?: ClearRule()) }
    var simple by rememberSaveable { mutableStateOf(macro.asClearRule() != null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    // 지금 화면의 내용을 저장할 매크로 한 개로 모은다
    fun collect(): Macro = if (simple) draft.withClearRule(rule) else draft

    /**
     * 화면에 들어온 순간의 모양.
     *
     * 옛 형식 매크로는 여는 것만으로 새 형식으로 펴진다. 원본과 견주면 손도 대지 않았는데
     * 고친 것으로 세어, 나갈 때마다 저장하겠느냐고 묻게 된다. 그래서 펴진 뒤의 모양을 잡아 둔다.
     */
    val opened by rememberSaveable(stateSaver = MacroSaver) { mutableStateOf(collect()) }

    // 고친 것이 있는데 그냥 나가면 그대로 사라진다. 나가기 전에 저장할지 묻는다
    fun leave() {
        if (collect() == opened) onCancel() else confirmLeave = true
    }
    BackHandler { leave() }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 매크로를 지울까요?") },
            text = { Text("\u201C${draft.name}\u201D이 사라집니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("지우기", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } }
        )
    }

    var menuOpen by remember { mutableStateOf(false) }
    var askName by remember { mutableStateOf(false) }
    val context = LocalContextCompat()
    // 처음 만드는 매크로인지. 이름은 이때 한 번만 묻고, 그 뒤로는 메뉴에서 고친다
    val isNew = remember { MacroStore.find(macro.id) == null }

    // 저장할 수 없는 이유가 있으면 그것부터 보여 준다. 경고만 하고 저장을 허락하면
    // "영원히 거기서 멈추는 매크로"가 만들어진다
    var problem by remember { mutableStateOf<String?>(null) }

    /**
     * 지금 펼쳐 둔 조각. `"t2"`는 세 번째 트리거, `"a0"`은 첫 단계, null은 다 접힌 상태다.
     *
     * 전에는 모든 조각이 펼쳐진 채였다. 단계가 여섯이면 화면이 폼 덩어리가 되고,
     * 정작 고치려던 한 줄을 찾는 데 스크롤이 필요했다. 한 번에 하나만 연다.
     */
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    fun save() {
        val blocked = collect().saveProblem()
        if (blocked != null) {
            problem = blocked
            return
        }
        if (isNew) askName = true else onSave(collect())
    }

    problem?.let { reason ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text("이대로는 저장할 수 없습니다") },
            text = { Text(reason) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text("고치기") } }
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("수정한 내용을 저장할까요?") },
            text = { Text("저장하지 않으면 고친 내용이 사라집니다.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; save() }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onCancel() }) {
                    Text("저장 안 함", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (askName) {
        TextPrompt(
            title = "이름을 정해 주세요",
            hint = "목록에서 이 이름으로 찾습니다",
            initial = draft.name,
            confirmLabel = "저장",
            onDone = { onSave(collect().copy(name = it.ifBlank { draft.name })); askName = false },
            onClose = { askName = false }
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                // 비슷한 이름 13개를 오가며 고칠 때, 무엇을 고치는지가 화면에 있어야 한다.
                // 모드 이름은 아래로 내린다 — 그건 이미 화면 모양으로 알 수 있다
                title = {
                    Column {
                        Text(
                            draft.name.ifBlank { "새 매크로" },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (simple) "알림 지우기" else "직접 짜기",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
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
        // 키보드가 올라오면 목록만 줄어들어야 한다. 그러지 않으면 창이 통째로 밀려
        // 상단 바가 사라지고 본문이 상태바 밑으로 들어간다 (targetSdk 35 는 창을 안 줄인다)
        Box(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (simple) {
                    clearRuleSections(rule) { rule = it }
                } else {
                    advancedSections(draft, open, { open = it }) { draft = it }
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
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("주의", style = MonoSmall, color = scheme.onErrorContainer)
        Spacer(Modifier.width(12.dp))
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

    item { ClearAllWarning(rule.packageName, rule.text) }

}

/** 문장 세 줄. 눌러야 하는 자리는 색으로 도드라진다 */
@Composable
private fun ClearSentence(rule: ClearRule, onChange: (ClearRule) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    text = if (rule.text.isBlank()) "모든 알림" else "\u201C${rule.text}\u201D",
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 문구를 손으로 맞추다 틀리는 일이 가장 흔한 실패다. 떠 있는 알림에서 그대로 가져온다
        LiveNotificationPicker(emphasis = false) { pkg, label, title, text, _ ->
            onChange(
                rule.copy(
                    packageName = pkg,
                    appLabel = label,
                    text = text.ifBlank { title }
                )
            )
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
    confirmLabel: String = "확인",
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
        confirmButton = { TextButton(onClick = { onDone(value) }) { Text(confirmLabel) } },
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
                // 60초가 넘어야 "5분"처럼 바꿔 읽을 값이 생긴다
                supportingText = if (seconds >= 60) {
                    { Text(humanSeconds(seconds)) }
                } else null,
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
    // 30분을 1800이라 적게 하던 자리다. 이 앱은 다른 모든 곳에서 「30분」이라 말하는데
    // 정작 값을 넣는 자리만 초로 되돌아갔다. 단위를 골라 적게 한다
    val unit = remember(seconds) { fittingUnit(seconds) }
    val shown = if (seconds == 0) "" else (seconds / unit.factor).toString()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = shown,
            onValueChange = {
                val amount = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0
                onChange(amount * unit.factor)
            },
            label = { Text("얼마 뒤") },
            supportingText = if (seconds >= 60) { { Text(humanSeconds(seconds)) } } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TimeUnit.entries.forEach { candidate ->
                ChoiceChip(candidate.label, candidate == unit, Modifier.weight(1f)) {
                    // 단위를 바꾸면 적어 둔 숫자를 그 단위로 읽는다. 30 분 -> 30 시간
                    val amount = (seconds / unit.factor).coerceAtLeast(0)
                    onChange(amount * candidate.factor)
                }
            }
        }
    }
}

/** 대기 시간을 적는 단위 */
private enum class TimeUnit(val label: String, val factor: Int) {
    Seconds("초", 1),
    Minutes("분", 60),
    Hours("시간", 3600)
}

/** 지금 값을 딱 나누어 떨어지게 담는 가장 큰 단위 */
private fun fittingUnit(seconds: Int): TimeUnit = when {
    seconds > 0 && seconds % 3600 == 0 -> TimeUnit.Hours
    seconds > 0 && seconds % 60 == 0 -> TimeUnit.Minutes
    else -> TimeUnit.Seconds
}

// ─────────────────────────── 직접 짜기 (고급) ───────────────────────────

private fun LazyListScope.advancedSections(
    draft: Macro,
    open: String?,
    onOpen: (String?) -> Unit,
    onChange: (Macro) -> Unit
) {
    val triggers = draft.allTriggers()

    item {
        SectionHeading(
            1, "언제",
            if (triggers.size > 1) "이 중 하나라도 생기면 매크로가 돕니다" else "이 일이 생기면 매크로가 돕니다"
        )
    }

    // 트리거도 액션과 같은 모양으로 세운다. 같은 조각인데 한쪽은 공용 카드 안, 한쪽은 제 카드였다
    items(triggers.size, key = { "t$it" }) { index ->
        val trigger = triggers[index]
        val key = "t$index"
        Column {
            // 「또는」은 두 카드 사이에 놓인다. 하나가 끝나고 다음이 시작한다는 표시다
            if (index > 0) {
                Text(
                    "또는",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                FoldedPiece(
                    mark = Step.Trigger,
                    accent = MaterialTheme.colorScheme.primary,
                    order = if (triggers.size > 1) "${index + 1}" else null,
                    summary = trigger.parts(),
                    opened = open == key,
                    onToggle = { onOpen(if (open == key) null else key) },
                    onRemove = if (triggers.size > 1) {
                        {
                            onChange(draft.withTriggers(triggers.filterIndexed { i, _ -> i != index }))
                            onOpen(null)
                        }
                    } else null,
                    padded = false
                ) {
                    TriggerEditor(trigger) { changed ->
                        onChange(draft.withTriggers(triggers.mapIndexed { i, t -> if (i == index) changed else t }))
                    }
                }
            }
        }
    }

    item {
        OutlinedButton(
            onClick = {
                onChange(draft.withTriggers(triggers + Trigger.Notification()))
                // 방금 넣은 것은 펼쳐 준다. 넣자마자 채워야 하는 값이라서다
                onOpen("t${triggers.size}")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("트리거 추가")
        }
    }

    item { SectionHeading(2, "무엇을", "위에서 아래로 차례대로 실행합니다") }

    items(draft.actions.size, key = { it }) { index ->
        val key = "a$index"
        ActionCard(
            index = index,
            total = draft.actions.size,
            action = draft.actions[index],
            opened = open == key,
            onToggle = { onOpen(if (open == key) null else key) },
            onEdit = { changed ->
                onChange(draft.copy(actions = draft.actions.mapIndexed { i, a -> if (i == index) changed else a }))
            },
            onRemove = {
                onChange(draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index }))
                onOpen(null)
            },
            onMove = { delta ->
                val to = index + delta
                if (to in draft.actions.indices) {
                    val list = draft.actions.toMutableList()
                    list.add(to, list.removeAt(index))
                    onChange(draft.copy(actions = list))
                    // 옮긴 것을 계속 따라간다. 손이 그 조각에 머물러 있으니까
                    onOpen("a$to")
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
        AddStepButton {
            onChange(draft.copy(actions = draft.actions + it))
            // 방금 넣은 단계는 펼쳐 준다
            onOpen("a${draft.actions.size}")
        }
    }
}

/** 단계는 네 가지뿐이라, 버튼 넉 장을 늘어놓는 대신 한 번 눌러 설명과 함께 고르게 한다 */
@Composable
private fun AddStepButton(onAdd: (Action) -> Unit) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("단계 추가")
    }

    if (open) {
        val choices = listOf(
            Triple("대기", "다음 단계까지 시간을 둡니다", Action.Delay() as Action),
            Triple("알림 삭제", "조건에 맞는 알림을 지웁니다", Action.ClearNotification()),
            Triple("브로드캐스트", "다른 앱에 신호를 보냅니다", Action.Broadcast()),
            Triple("이럴 때만 계속", "지금 상태가 조건과 다르면 여기서 멈춥니다", Action.StopUnless())
        )
        PickerSheet("어떤 단계를 넣을까요?", onClose = { open = false }) {
            itemsIndexed(choices) { i, (label, hint, action) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onAdd(action); open = false }
                        .padding(vertical = 12.dp)
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
    }
}

/** 단계 한 칸. 순서가 곧 실행 순서라 위아래로 옮길 수 있게 둔다 */
@Composable
private fun ActionCard(
    index: Int,
    total: Int,
    action: Action,
    opened: Boolean,
    onToggle: () -> Unit,
    onEdit: (Action) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (action.step()) {
        Step.Wait -> scheme.secondary
        Step.Gate -> scheme.primary
        else -> scheme.onSurfaceVariant
    }

    Card(colors = CardDefaults.cardColors(containerColor = scheme.surface)) {
        FoldedPiece(
            mark = action.step(),
            accent = accent,
            order = "${index + 1}",
            summary = action.parts(),
            opened = opened,
            onToggle = onToggle,
            onRemove = onRemove,
            onMove = onMove.takeIf { total > 1 },
            canMoveUp = index > 0,
            canMoveDown = index < total - 1,
            padded = false
        ) {
            ActionEditor(action, onEdit)
        }
    }
}

/**
 * 접었다 펴는 조각 하나.
 *
 * 편집 화면이 열리면 모든 조각이 펼쳐져 있었다. 단계가 여섯이면 폼 덩어리가 되고,
 * 고치려던 한 줄을 찾는 데 스크롤이 필요했다.
 *
 * 접힌 줄에는 목록 카드가 쓰는 그 요약을 그대로 쓴다 — 「30분 대기」,
 * 「WireGuard 터널 켜기 · home-server」. 새 언어를 만들지 않고 앱이 이미 아는 말을 쓴다.
 * 그래서 다 접힌 편집 화면은 목록 카드와 똑같이 읽힌다.
 *
 * 옮기고 빼는 버튼은 펼친 것에만 둔다. 접힌 줄에 조작점이 넷이면 다시 복잡해진다.
 */
@Composable
private fun FoldedPiece(
    mark: Step,
    accent: Color,
    order: String?,
    summary: List<Part>,
    opened: Boolean,
    onToggle: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onMove: ((Int) -> Unit)? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    padded: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(if (padded) 0.dp else 4.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (opened) "접기" else "펼쳐서 고치기",
                    onClick = onToggle
                )
                .padding(start = if (padded) 0.dp else 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepMark(mark, accent)
            Spacer(Modifier.width(12.dp))
            if (order != null) {
                Text(order, style = MonoSmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            // 접혀 있을 때 이 줄이 내용의 전부다. 종류가 아니라 무엇을 하는지 적는다
            // 접혀 있을 때 이 줄이 내용의 전부다. 사람이 정한 값만 진하게 남는다
            Text(
                summary.styled(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (opened) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (opened) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = scheme.outline
            )
        }

        if (opened) {
            Column(
                Modifier.padding(start = if (padded) 0.dp else 12.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()

                // 옮기고 빼는 일은 펼친 뒤에 한다
                if (onMove != null || onRemove != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        onMove?.let { move ->
                            IconButton(onClick = { move(-1) }, enabled = canMoveUp) {
                                Icon(Icons.Default.ArrowUpward, "위로", Modifier.size(18.dp))
                            }
                            IconButton(onClick = { move(1) }, enabled = canMoveDown) {
                                Icon(Icons.Default.ArrowDownward, "아래로", Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        onRemove?.let { remove ->
                            TextButton(onClick = remove) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("이 단계 빼기")
                            }
                        }
                    }
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

@Composable
private fun SectionHeading(number: Int, title: String, hint: String? = null) {
    Column {
        // 앞 칸과 눈으로 끊어 준다. 첫 칸 위에는 끊을 것이 없다
        if (number > 1) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(number)
            Spacer(Modifier.width(12.dp))
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
        QuietSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StateSwitch(onLabel: String, offLabel: String, value: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(title = if (value) onLabel else offLabel, hint = null, checked = value, onChange = onChange)
}

// ─────────────────────────── 트리거·액션 편집 ───────────────────────────

@Composable
private fun TriggerEditor(trigger: Trigger, onChange: (Trigger) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
            // 아는 것은 앱이 채운다. 손으로 적을 값은 하나만 남긴다
            BroadcastPresetRow(action) { onChange(action.withPreset(it)) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = action.extraName,
                    onValueChange = { onChange(action.copy(extraName = it)) },
                    label = { Text("추가값 이름") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                SecretField(
                    value = action.extraValue,
                    secret = isSecretExtra(action.extraName),
                    modifier = Modifier.weight(1f)
                ) { onChange(action.copy(extraValue = it)) }
            }

            // WireGuard는 이름이 한 글자만 달라도 아무 일 없이 끝난다. 만들 때 짚어 준다
            if (action.packageName == WIREGUARD_PACKAGE) {
                Text(
                    "추가값은 WireGuard에 있는 터널 이름이어야 합니다. 대소문자까지 똑같아야 하고, " +
                        "다르면 신호는 가지만 아무 일도 일어나지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 저장한 뒤 실행 기록에서 알게 되던 것을 지금 짚는다
            BroadcastBlockedNote(action)
        }
    }
}

/**
 * 자주 쓰는 브로드캐스트를 한 번에 채우는 줄.
 *
 * 지금 값과 같은 일을 하는 것은 눌린 채로 보인다. 켜기↔끄기를 바꿔도 이미 적어 둔
 * 터널 이름이나 비밀번호는 남는다 — 같은 칸을 두 번 적게 하지 않으려는 것이다.
 */
@Composable
private fun BroadcastPresetRow(action: Action.Broadcast, onPick: (BroadcastPreset) -> Unit) {
    val picked = broadcastPresets.firstOrNull { action.matches(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "아는 것부터 고르세요",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 두 줄로 나눈다. 한 줄에 네 개를 밀어 넣으면 글자가 잘린다
        broadcastPresets.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { preset ->
                    ChoiceChip(preset.label, preset === picked, Modifier.weight(1f)) { onPick(preset) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        picked?.let {
            Text(
                it.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 비밀일 수 있는 값을 적는 칸.
 *
 * AdGuard 자동화는 비밀번호를 브로드캐스트로 받는다. 그 값이 화면에 평문으로 찍히고 있었다.
 * 가려 두되 눈 버튼으로 볼 수 있게 한다 — 잘못 적었는지 확인할 길은 있어야 한다.
 */
@Composable
private fun SecretField(
    value: String,
    secret: Boolean,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    val hide = secret && !shown
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("추가값") },
        singleLine = true,
        visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (!secret) null else {
            {
                IconButton(onClick = { shown = !shown }) {
                    Icon(
                        if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (shown) "가리기" else "보기"
                    )
                }
            }
        },
        modifier = modifier
    )
}

/**
 * 이 브로드캐스트가 지금 막힐 이유를 저장 전에 알려 준다.
 *
 * 엔진이 실행할 때 쓰는 것과 같은 판정을 쓴다. 다르게 판정하면 "괜찮다"고 말한 뒤
 * 실제로는 조용히 버려지는, 가장 나쁜 종류의 거짓말이 된다.
 */
@Composable
private fun BroadcastBlockedNote(action: Action.Broadcast) {
    val context = LocalContext.current
    val reason = remember(action.packageName, action.className, action.action) {
        broadcastBlockedReason(context, action)
    } ?: return
    Text(
        reason,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

/**
 * "이럴 때만 계속" 조건을 고르고 채운다.
 *
 * 트리거가 일이 벌어진 순간을 잡고, 여기서 그 순간의 상태를 본다.
 * 둘을 합쳐야 "차에서 내렸고, 집이 아닐 때만" 같은 말이 된다.
 */
@Composable
private fun ConditionEditor(condition: Condition, onChange: (Condition) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ChoiceChip("배터리", condition is Condition.Battery, Modifier.weight(1f)) {
            if (condition !is Condition.Battery) onChange(Condition.Battery())
        }
        ChoiceChip("위치", condition is Condition.Place, Modifier.weight(1f)) {
            if (condition !is Condition.Place) onChange(Condition.Place())
        }
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
                condition.parts().styled(),
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
            if (condition.atLeast > condition.atMost) {
                Text(
                    "최소가 최대보다 큽니다. 이대로면 이 조건은 절대 맞지 않아 매크로가 늘 여기서 멈춥니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
    // 치는 대로 화면에 남긴다. 네 자리가 되기 전에는 값이 정해지지 않았을 뿐, 글자는 지워지지 않는다
    var typed by remember(minuteOfDay) { mutableStateOf(clockText(minuteOfDay)) }
    val digits = typed.filter(Char::isDigit)
    val ready = digits.length == 4

    OutlinedTextField(
        value = typed,
        onValueChange = { raw ->
            val next = raw.filter(Char::isDigit).take(4)
            typed = next
            // 숫자만 남겨 HHMM으로 읽는다. 콜론을 손으로 넣지 않아도 된다
            if (next.length == 4) {
                val h = next.take(2).toInt().coerceAtMost(23)
                val m = next.drop(2).toInt().coerceAtMost(59)
                onChange(h * 60 + m)
                typed = clockText(h * 60 + m)
            }
        },
        label = { Text(label) },
        isError = !ready,
        supportingText = {
            Text(if (ready) "시분 네 자리" else "네 자리를 다 채워야 정해집니다")
        },
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

    Text(
        if (place.latitude == 0.0 && place.longitude == 0.0) "아직 자리를 정하지 않았습니다"
        else "%.5f, %.5f".format(place.latitude, place.longitude),
        style = MonoSmall,
        color = if (place.latitude == 0.0) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutlinedTextField(
        value = place.label,
        onValueChange = { onChange(place.copy(label = it)) },
        label = { Text("자리 이름") },
        supportingText = { Text("집, 회사처럼 알아볼 이름") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

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
        "설정 → 앱 → 위치를 \u201C항상 허용\u201D으로 두어야 배경에서도 이 조건을 볼 수 있습니다.",
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
        OutlinedButton(
            onClick = { open = true },
            // 고정 높이면 글꼴을 크게 쓴 사람에게 글자가 잘린다
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("지금 떠 있는 알림에서 고르기") }
    } else {
        TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
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
    PickerSheet(title = "지금 떠 있는 알림", onClose = onClose, wide = items.size > 4) {
        if (items.isEmpty()) {
            item {
                Text(
                    "떠 있는 알림이 없거나 엔진이 꺼져 있습니다.\n지우려는 알림을 띄운 상태에서 다시 열어 보세요.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        itemsIndexed(items) { index, peek ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) {
                        onPick(peek.packageName, peek.appLabel, peek.title, peek.text, peek.clearable)
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

/**
 * 목록에서 하나를 고르는 자리.
 *
 * 다이얼로그로 만들면 380dp 창에 수백 줄을 밀어 넣게 되고, 확인 버튼 자리가 값을 적용하는
 * 버튼이 되어 「닫기」와 뜻이 겹친다. 시트는 화면 높이를 그대로 쓰고 확인 버튼이 없다.
 * 넷이 같은 껍데기를 쓰므로 어느 자리에서 열어도 같은 모양이다.
 *
 * @param wide 목록이 길 수 있는 자리. 처음부터 펼쳐 올린다
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    onClose: () -> Unit,
    wide: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    items: LazyListScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = wide)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (wide) Modifier.fillMaxHeight(0.9f) else Modifier)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            header?.invoke()
            LazyColumn(Modifier.weight(1f, fill = false)) { items() }
            footer?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                it()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 시트 안의 한 줄. 위에 사람이 읽는 이름, 아래에 기계값 */
@Composable
private fun PickerRow(primary: String, secondary: String?, warn: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            primary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!secondary.isNullOrBlank()) {
            Text(
                secondary,
                style = MonoSmall,
                color = if (warn) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 목록 밖의 선택지. 「모든 앱으로」처럼 값을 비우는 일이 확인 버튼 자리에 앉지 않게 한다 */
@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp)
    )
}

/** 고른 값을 그 자리에 보여주는 칸. 눌러야 뭐가 들었는지 아는 버튼은 두지 않는다 */
@Composable
private fun PickerField(label: String, value: String, detail: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, scheme.outline, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
    val apps by produceState(emptyList<AppEntry>(), context) {
        value = withContext(Dispatchers.IO) { installedApps(context) }
    }
    var query by remember { mutableStateOf("") }

    val shown = apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
    }
    // 흔히 쓰는 앱이 먼저 온다. 예전에는 첫 화면이 「2 Button Navigation Bar」부터였다
    val common = shown.filter { it.common }
    val rest = shown.filterNot { it.common }

    PickerSheet(
        title = "앱 고르기",
        onClose = onClose,
        wide = true,
        header = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("이름이나 패키지로 찾기") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        },
        footer = { SheetAction("모든 앱으로") { onPick("", "") } }
    ) {
        if (apps.isEmpty()) {
            item {
                Text(
                    "설치된 앱을 읽는 중…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else if (shown.isEmpty()) {
            item {
                Text(
                    "찾는 앱이 없습니다. 다른 말로 찾아 보세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
        itemsIndexed(common) { index, app ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PickerRow(app.label, app.pkg) { onPick(app.pkg, app.label) }
        }
        if (rest.isNotEmpty()) {
            item {
                Text(
                    "그 밖에",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            itemsIndexed(rest) { index, app ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PickerRow(app.label, app.pkg) { onPick(app.pkg, app.label) }
            }
        }
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
        PickerSheet(
            title = "짝지어 둔 기기",
            onClose = { open = false },
            footer = { SheetAction("모든 기기로") { onPick("", ""); open = false } }
        ) {
            if (devices.isEmpty()) {
                item {
                    Text(
                        if (hasBluetoothPermission(context))
                            "짝지어 둔 기기가 없습니다. 안드로이드 설정에서 기기와 먼저 짝을 지어 주세요."
                        else
                            "블루투스 권한이 없어 기기 목록을 읽지 못합니다. 앱 정보 → 권한에서 허용해 주세요.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            itemsIndexed(devices) { index, (name, addr) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PickerRow(name, addr) { onPick(addr, name); open = false }
            }
        }
    }
}

@Composable
private fun LocalContextCompat(): Context = androidx.compose.ui.platform.LocalContext.current

/** 목록에 세울 앱 하나. `common`은 런처에 아이콘이 있거나 사용자가 깐 앱이라는 뜻이다 */
private data class AppEntry(val label: String, val pkg: String, val common: Boolean)

/**
 * 설치된 앱을 뽑는다.
 *
 * 무필터로 뽑으면 첫 화면이 「2 Button Navigation Bar」 같은 시스템 조각부터 시작한다.
 * 그렇다고 걸러 버리면 런처 아이콘 없는 앱의 알림을 지울 수 없게 되므로, 숨기지 않고 뒤로 보낸다.
 */
private fun installedApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val launchable = runCatching {
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).mapNotNull { it.activityInfo?.packageName }.toSet()
    }.getOrDefault(emptySet())

    return pm.getInstalledApplications(0)
        .map { info ->
            val system = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            AppEntry(
                label = pm.getApplicationLabel(info).toString(),
                pkg = info.packageName,
                common = info.packageName in launchable || !system
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** 블루투스 기기 목록을 읽어도 되는지. 안드로이드 12부터 따로 승인을 받는다 */
private fun hasBluetoothPermission(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** 짝지어 둔 기기를 (이름, MAC)으로 뽑는다. 권한이 없으면 빈 목록 */
private fun bondedDevices(context: Context): List<Pair<String, String>> {
    if (!hasBluetoothPermission(context)) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching {
        adapter.bondedDevices.map { (it.name ?: it.address) to it.address }.sortedBy { it.first.lowercase() }
    }.getOrDefault(emptyList())
}
