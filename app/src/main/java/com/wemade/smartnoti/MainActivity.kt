package com.wemade.smartnoti

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // 설정에서 권한을 켜고 돌아왔을 때 화면을 다시 그리기 위한 신호
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MacroStore.load(this)
        setContent { SmartNotiTheme { AppRoot(resumeTick) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

private sealed interface Screen {
    data object List : Screen
    data class Edit(val macro: Macro) : Screen
    data object Log : Screen
}

@Composable
private fun AppRoot(resumeTick: Int) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    // 배경에서 받아둔 새 버전이 있으면 화면에 들어온 지금 이어서 설치할 수 있다
    LaunchedEffect(Unit) {
        if (Updater.state.value == null && Updater.pendingApk(context) != null) {
            Updater.state.value = UpdateState.Downloaded("")
        }
    }

    // 블루투스 트리거를 쓰려면 안드로이드 12+에서 런타임 승인이 필요하다
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    when (val current = screen) {
        is Screen.List -> MacroListScreen(
            resumeTick = resumeTick,
            onAdd = { screen = Screen.Edit(it) },
            onEdit = { screen = Screen.Edit(it) },
            onOpenLog = { screen = Screen.Log }
        )

        is Screen.Edit -> {
            BackHandler { screen = Screen.List }
            EditScreen(
                macro = current.macro,
                onSave = { MacroStore.upsert(context, it); screen = Screen.List },
                onDelete = { MacroStore.delete(context, current.macro.id); screen = Screen.List },
                onCancel = { screen = Screen.List }
            )
        }

        is Screen.Log -> {
            BackHandler { screen = Screen.List }
            RunLogScreen(onBack = { screen = Screen.List })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroListScreen(
    resumeTick: Int,
    onAdd: (Macro) -> Unit,
    onEdit: (Macro) -> Unit,
    onOpenLog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val macros by MacroStore.macros.collectAsState()
    val running by EngineState.running.collectAsState()
    val recent by RunLog.lines.collectAsState()
    val updateState by Updater.state.collectAsState()

    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }
    LaunchedEffect(resumeTick) { listenerEnabled = isListenerEnabled(context) }

    var menuOpen by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<ImportResult?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var showNewMacro by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // MacroDroid 백업 파일 읽기 — 확장자가 .mdr이라 종류를 가리지 않고 연다
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                importMdr(text)
            }
            outcome.onSuccess { result ->
                if (result.macros.isEmpty() && result.skippedMacros.isEmpty()) {
                    snackbar.showMessage("매크로가 들어 있지 않은 파일입니다")
                } else {
                    MacroStore.save(context, MacroStore.macros.value + result.macros)
                    importReport = result
                }
            }.onFailure {
                snackbar.showMessage("파일을 읽지 못했습니다. MacroDroid 백업(.mdr)이 맞는지 확인하세요")
            }
        }
    }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use {
                        it.write(exportMdr(MacroStore.macros.value))
                    }
                }
            }
            snackbar.showMessage(
                if (outcome.isSuccess) "매크로 ${MacroStore.macros.value.size}개를 내보냈습니다"
                else "파일을 저장하지 못했습니다"
            )
        }
    }

    importReport?.let { report ->
        ImportReportDialog(report) { importReport = null }
    }
    if (showUpdate) UpdateDialog { showUpdate = false }
    if (showNewMacro) NewMacroDialog(onPick = { showNewMacro = false; onAdd(it) }) { showNewMacro = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("스마트 안드로이드", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "더 보기")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("MacroDroid에서 가져오기") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = { menuOpen = false; openFile.launch(arrayOf("*/*")) }
                        )
                        DropdownMenuItem(
                            text = { Text("MacroDroid로 내보내기") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = { menuOpen = false; saveFile.launch("SmartAndroid.mdr") }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("업데이트") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = {
                                menuOpen = false
                                showUpdate = true
                                scope.launch { Updater.check(BuildConfig.VERSION_NAME) }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewMacro = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("매크로 만들기") }
            )
        }
    ) { padding ->
        // 태블릿 가로 화면에서 한 줄이 너무 길어지지 않게 읽기 좋은 폭으로 묶는다
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                EngineStrip(
                    listenerEnabled = listenerEnabled,
                    macroCount = macros.size,
                    lastLine = recent.firstOrNull(),
                    onOpenSettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onOpenLog = onOpenLog
                )
            }

            (updateState as? UpdateState.Downloaded)?.let { ready ->
                item {
                    UpdateReadyBanner(ready.version) {
                        scope.launch {
                            Updater.install(context, ready.version)
                            if (Updater.state.value == UpdateState.NeedsInstallPermission) {
                                snackbar.showMessage("앱 설치 권한이 필요합니다. 메뉴 → 업데이트에서 켜 주세요")
                            }
                        }
                    }
                }
            }

            items(macros, key = { it.id }) { macro ->
                MacroCard(
                    macro = macro,
                    running = macro.id in running,
                    engineReady = listenerEnabled,
                    onToggle = { MacroStore.upsert(context, macro.copy(enabled = it)) },
                    onRunNow = {
                        val service = MacroService.instance
                        if (service == null) {
                            scope.launch { snackbar.showMessage("엔진이 꺼져 있습니다. 알림 접근 권한을 켜세요") }
                        } else {
                            scope.launch {
                                // 강제 실행은 대기를 건너뛰므로 곧 끝난다. 끝나면 결과를 그 자리에서 보여준다
                                service.runNow(macro)?.join()
                                val last = RunLog.lines.value.firstOrNull()?.substringAfter("  ")
                                snackbar.showMessage(last ?: "${macro.name} 실행함")
                            }
                        }
                    },
                    onClick = { onEdit(macro) }
                )
            }

            if (macros.isEmpty()) {
                item { EmptyState() }
            }
        }
        }
    }
}

