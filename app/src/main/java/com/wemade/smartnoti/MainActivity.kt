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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // 설정에서 권한을 켜고 돌아왔을 때 화면을 다시 그리기 위한 신호
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RunLog.attach(this)
        Diagnostics.load(this)
        ThemeState.load(this)
        MacroStore.load(this)
        MacroHistory.load(this)
        PendingWaits.load(this)
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

/**
 * 화면을 돌리면 액티비티가 다시 만들어진다.
 * 그때 목록으로 튕기지 않게, 지금 보던 화면을 문자열 한 줄로 접어 두었다 편다.
 */
/** 고른 매크로 id 묶음을 화면 밖으로 넘긴다. 번들은 목록만 담을 수 있어 한 번 펴서 보낸다 */
private val LongSetSaver = listSaver<Set<Long>, Long>(
    save = { it.toList() },
    restore = { it.toSet() }
)

private val ScreenSaver = Saver<Screen, String>(
    save = {
        when (it) {
            Screen.List -> "L"
            Screen.Log -> "G"
            is Screen.Edit -> "E" + macroJson.encodeToString(it.macro)
        }
    },
    restore = {
        when {
            it == "L" -> Screen.List
            it == "G" -> Screen.Log
            else -> runCatching { Screen.Edit(macroJson.decodeFromString<Macro>(it.drop(1))) }.getOrNull()
        }
    }
)

