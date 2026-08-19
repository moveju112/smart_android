package com.wemade.smartnoti

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 매크로는 위에서 아래로 흐르는 순서 그 자체다. 그래서 각 단계를 하나의 레일에 꿴다.
 * 채워진 원 = 트리거(여기서 시작), 점선 = 대기(시간이 흐름), 사각 = 액션(무언가 함), 빈 원 = 조건.
 */
enum class Step { Trigger, Wait, Act, Gate }

fun Action.step(): Step = when (this) {
    is Action.Delay -> Step.Wait
    is Action.StopIfBluetooth, is Action.StopUnless -> Step.Gate
    else -> Step.Act
}

/**
 * 레일 한 칸. 실행 중이면 선이 흐르는 것처럼 보이게 한다 —
 * 매크로가 30분씩 대기하는 일이 있어서, 지금 돌고 있는지가 화면에서 바로 보여야 한다.
 */
@Composable
fun RailRow(
    step: Step,
    isFirst: Boolean,
    isLast: Boolean,
    lineColor: Color,
    markerColor: Color,
    running: Boolean = false,
    content: @Composable () -> Unit
) {
    // 애니메이션을 꺼 둔 사람에게는 흐르지 않는다. 점선은 그대로 보인다
    val flowing = running && !reduceMotion()
    val phase by if (flowing) {
        rememberInfiniteTransition(label = "rail").animateFloat(
            initialValue = 0f,
            targetValue = 24f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "flow"
        )
    } else {
        rememberStaticZero()
    }

    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .width(20.dp)
                .semantics {
                    contentDescription = when (step) {
                        Step.Trigger -> "트리거"
                        Step.Wait -> "대기"
                        Step.Act -> "실행"
                        Step.Gate -> "조건"
                    }
                }
                .fillMaxHeight()
                .heightIn(min = 22.dp)
                .drawBehind {
                    val x = size.width / 2
                    val midY = 11.dp.toPx()
                    val stroke = 1.5.dp.toPx()

                    // 1. 위아래로 잇는 선. 첫 칸은 위쪽이, 마지막 칸은 아래쪽이 없다
                    if (!isFirst) drawLine(lineColor, Offset(x, 0f), Offset(x, midY), stroke)
                    if (!isLast) {
                        // 대기 구간만 점선으로 — 시간이 비어 있다는 뜻
                        val effect = if (step == Step.Wait)
                            PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), phase)
                        else null
                        drawLine(lineColor, Offset(x, midY), Offset(x, size.height), stroke, pathEffect = effect)
                    }

                    // 2. 이 칸이 무엇인지 알리는 표식
                    when (step) {
                        Step.Trigger -> drawCircle(markerColor, 4.5.dp.toPx(), Offset(x, midY))
                        Step.Act -> drawRect(
                            markerColor,
                            Offset(x - 3.5.dp.toPx(), midY - 3.5.dp.toPx()),
                            androidx.compose.ui.geometry.Size(7.dp.toPx(), 7.dp.toPx())
                        )
                        Step.Gate -> drawCircle(markerColor, 4.dp.toPx(), Offset(x, midY), style = Stroke(stroke))
                        Step.Wait -> drawCircle(markerColor, 2.dp.toPx(), Offset(x, midY))
                    }
                }
        )
        Box(Modifier.padding(start = 6.dp, bottom = 3.dp), contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}

/** 실행 중이 아닐 때는 애니메이션을 아예 만들지 않는다 */
@Composable
private fun rememberStaticZero() = androidx.compose.runtime.remember {
    object : androidx.compose.runtime.State<Float> {
        override val value = 0f
    }
}

/** 매크로 한 개를 레일로 그린다. 목록과 편집 화면이 같은 그림을 쓴다 */
@Composable
fun MacroRail(
    macro: Macro,
    running: Boolean,
    lineColor: Color,
    triggerColor: Color,
    waitColor: Color,
    actColor: Color,
    line: @Composable (index: Int, text: String) -> Unit
) {
    val triggers = macro.allTriggers()
    val total = macro.actions.size + triggers.size
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // 트리거가 여럿이면 모두 적는다. 그중 하나만 걸려도 돌기 때문이다
        triggers.forEachIndexed { index, trigger ->
            RailRow(
                step = Step.Trigger,
                isFirst = index == 0,
                isLast = total == index + 1,
                lineColor = lineColor,
                markerColor = triggerColor,
                running = running
            ) { line(-1, trigger.summary() + if (index < triggers.lastIndex) "  또는" else "") }
        }

        macro.actions.forEachIndexed { index, action ->
            val step = action.step()
            RailRow(
                step = step,
                isFirst = false,
                isLast = index == macro.actions.lastIndex,
                lineColor = lineColor,
                markerColor = if (step == Step.Wait) waitColor else actColor,
                running = running
            ) { line(index, action.summary()) }
        }
    }
}
