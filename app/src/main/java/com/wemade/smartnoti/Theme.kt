package com.wemade.smartnoti

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 계기판에서 가져온 색. 청록은 신호(트리거·동작 중), 앰버는 시간(대기), 빨강은 되돌릴 수 없는 일
private val SignalLight = Color(0xFF00796B)
private val SignalDark = Color(0xFF4FD1C5)
private val PulseLight = Color(0xFFB25E00)
private val PulseDark = Color(0xFFFFB454)
private val AlertLight = Color(0xFFC2352F)
private val AlertDark = Color(0xFFFF7A75)

private val InkLight = Color(0xFF12161C)
private val PaperLight = Color(0xFFF5F7F9)
private val CardLight = Color(0xFFFFFFFF)
private val MuteLight = Color(0xFF5B6773)

private val InkDark = Color(0xFFE6EBF0)
private val PaperDark = Color(0xFF0F141A)
private val CardDark = Color(0xFF1A222C)
private val MuteDark = Color(0xFF8A97A6)

private val LightColors = lightColorScheme(
    primary = SignalLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8E8E1),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = PulseLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B8),
    onSecondaryContainer = Color(0xFF2B1700),
    error = AlertLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD7),
    onErrorContainer = Color(0xFF410004),
    background = PaperLight,
    onBackground = InkLight,
    surface = CardLight,
    onSurface = InkLight,
    surfaceVariant = Color(0xFFE6EBEF),
    onSurfaceVariant = MuteLight,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = Color(0xFFEDF1F4),
    outline = Color(0xFFBFC8D1),
    outlineVariant = Color(0xFFD9E0E6)
)

private val DarkColors = darkColorScheme(
    primary = SignalDark,
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFB8E8E1),
    secondary = PulseDark,
    onSecondary = Color(0xFF2B1700),
    secondaryContainer = Color(0xFF6B3E00),
    onSecondaryContainer = Color(0xFFFFE0B8),
    error = AlertDark,
    onError = Color(0xFF410004),
    errorContainer = Color(0xFF7A1F1C),
    onErrorContainer = Color(0xFFFFDAD7),
    background = PaperDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF252E39),
    onSurfaceVariant = MuteDark,
    surfaceContainerLowest = Color(0xFF0B1015),
    surfaceContainerLow = Color(0xFF151C24),
    surfaceContainer = CardDark,
    surfaceContainerHigh = Color(0xFF212B36),
    surfaceContainerHighest = Color(0xFF283340),
    outline = Color(0xFF3A4650),
    outlineVariant = Color(0xFF2A333D)
)

/**
 * 이 앱의 간격은 4의 배수 여섯 단계다 — 4 · 8 · 12 · 16 · 20 · 24.
 *
 * 한때 3·5·6·7·9·10·11·14·17·18·22가 뒤섞여 스물두 종이었다. 값이 스물두 개면 어느 것도
 * 뜻이 없어서, 붙은 것과 떨어진 것을 눈이 구별하지 못한다. 여섯 단계에서는 각각이 일을 맡는다.
 *
 *   4  같은 것끼리 (이름과 그 아래 부제)
 *   8  한 줄 안의 칸 사이, 카드끼리
 *  12  한 덩어리 안의 줄 사이, 카드 안쪽 여백
 *  16  글이 왼쪽에서 시작하는 자리
 *  20  머리글 위 (아래보다 넉넉해야 아래 것을 데리고 있다)
 *  24  목록의 끝
 *
 * 그림 자체의 치수는 이 스케일이 아니다. 레일의 4.5dp 원과 1.5dp 선은 눈으로 맞춘 값이고,
 * 48·56dp는 손가락이 정한 값이다.
 */

/**
 * 이 앱은 시간·초·MAC 주소·패키지명 같은 기계 값이 화면의 절반이다.
 * 그래서 그런 값에만 고정폭을 쓰고, 사람이 읽는 글은 시스템 글꼴 그대로 둔다.
 */
val MonoLabel = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    letterSpacing = 0.sp
)

val MonoSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    letterSpacing = 0.sp
)