@Composable
private fun AppRoot(resumeTick: Int) {
    val context = LocalContext.current
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.List) }

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

    /**
     * WireGuard 터널을 켜고 끄는 매크로가 있을 때만 그 권한을 묻는다.
     *
     * WireGuard가 리시버에 자기 권한을 걸어 두어서, 없으면 브로드캐스트가 아무 말 없이 버려진다.
     * 쓰지도 않는 사람에게 미리 물을 일은 아니라 매크로가 생긴 뒤에 묻는다.
     */
    val macrosForPermission by MacroStore.macros.collectAsState()
    val askWireGuard = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(macrosForPermission) {
        val usesWireGuard = macrosForPermission.any { macro ->
            macro.actions.any { it is Action.Broadcast && it.packageName == WIREGUARD_PACKAGE }
        }
        if (usesWireGuard &&
            context.checkSelfPermission(WIREGUARD_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { askWireGuard.launch(WIREGUARD_PERMISSION) }
                .onFailure { RunLog.add("WireGuard 권한을 묻지 못했습니다 · 그 앱이 깔려 있는지 확인하세요") }
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
    val engineConnected by EngineState.connected.collectAsState()
    val waiting by PendingWaits.waiting.collectAsState()
    val history by MacroHistory.entries.collectAsState()
    val updateState by Updater.state.collectAsState()

    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }
    LaunchedEffect(resumeTick) { listenerEnabled = isListenerEnabled(context) }

    /**
     * 목록의 차례는 화면에 들어올 때 한 번만 정한다.
     *
     * 꺼둔 매크로를 아래로 내리는 것은 목록을 열었을 때 도는 것부터 보이라는 뜻이지,
     * 스위치를 누를 때마다 카드가 발밑에서 자리를 옮기라는 뜻이 아니다.
     * 바로 다시 정렬하면 방금 누른 카드가 사라지고 다른 카드가 그 자리로 올라와,
     * 한 번 더 누르면 엉뚱한 매크로가 꺼진다.
     */
    val order = remember(resumeTick) {
        MacroStore.macros.value.sortedByDescending { it.enabled }.map { it.id }
    }

    var menuOpen by remember { mutableStateOf(false) }
    val themeMode by ThemeState.mode.collectAsState()

    // 블루투스 매크로를 쓰는데 권한이 없으면 조용히 안 걸린다. 그 조합만 짚어 둔다
    val needsBluetooth = macros.any { macro ->
        macro.enabled && macro.allTriggers().any { it is Trigger.Bluetooth }
    }
    val hasBluetooth = remember(resumeTick) {
        Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
    var importReport by remember { mutableStateOf<ImportResult?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var showNewMacro by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Macro?>(null) }
    var deleting by remember { mutableStateOf<Macro?>(null) }
    // 여러 개를 체크해 둔 채로 회전하면 통째로 날아갔다. 매크로 자체가 아니라 id만 붙잡아 둔다
    var movingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // 접어 둔 폴더는 앱을 껐다 켜도 접힌 채로 있어야 한다
    val expanded = remember { mutableStateListOf<String>().apply { addAll(FolderState.expanded(context)) } }
    val snackbar = remember { SnackbarHostState() }

    // 백업 파일 읽기 — 이 앱의 .json과 예전 .mdr을 모두 받아서 종류를 가리지 않고 연다
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                importBackup(text)
            }
            outcome.onSuccess { result ->
                // 읽기만 하고 아직 덮어쓰지 않는다. 무엇이 사라지는지 보여주고 확인을 받는다
                if (result.macros.isEmpty() && result.skippedMacros.isEmpty()) {
                    snackbar.showMessage("매크로가 들어 있지 않은 파일입니다. 다른 파일을 골라 주세요")
                } else {
                    importReport = result
                }
            }.onFailure {
                snackbar.showMessage("파일을 읽지 못했습니다. 이 앱에서 내보낸 백업이 맞는지 확인하세요")
            }
        }
    }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use {
                        it.write(exportBackup(MacroStore.macros.value))
                    }
                }
            }
            snackbar.showMessage(
                if (outcome.isSuccess) "매크로 ${MacroStore.macros.value.size}개를 백업했습니다"
                else "파일을 저장하지 못했습니다. 다른 폴더를 골라 다시 해 보세요"
            )
        }
    }

    importReport?.let { report ->
        ImportConfirmDialog(
            result = report,
            current = macros.size,
            onConfirm = {
                MacroStore.save(context, report.macros)
                importReport = null
                scope.launch { snackbar.showMessage("매크로 ${report.macros.size}개를 불러왔습니다") }
            },
            onClose = { importReport = null }
        )
    }
    if (showUpdate) UpdateDialog { showUpdate = false }

    renaming?.let { target ->
        TextPrompt(
            title = "이름 바꾸기",
            hint = "목록에서 이 이름으로 찾습니다",
            initial = target.name,
            onDone = { name ->
                if (name.isNotBlank()) MacroStore.upsert(context, target.copy(name = name))
                renaming = null
            },
            onClose = { renaming = null }
        )
    }

    movingId?.let { id ->
        val target = macros.firstOrNull { it.id == id }
        if (target == null) {
            // 다이얼로그가 떠 있는 사이 그 매크로가 사라졌다
            movingId = null
            return@let
        }
        FolderPickDialog(
            target = target,
            others = macros.filter { it.id != target.id }.inOrder(order),
            existing = macros.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted(),
            onApply = { folder, ids ->
                MacroStore.moveToFolder(context, ids, folder)
                // 넣은 폴더는 펼쳐 둔다. 접힌 채로 두면 방금 옮긴 것이 사라진 것처럼 보인다
                if (folder.isNotBlank() && folder !in expanded) {
                    expanded.add(folder)
                    FolderState.setExpanded(context, folder, true)
                }
                movingId = null
                scope.launch {
                    snackbar.showMessage(
                        if (folder.isBlank()) "${ids.size}개를 폴더에서 뺐습니다"
                        else "${ids.size}개를 \u201C$folder\u201D 폴더로 옮겼습니다"
                    )
                }
            },
            onClose = { movingId = null }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("이 매크로를 지울까요?") },
            text = { Text("\u201C${target.name}\u201D이 사라집니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    MacroStore.delete(context, target.id)
                    MacroHistory.forget(context, target.id)
                    deleting = null
                }) {
                    Text("지우기", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }
        )
    }
    if (showNewMacro) NewMacroDialog(onPick = { showNewMacro = false; onAdd(it) }) { showNewMacro = false }

    // 목록을 올려도 앱바는 남는다. 대신 스크롤이 시작되면 색이 한 겹 올라와 경계가 생긴다
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
          Column {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text("스마트 안드로이드", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "더 보기")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("백업하기") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = { menuOpen = false; saveFile.launch(backupFileName()) }
                        )
                        DropdownMenuItem(
                            text = { Text("백업 불러오기") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = { menuOpen = false; openFile.launch(arrayOf("*/*")) }
                        )
                        HorizontalDivider()
                        // 엔진이 도는지는 여기서 본다. 상태를 말하는 것은 아래 글이고 점은 그 글을 거든다
                        DropdownMenuItem(
                            text = { Text("실행 기록") },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                            onClick = { menuOpen = false; onOpenLog() }
                        )
                        // 메뉴는 닫지 않는다. 누른 자리에서 화면 색이 바뀌는 것을 보고 고르게 한다
                        DropdownMenuItem(
                            text = { Text("화면 · ${themeMode.label}") },
                            leadingIcon = {
                                Icon(
                                    when (themeMode) {
                                        ThemeMode.System -> Icons.Default.BrightnessAuto
                                        ThemeMode.Light -> Icons.Default.LightMode
                                        ThemeMode.Dark -> Icons.Default.DarkMode
                                    },
                                    null
                                )
                            },
                            onClick = { ThemeState.cycle(context) }
                        )
                        HorizontalDivider()
                        // 자주 쓰는 것이 위, 어쩌다 쓰는 것이 아래
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

            // 권한이 없으면 이 앱은 아무것도 못 한다. 그때만 목록 위에 남는다
            if (!listenerEnabled) {
                TopWarning(
                    title = "알림 접근 권한이 꺼져 있습니다",
                    body = "권한을 켜야 알림을 읽고 지울 수 있습니다. 눌러서 설정으로 갑니다."
                ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            if (listenerEnabled) {
                // 잘 돌 때도 이만큼은 보인다. 완전한 침묵이 이 앱의 실패 방식이었다
                EngineLine(
                    connected = engineConnected,
                    macroCount = macros.size,
                    lastRunAt = history.values.maxByOrNull { it.at }?.at,
                    onOpenLog = onOpenLog
                )
            }
            if (listenerEnabled && needsBluetooth && !hasBluetooth) {
                // 블루투스 트리거는 이 권한 없이는 아무 말 없이 안 걸린다. 그래서 눈에 걸리게 둔다
                TopWarning(
                    title = "근처 기기 권한이 꺼져 있습니다",
                    body = "블루투스로 켜고 끄는 매크로가 걸리지 않습니다. 눌러서 권한 화면으로 갑니다."
                ) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            }
          }
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

            // 폴더에 넣지 않은 것이 먼저, 그 아래 폴더가 이름순으로 온다
            val byFolder = macros.groupBy { it.folder }
            val loose = byFolder[""].orEmpty()
            val folders = byFolder.filterKeys { it.isNotBlank() }.toSortedMap()

            fun handlers() = MacroActions(
                running = running,
                engineReady = listenerEnabled,
                waiting = waiting,
                history = history,
                onOpenLog = onOpenLog,
                onToggle = { macro, on -> MacroStore.upsert(context, macro.copy(enabled = on)) },
                onRunNow = { macro ->
                    val service = MacroService.instance
                    if (service == null) {
                        scope.launch { snackbar.showMessage("엔진이 꺼져 있습니다. 알림 접근 권한을 켜세요") }
                    } else {
                        scope.launch {
                            // 강제 실행은 대기를 건너뛰므로 곧 끝난다. 끝나면 결과를 그 자리에서 보여준다
                            service.runNow(macro)?.join()
                            val last = RunLog.lines.value.firstOrNull()?.substringAfter("  ")
                            snackbar.showMessage(last ?: "${macro.name} · 실행했습니다")
                        }
                    }
                },
                onEdit = onEdit,
                onRename = { renaming = it },
                onDuplicate = { macro ->
                    val copy = macro.duplicate(System.currentTimeMillis())
                    MacroStore.upsert(context, copy)
                    scope.launch { snackbar.showMessage("${copy.name} · 꺼 둔 채로 만들었습니다") }
                },
                onMove = { movingId = it.id },
                onDelete = { deleting = it }
            )

            macroItems(loose.inOrder(order), handlers())

            folders.forEach { (folder, inside) ->
                val isCollapsed = folder !in expanded
                item(key = "folder:$folder") {
                    FolderHeader(
                        name = folder,
                        count = inside.size,
                        collapsed = isCollapsed
                    ) {
                        if (isCollapsed) expanded.add(folder) else expanded.remove(folder)
                        FolderState.setExpanded(context, folder, isCollapsed)
                    }
                }
                if (!isCollapsed) macroItems(inside.inOrder(order), handlers())
            }

            if (macros.isEmpty()) {
                item { EmptyState() }
            }
        }
        }
    }
}

