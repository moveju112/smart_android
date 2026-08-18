package com.wemade.smartnoti

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
    outline = Color(0xFF3A4650),
    outlineVariant = Color(0xFF2A333D)
)

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

@Composable
fun SmartNotiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