/**
 * 매크로 하나를 문장으로 읽는 화면에서 쓴다.
 * 이 앱에서 가장 큰 글자다 — 화면에 들어와서 첫 번째로 읽히라고 그렇게 뒀다.
 */
val SentenceStyle = TextStyle(
    fontSize = 21.sp,
    lineHeight = 34.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.2).sp
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

/** 화면을 밝게 볼지 어둡게 볼지 */
enum class ThemeMode(val label: String) {
    System("기기 설정 따름"),
    Light("밝게"),
    Dark("어둡게");

    /** 다음 값 — 마지막까지 가면 처음으로 돌아온다 */
    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/**
 * 고른 화면 밝기를 기억한다.
 *
 * 기본은 기기 설정을 따르는 것이다. 사람이 한 번 정하면 기기가 밤이 되든 말든 그 값을 지킨다.
 */
object ThemeState {
    private const val PREFS = "theme"
    private const val KEY_MODE = "mode"

    val mode = MutableStateFlow(ThemeMode.System)

    // 1. 저장해 둔 값 읽기 (없거나 모르는 값이면 기기 설정 따름)
    fun load(context: Context) {
        val saved = prefs(context).getString(KEY_MODE, null)
        mode.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    // 2. 다음 값으로 넘기고 바로 남긴다
    fun cycle(context: Context) {
        val next = mode.value.next()
        mode.value = next
        prefs(context).edit().putString(KEY_MODE, next.name).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

@Composable
fun SmartNotiTheme(content: @Composable () -> Unit) {
    val mode by ThemeState.mode.collectAsState()
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkColors else LightColors

    // 창 배경과 상태바 글자색도 같이 맞춘다. 안 그러면 고른 것과 반대 색이 한 번 번쩍인다
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
            WindowInsetsControllerCompat(window, view).run {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}

/**
 * 기기에서 애니메이션을 꺼 둔 사람인지 본다.
 * 개발자 옵션의 애니메이션 배율과 접근성의 "애니메이션 제거"가 같은 값을 0으로 만든다.
 * 이 앱은 숨쉬는 점과 흐르는 레일이 계속 움직이므로, 꺼 둔 사람에게는 멈춰 보여야 한다.
 */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * 이 앱의 스위치.
 *
 * 켜고 끄는 일은 자주 하지 않는데 목록에 다섯 개가 세로로 늘어서면 화면에서 가장 진한 것이 된다.
 * 정작 읽어야 할 것은 매크로가 무엇을 하는지다. 그래서 판은 연하게 두고 손잡이만 진하게 남긴다.
 */
@Composable
fun QuietSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.scale(0.85f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.primary,
            checkedTrackColor = scheme.primaryContainer,
            checkedBorderColor = scheme.primary,
            uncheckedThumbColor = scheme.outline,
            uncheckedTrackColor = scheme.surfaceVariant,
            uncheckedBorderColor = scheme.outline
        )
    )
}


/**
 * 요약 문장을 그린다. 사람이 고른 값만 진하게 남는다.
 *
 * 문장 편집기는 채워진 칸에 색을 깔아 「이건 네가 정한 것」을 보여 준다. 한 줄 요약에는
 * 칸을 깔 자리가 없으니 같은 뜻을 글자 무게와 잉크로 옮긴다 — 값은 진한 잉크에 Medium,
 * 앱이 붙인 문법은 연한 잉크에 보통 무게.
 *
 * 색을 쓰지 않는 이유가 있다. 이 앱의 청록은 이미 「선택됨」과 「누를 수 있는 것」 두 뜻을
 * 나눠 쓰고 있어서, 세 번째 뜻을 얹으면 색이 아무 말도 하지 않게 된다.
 */
@Composable
fun List<Part>.styled(): AnnotatedString {
    val scheme = MaterialTheme.colorScheme
    return buildAnnotatedString {
        this@styled.forEach { part ->
            when (part) {
                is Part.Value -> withStyle(
                    SpanStyle(color = scheme.onSurface, fontWeight = FontWeight.Medium)
                ) { append(part.text) }

                is Part.Plain -> withStyle(
                    SpanStyle(color = scheme.onSurfaceVariant)
                ) { append(part.text) }
            }
        }
    }
}