/**
 * 카드 맨 아래 한 줄 — 이 매크로가 마지막으로 무엇을 했는지.
 *
 * 색은 이 앱의 규칙을 그대로 따른다. 앰버는 시간이므로 기다리는 중이 앰버,
 * 빨강은 잘못된 일이므로 실패가 빨강, 나머지는 조용한 회색이다.
 * 눌러 실행 기록으로 갈 수 있게 둔다 — 궁금해지는 자리가 바로 여기다.
 */
@Composable
private fun MacroStatusLine(dueAt: Long?, last: MacroHistory.Entry?, onOpenLog: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // 분이 바뀌면 「21:35」가 「어제 21:35」로 넘어가야 한다. 화면에 들어올 때마다 다시 센다
    val now = remember(dueAt, last) { System.currentTimeMillis() }
    val status = statusLine(now, dueAt, last?.at, last?.outcome)
    val color = when {
        dueAt != null -> scheme.secondary
        last?.outcome == MacroHistory.Outcome.Failed -> scheme.error
        else -> scheme.onSurfaceVariant
    }
    val body = MaterialTheme.typography.bodySmall
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "실행 기록 열기", onClick = onOpenLog)
            // 읽어 주는 기계에는 한 문장으로 넘긴다. 토막이 따로 읽히면 뜻이 흩어진다
            .clearAndSetSemantics { contentDescription = status.plain }
            .padding(top = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 시각만 고정폭이다. 앞뒤로 붙는 말은 사람이 읽는 글이라 시스템 글꼴을 쓴다
        if (status.lead.isNotBlank()) {
            Text(status.lead, style = body, color = color)
            Spacer(Modifier.width(5.dp))
        }
        if (status.stamp.isNotBlank()) {
            Text(status.stamp, style = MonoSmall, color = color)
            Spacer(Modifier.width(7.dp))
        }
        Text(status.tail, style = body, color = color)
    }
}

