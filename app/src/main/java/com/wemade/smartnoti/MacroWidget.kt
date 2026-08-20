package com.wemade.smartnoti

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 홈 화면에 놓는 매크로 실행 버튼.
 *
 * 트리거를 기다리지 않고 손으로 돌리고 싶을 때가 있다 — VPN을 바꾼다든지.
 * 매크로를 지정해 두고 누르면 그 매크로가 그 자리에서 돈다.
 */
class MacroWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { prefs.remove(key(it)) }
        prefs.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)

        when (intent.action) {
            // 홈에 막 놓인 위젯이다. 어느 매크로를 걸지는 놓기 전에 이미 정해져 있다
            ACTION_PINNED -> {
                val macroId = intent.getLongExtra(EXTRA_MACRO_ID, -1)
                if (widgetId != 0 && macroId >= 0) {
                    bind(context, widgetId, macroId)
                    render(context, AppWidgetManager.getInstance(context), widgetId)
                }
            }

            ACTION_RUN -> {
                val macro = macroOf(context, widgetId)
                val service = MacroService.instance
                when {
                    macro == null -> toast(context, "실행할 매크로가 없습니다. 위젯을 다시 놓아 주세요")
                    service == null -> toast(context, "엔진이 꺼져 있습니다. 알림 접근 권한을 켜세요")
                    else -> {
                        service.runNow(macro)
                        toast(context, "${macro.name} 실행")
                    }
                }
            }
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val PREFS = "widgets"
        const val ACTION_RUN = "com.wemade.smartnoti.WIDGET_RUN"
        private const val ACTION_PINNED = "com.wemade.smartnoti.WIDGET_PINNED"
        private const val EXTRA_MACRO_ID = "macroId"

        private fun key(widgetId: Int) = "macro_$widgetId"

        /** 런처 위젯 목록을 뒤지게 하지 않는다. 앱에서 바로 홈에 놓아 준다 */
        fun canPin(context: Context): Boolean =
            runCatching {
                context.getSystemService(AppWidgetManager::class.java).isRequestPinAppWidgetSupported
            }.getOrDefault(false)

        fun pin(context: Context, macro: Macro) {
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
            // 놓인 뒤에 시스템이 위젯 번호를 담아 이 신호를 돌려준다. 그래서 고쳐 쓸 수 있어야 한다
            val callback = PendingIntent.getBroadcast(
                context, macro.id.toInt(),
                Intent(context, MacroWidget::class.java).apply {
                    action = ACTION_PINNED
                    putExtra(EXTRA_MACRO_ID, macro.id)
                },
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            runCatching {
                manager.requestPinAppWidget(
                    ComponentName(context, MacroWidget::class.java), null, callback
                )
            }
        }

        fun bind(context: Context, widgetId: Int, macroId: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(key(widgetId), macroId).apply()
        }

        private fun macroOf(context: Context, widgetId: Int): Macro? {
            MacroStore.load(context)
            val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(key(widgetId), -1)
            return MacroStore.find(id)
        }

        /** 위젯 한 칸을 그린다. 지운 매크로를 가리키고 있으면 그대로 알려 준다 */
        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val macro = macroOf(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_macro)
            views.setTextViewText(R.id.widget_name, macro?.name ?: "매크로 없음")
            // 눌러도 다시 지정되지 않는다. 그러니 그렇게 적지 않는다 — 위젯은 놓을 때만 고를 수 있다
            views.setTextViewText(
                R.id.widget_hint,
                macro?.firstTrigger()?.summary() ?: "이 위젯을 지우고 다시 놓으세요"
            )

            val intent = Intent(context, MacroWidget::class.java).apply {
                action = ACTION_RUN
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val pending = PendingIntent.getBroadcast(
                context, widgetId, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }
    }
}

/** 위젯을 놓을 때 어느 매크로를 걸지 고르는 화면 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 고르지 않고 나가면 위젯을 놓지 않은 것으로 돌려준다
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        ThemeState.load(this)
        MacroStore.load(this)
        setContent {
            SmartNotiTheme {
                WidgetConfigScreen { macro ->
                    MacroWidget.bind(this, widgetId, macro.id)
                    MacroWidget.render(this, AppWidgetManager.getInstance(this), widgetId)
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    )
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun WidgetConfigScreen(onPick: (Macro) -> Unit) {
    val macros by MacroStore.macros.collectAsState()

    val scheme = MaterialTheme.colorScheme
    // 목록 화면과 같은 앱바 규칙을 쓴다. 스크롤이 시작되면 경계가 생긴다
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text("어떤 매크로를 걸까요?") }
            )
        }
    ) { padding ->
        if (macros.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("아직 매크로가 없습니다", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    "앱에서 매크로를 만든 뒤에 위젯을 놓아 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(macros, key = { it.id }) { macro ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = scheme.surface),
                        modifier = Modifier.fillMaxWidth()
                            .clickable(role = Role.Button) { onPick(macro) }
                    ) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
                            Text(macro.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.padding(top = 5.dp))
                            // 목록에서 쓰는 표식 그대로. 어느 매크로인지 같은 말로 읽힌다
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StepMark(Step.Trigger, scheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    macro.oneLine(),
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
    }
}