/** 엔진이 살아 있는지, 방금 무슨 일이 있었는지. 이 앱에서 가장 자주 확인하게 되는 줄이다 */
@Composable
private fun EngineStrip(
    listenerEnabled: Boolean,
    macroCount: Int,
    lastLine: String?,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit
) {
    val connected by EngineState.connected.collectAsState()
    val live = listenerEnabled && connected

    Card(
        onClick = if (listenerEnabled) onOpenLog else onOpenSettings,
        colors = CardDefaults.cardColors(
            containerColor = if (listenerEnabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(live)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !listenerEnabled -> "알림 접근 권한이 꺼져 있습니다"
                        !connected -> "엔진 연결 중"
                        else -> "켜져 있음"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (listenerEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "매크로 ${macroCount}개",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.padding(top = 6.dp))

            if (!listenerEnabled) {
                Text(
                    "권한을 켜야 알림을 읽고 지울 수 있습니다. 눌러서 설정으로 갑니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                Text(
                    lastLine ?: "아직 실행된 매크로가 없습니다",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 살아 있으면 천천히 숨쉬는 점 */
@Composable
private fun StatusDot(live: Boolean) {
    val alpha by if (live) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "breathe"
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Box(
        Modifier
            .size(9.dp)
            .alpha(if (live) alpha else 1f)
            .background(
                if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                CircleShape
            )
    )
}

@Composable
private fun MacroCard(
    macro: Macro,
    running: Boolean,
    engineReady: Boolean,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onClick: () -> Unit
) {
    val dim = !macro.enabled
    val scheme = MaterialTheme.colorScheme

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = scheme.surface)
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    macro.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dim) scheme.onSurfaceVariant else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRunNow, enabled = engineReady) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "지금 실행",
                        tint = if (engineReady) scheme.primary else scheme.onSurfaceVariant
                    )
                }
                Switch(checked = macro.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.padding(top = 2.dp))

            // 알림 지우기는 단계를 펼쳐 봐야 알 게 없다. 한 줄로 접어 목록을 가볍게 둔다
            val rule = macro.asClearRule()
            Box(Modifier.alpha(if (dim) 0.45f else 1f)) {
                if (rule != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KindTag("알림 지우기")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            rule.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    MacroRail(
                        macro = macro,
                        running = running,
                        lineColor = scheme.outline,
                        triggerColor = scheme.primary,
                        waitColor = scheme.secondary,
                        actColor = scheme.onSurfaceVariant
                    ) { _, text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** 매크로가 어떤 종류인지 다는 작은 꼬리표 */
@Composable
private fun KindTag(label: String) {
    Text(
        label,
        style = MonoSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 무엇을 만들지 먼저 고르게 한다. 대부분은 알림 지우기 하나면 끝난다 */
@Composable
private fun NewMacroDialog(onPick: (Macro) -> Unit, onClose: () -> Unit) {
    val id = System.currentTimeMillis()
    val choices = listOf(
        Triple(
            "알림 지우기", "성가신 알림을 정해둔 시간 뒤에 자동으로 지웁니다",
            Macro(
                id = id, name = "알림 지우기",
                trigger = Trigger.Notification(),
                actions = listOf(Action.ClearNotification())
            )
        ),
        Triple(
            "직접 짜기", "알림·블루투스·와이파이를 조건으로 단계를 엮습니다",
            Macro(id = id, name = "새 매크로", trigger = Trigger.Notification())
        )
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("무엇을 만들까요?") },
        text = {
            Column {
                choices.forEachIndexed { i, (label, hint, macro) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { onPick(macro) }
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
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } }
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("아직 매크로가 없습니다", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(top = 6.dp))
        Text(
            "아래 버튼으로 만들거나,\n위 메뉴에서 MacroDroid 백업을 가져오세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 배경에서 받아둔 새 버전을 알린다. 설치 확인 화면은 앱이 화면에 있을 때만 뜰 수 있다 */
@Composable
private fun UpdateReadyBanner(version: String, onInstall: () -> Unit) {
    Card(
        onClick = onInstall,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (version.isBlank()) "새 버전을 받아 뒀습니다" else "새 버전 $version 을 받아 뒀습니다",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "눌러서 설치합니다. 처음 한 번은 확인 화면이 뜹니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** 지금 버전이 무엇이고, 새것이 있는지, 자동으로 받을지를 한 화면에서 다룬다 */
@Composable
private fun UpdateDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    var auto by remember { mutableStateOf(Updater.isAutoEnabled(context)) }

    val available = state as? UpdateState.Available

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("업데이트") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 손대는 것이 위, 알려주는 것이 아래
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("스스로 최신으로 유지", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "하루 한 번 확인해 새 버전을 받아 깝니다. 처음 한 번은 설치 확인 화면이 뜹니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = auto,
                        onCheckedChange = { auto = it; Updater.setAutoEnabled(context, it) }
                    )
                }

                HorizontalDivider()

                Row {
                    Text("지금 버전", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(BuildConfig.VERSION_NAME, style = MonoLabel)
                }

                Text(
                    when (val s = state) {
                        null -> "아직 확인해 보지 않았습니다."
                        UpdateState.Checking -> "확인하는 중…"
                        UpdateState.UpToDate -> "최신 버전입니다."
                        is UpdateState.Failed -> s.message
                        UpdateState.NeedsInstallPermission ->
                            "이 앱이 앱을 설치할 수 있게 허용해야 합니다. 아래 버튼으로 설정에서 켜 주세요."
                        is UpdateState.Available -> "새 버전 ${s.version}이 나왔습니다."
                        is UpdateState.Downloading -> "내려받는 중… ${s.percent}%"
                        is UpdateState.Downloaded ->
                            if (s.version.isBlank()) "받아 뒀습니다. 설치만 남았습니다."
                            else "${s.version}을 받아 뒀습니다. 설치만 남았습니다."
                        is UpdateState.Installing -> "설치하는 중…"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

            }
        },
        confirmButton = {
            val downloaded = state as? UpdateState.Downloaded
            when {
                state == UpdateState.NeedsInstallPermission -> TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("설정 열기") }

                downloaded != null -> TextButton(onClick = {
                    scope.launch { Updater.install(context, downloaded.version) }
                }) { Text("설치") }

                // 받기를 누르면 설치까지 이어간다. 두 번 누를 이유가 없다
                available?.apkUrl != null -> TextButton(onClick = {
                    scope.launch {
                        Updater.download(context, available.version, available.apkUrl)
                        if (Updater.state.value is UpdateState.Downloaded) {
                            Updater.install(context, available.version)
                        }
                    }
                }) { Text("받아서 설치") }

                else -> TextButton(onClick = {
                    scope.launch { Updater.check(BuildConfig.VERSION_NAME) }
                }) { Text("지금 확인") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("닫기") } }
    )
}

/** 가져오기 결과 — 몇 개를 옮겼고 무엇을 못 옮겼는지 숨기지 않는다 */
@Composable
private fun ImportReportDialog(result: ImportResult, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("매크로 ${result.macros.size}개를 가져왔습니다") },
        text = {
            Column {
                if (result.skippedMacros.isEmpty() && result.partialMacros.isEmpty()) {
                    Text("빠짐없이 옮겼습니다.")
                } else {
                    if (result.skippedMacros.isNotEmpty()) {
                        Text("이 앱이 모르는 트리거라 건너뜀", style = MaterialTheme.typography.titleSmall)
                        result.skippedMacros.forEach {
                            Text("· $it", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.padding(top = 8.dp))
                    }
                    if (result.partialMacros.isNotEmpty()) {
                        Text("일부만 옮김", style = MaterialTheme.typography.titleSmall)
                        result.partialMacros.forEach {
                            Text("· $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("확인") } }
    )
}

/** 매크로가 실제로 돌았는지 확인하는 화면 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunLogScreen(onBack: () -> Unit) {
    val lines by RunLog.lines.collectAsState()
    val peek by Diagnostics.peekNotifications.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("실행 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 매크로가 왜 안 걸리는지 알려면 알림의 진짜 문구를 봐야 한다
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("들어오는 알림 엿보기", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "모든 알림의 앱과 문구를 여기에 남깁니다. 매크로 문구를 맞출 때만 켜세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = peek,
                        onCheckedChange = { Diagnostics.peekNotifications.value = it }
                    )
                }
            }

        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "아직 기록이 없습니다.\n매크로가 실행되면 여기에 쌓입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(lines) { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(
                            line.substringBefore("  "),
                            style = MonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            line.substringAfter("  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        }
    }
}

private suspend fun SnackbarHostState.showMessage(text: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(text)
}

/** 알림 접근 권한이 켜져 있는지 — 시스템 설정 문자열에 이 앱이 들어있는지로 판단한다 */
private fun isListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any { it.substringBefore("/") == context.packageName }
}