/** 카드 한 장이 부르는 일들. 목록과 폴더가 같은 묶음을 나눠 쓴다 */
private data class MacroActions(
    val running: Set<Long>,
    val engineReady: Boolean,
    val waiting: Map<Long, Long>,
    val history: Map<Long, MacroHistory.Entry>,
    val onOpenLog: () -> Unit,
    val onToggle: (Macro, Boolean) -> Unit,
    val onRunNow: (Macro) -> Unit,
    val onEdit: (Macro) -> Unit,
    val onRename: (Macro) -> Unit,
    val onDuplicate: (Macro) -> Unit,
    val onMove: (Macro) -> Unit,
    val onDelete: (Macro) -> Unit
)

/** 화면에 들어올 때 정해 둔 차례로 줄 세운다. 그 사이 새로 생긴 것은 맨 뒤에 붙는다 */
private fun List<Macro>.inOrder(order: List<Long>): List<Macro> {
    val rank = order.withIndex().associate { (at, id) -> id to at }
    return sortedBy { rank[it.id] ?: Int.MAX_VALUE }
}

/** 매크로 여러 장을 목록에 붙인다 */
private fun LazyListScope.macroItems(list: List<Macro>, actions: MacroActions) {
    items(list, key = { it.id }) { macro ->
        MacroCard(
            macro = macro,
            running = macro.id in actions.running,
            engineReady = actions.engineReady,
            dueAt = actions.waiting[macro.id],
            last = actions.history[macro.id],
            onOpenLog = actions.onOpenLog,
            onToggle = { actions.onToggle(macro, it) },
            onRunNow = { actions.onRunNow(macro) },
            onClick = { actions.onEdit(macro) },
            onRename = { actions.onRename(macro) },
            onDuplicate = { actions.onDuplicate(macro) },
            onMove = { actions.onMove(macro) },
            onDelete = { actions.onDelete(macro) }
        )
    }
}

/**
 * 폴더 한 줄.
 *
 * 매크로가 늘면 목록이 길어져 무엇이 어디 있는지 알기 어렵다. 이름을 눌러 접었다 편다.
 * 카드가 아니라 줄로 그린다 — 폴더는 내용이 아니라 내용을 담는 자리다.
 */
@Composable
private fun FolderHeader(name: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = if (collapsed) "펼치기" else "접기",
                onClick = onToggle
            )
            .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Text("${count}개", style = MonoSmall, color = scheme.onSurfaceVariant)
    }
}

/**
 * 목록 위 엔진 한 줄.
 *
 * 한때 이 자리를 비워 두고 상태를 메뉴 안에 넣었다. 잘 돌 때 자리를 아끼자는 판단이었는데,
 * 실제 실패는 「다 정상으로 보이는데 안 돈」 경우였고 그때 사람은 메뉴를 열 이유를 모른다.
 * 그래서 돌려놓되 놀라게 하지 않을 만큼만 조용히 둔다.
 */
@Composable
private fun EngineLine(connected: Boolean, macroCount: Int, lastRunAt: Long?, onOpenLog: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = remember(lastRunAt) { System.currentTimeMillis() }
    val text = buildString {
        append(if (connected) "켜져 있음" else "엔진 연결 중")
        append(" · 매크로 ").append(macroCount).append("개")
        if (lastRunAt != null) {
            val last = statusLine(now, null, lastRunAt, MacroHistory.Outcome.Ran)
            append(" · 마지막 ").append(listOf(last.lead, last.stamp).filter { it.isNotBlank() }.joinToString(" "))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .clickable(role = Role.Button, onClickLabel = "실행 기록 열기", onClick = onOpenLog)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(connected)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = scheme.outlineVariant)
}

/**
 * 매크로가 돌 수 없는 상태라고 알리는 띠.
 *
 * 엔진이 도는지는 메뉴에서 본다. 잘 돌 때는 목록 위 자리를 비워 두는 것이 낫다.
 * 다만 권한이 없으면 켜 둔 매크로가 조용히 안 걸리므로, 그때는 목록보다 먼저 눈에 걸려야 한다.
 */
@Composable
private fun TopWarning(title: String, body: String, onOpenSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.errorContainer)
            .clickable(role = Role.Button, onClickLabel = "설정 열기", onClick = onOpenSettings)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(false)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onErrorContainer)
        }
        Spacer(Modifier.padding(top = 3.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer)
    }
    HorizontalDivider(color = scheme.outlineVariant)
}

/** 살아 있으면 천천히 숨쉬는 점. 상태를 말하는 것은 옆의 글이고, 이 점은 그 글을 거드는 그림이다 */
@Composable
private fun StatusDot(live: Boolean, size: Dp = 9.dp) {
    val breathing = live && !reduceMotion()
    val alpha by if (breathing) {
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
            .size(size)
            .clearAndSetSemantics { }
            .alpha(if (breathing) alpha else 1f)
            .background(
                if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                CircleShape
            )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MacroCard(
    macro: Macro,
    running: Boolean,
    engineReady: Boolean,
    dueAt: Long?,
    last: MacroHistory.Entry?,
    onOpenLog: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val dim = !macro.enabled
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            // 길게 누르면 이 매크로를 두고 할 수 있는 일이 한자리에 나온다
            modifier = Modifier.combinedClickable(
                onClickLabel = "수정하기",
                onLongClickLabel = "이름 바꾸기·지우기 메뉴 열기",
                onClick = onClick,
                onLongClick = { menuOpen = true }
            )
        ) {
            Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        macro.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dim) scheme.onSurfaceVariant else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // 48dp 미만이면 스위치와 붙어 오탭한다. 이건 눌리면 즉시 브로드캐스트가 나간다
                    IconButton(onClick = onRunNow, enabled = engineReady, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "지금 실행",
                            modifier = Modifier.size(20.dp),
                            tint = if (engineReady) scheme.primary else scheme.onSurfaceVariant
                        )
                    }
                    QuietSwitch(checked = macro.enabled, onCheckedChange = onToggle)
                }

                Spacer(Modifier.padding(top = 5.dp))

                // 알림 지우기는 단계를 펼쳐 봐야 알 게 없다. 한 줄로 접어 목록을 가볍게 둔다
                val rule = macro.asClearRule()
                Box(Modifier.alpha(if (dim) 0.45f else 1f)) {
                    if (rule != null) {
                        // 단계를 펼쳐도 알 게 없는 모양이라 한 칸으로 접는다. 표식은 그대로 트리거다
                        RailRow(
                            step = Step.Trigger,
                            isFirst = true,
                            isLast = true,
                            lineColor = scheme.outline,
                            markerColor = scheme.primary
                        ) {
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

                // 구성만 보여 주면 "무장됐나"에 답을 못 한다. 마지막으로 무엇을 했는지 한 줄로 남긴다
                Spacer(Modifier.padding(top = 7.dp))
                MacroStatusLine(dueAt = dueAt, last = last, onOpenLog = onOpenLog)
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("지금 실행") },
                enabled = engineReady,
                onClick = { menuOpen = false; onRunNow() }
            )
            DropdownMenuItem(
                text = { Text("이름 바꾸기") },
                onClick = { menuOpen = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text("수정하기") },
                onClick = { menuOpen = false; onClick() }
            )
            DropdownMenuItem(
                text = { Text("복제하기") },
                onClick = { menuOpen = false; onDuplicate() }
            )
            DropdownMenuItem(
                text = { Text(if (macro.folder.isBlank()) "폴더에 넣기" else "폴더 옮기기") },
                onClick = { menuOpen = false; onMove() }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DropdownMenuItem(
                text = { Text("지우기", color = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() }
            )
        }
    }
}

/**
 * 어느 폴더에 둘지 고르고, 같이 옮길 매크로를 더 고른다.
 *
 * 폴더를 따로 관리하는 화면은 두지 않는다. 이름을 적으면 그때 생기고, 마지막 매크로가 빠지면 사라진다.
 * 폴더를 만드는 이유는 대개 여러 개를 한군데 모으려는 것이라, 옮길 것을 이 자리에서 함께 고르게 한다.
 */
@Composable
private fun FolderPickDialog(
    target: Macro,
    others: List<Macro>,
    existing: List<String>,
    onApply: (String, Set<Long>) -> Unit,
    onClose: () -> Unit
) {
    var naming by remember { mutableStateOf(false) }
    // 회전하면 고른 폴더와 체크가 통째로 날아갔다. 사람이 한 결정이므로 붙잡아 둔다
    var picked by rememberSaveable { mutableStateOf(target.folder) }
    var also by rememberSaveable(stateSaver = LongSetSaver) { mutableStateOf(emptySet<Long>()) }

    if (naming) {
        TextPrompt(
            title = "새 폴더 이름",
            hint = "목록에서 이 이름으로 묶입니다",
            initial = "",
            confirmLabel = "만들기",
            onDone = { name ->
                if (name.isNotBlank()) picked = name.trim()
                naming = false
            },
            onClose = { naming = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("어느 폴더에 둘까요?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FolderChoice("폴더에 넣지 않음", picked.isBlank()) { picked = "" }
                // 새로 적은 이름은 아직 목록에 없다. 그것도 골라진 것으로 보여야 한다
                val names = (existing + picked).filter { it.isNotBlank() }.distinct().sorted()
                names.forEach { folder ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FolderChoice(folder, folder == picked) { picked = folder }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FolderChoice("새 폴더 만들기…", false) { naming = true }

                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "\u201C${target.name}\u201D${target.name.andParticle()} 함께 옮길 것",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    others.forEach { macro ->
                        MacroCheck(macro, macro.id in also) { on ->
                            also = if (on) also + macro.id else also - macro.id
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(picked, also + target.id) }) {
                Text(if (also.isEmpty()) "넣기" else "${also.size + 1}개 넣기")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } }
    )
}

/** 같이 옮길 매크로 한 줄. 지금 어느 폴더에 있는지 아래에 붙여 둔다 */
@Composable
private fun MacroCheck(macro: Macro, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                macro.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (macro.folder.isNotBlank()) {
                Text(
                    macro.folder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp)
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
                triggers = listOf(Trigger.Notification()),
                // 0초면 알림이 뜨자마자 사라져 눈으로 볼 새가 없다
                actions = listOf(Action.Delay(5), Action.ClearNotification())
            )
        ),
        Triple(
            "직접 짜기", "알림·블루투스·와이파이를 조건으로 단계를 엮습니다",
            Macro(id = id, name = "새 매크로", triggers = listOf(Trigger.Notification()))
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
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onPick(macro) }
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

/**
 * 비어 있는 목록.
 *
 * 글로 "매크로를 만드세요"라고 적는 대신, 매크로가 어떻게 생긴 것인지를 레일로 보여준다.
 * 목록에서도 편집에서도 쓰는 그 표식이라, 이 화면이 곧 사용법이 된다.
 */
@Composable
private fun EmptyState() {
    val scheme = MaterialTheme.colorScheme
    val sample = listOf(
        Step.Trigger to "알림이 뜨면",
        Step.Wait to "잠깐 기다렸다가",
        Step.Act to "그 알림을 지웁니다"
    )

    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("아직 매크로가 없습니다", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.padding(top = 20.dp))

        Column(Modifier.alpha(0.5f)) {
            sample.forEachIndexed { index, (step, text) ->
                RailRow(
                    step = step,
                    isFirst = index == 0,
                    isLast = index == sample.lastIndex,
                    lineColor = scheme.outline,
                    markerColor = when (step) {
                        Step.Trigger -> scheme.primary
                        Step.Wait -> scheme.secondary
                        else -> scheme.onSurfaceVariant
                    }
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.padding(top = 20.dp))

        Text(
            "이런 것을 만듭니다. 아래 버튼으로 시작하거나, 위 메뉴에서 백업을 불러오세요.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("스스로 최신으로 유지", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "하루 한 번 확인해 새 버전을 받아 깝니다. 처음 한 번은 설치 확인 화면이 뜹니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    QuietSwitch(checked = auto) { auto = it; Updater.setAutoEnabled(context, it) }
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

/** 백업 파일 이름 — 언제 받은 것인지 파일 목록에서 바로 보이게 날짜를 붙인다 */
private fun backupFileName(): String =
    "SmartAndroid-" + java.time.LocalDate.now() + ".json"

/**
 * 불러오기는 지금 있는 것을 통째로 바꾼다.
 * 되돌릴 수 없으니 무엇이 사라지고 무엇이 들어오는지 먼저 보여준다.
 */
@Composable
private fun ImportConfirmDialog(
    result: ImportResult,
    current: Int,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("백업을 불러올까요?") },
        text = {
            Column {
                Text("지금 매크로 ${current}개가 사라지고, 파일의 ${result.macros.size}개로 바뀝니다.")

                // MacroDroid 백업을 읽었을 때만 나온다. 이 앱 백업은 빠지는 것이 없다
                if (result.skippedMacros.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 8.dp))
                    Text("이 앱이 모르는 트리거라 건너뜀", style = MaterialTheme.typography.titleSmall)
                    result.skippedMacros.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (result.partialMacros.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 8.dp))
                    Text("일부만 옮김", style = MaterialTheme.typography.titleSmall)
                    result.partialMacros.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("불러오기") } },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } }
    )
}

/** 매크로가 실제로 돌았는지 확인하는 화면 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lines by RunLog.lines.collectAsState()
    val today = remember {
        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))
    }
    val peek by Diagnostics.peekNotifications.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
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
                Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("들어오는 알림 엿보기", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "모든 알림의 앱과 문구를 여기에 남깁니다. 매크로 문구를 맞출 때만 켜세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    QuietSwitch(checked = peek) { Diagnostics.setPeek(context, it) }
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
                itemsIndexed(lines) { index, line ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth()
                            // 눈으로는 ▶ ■ 가 빠르지만 읽어 주는 기계는 그대로 읽는다
                            .clearAndSetSemantics { contentDescription = line.spoken() }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            // 오늘 것은 시각만 보여 준다. 날짜는 어제 일과 섞일 때만 쓸모가 있다
                            line.substringBefore("  ").removePrefix("$today "),
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

/** 기호를 말로 바꾼다. 화면에는 ▶ 가 빠르고, 소리로는 「실행」이 맞다 */
private fun String.spoken(): String = this
    .replace("▶", "실행")
    .replace("■", "멈춤")

/** 알림 접근 권한이 켜져 있는지 — 시스템 설정 문자열에 이 앱이 들어있는지로 판단한다 */
private fun isListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any { it.substringBefore("/") == context.packageName }
}
