package top.deepcommunity.app.ui.chat.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// 3x3 点阵映射（与 Web 端 FACE_PIPS 完全一致）
private val DICE_FACE_PIPS: Map<Int, List<Int>> = mapOf(
    1 to listOf(4),
    2 to listOf(0, 8),
    3 to listOf(0, 4, 8),
    4 to listOf(0, 2, 6, 8),
    5 to listOf(0, 2, 4, 6, 8),
    6 to listOf(0, 2, 3, 5, 6, 8)
)

// 骰子颜色定义（象牙白双色渐变与金属反光圆点）
private val DICE_BG_GRADIENT = Brush.linearGradient(
    colors = listOf(Color(0xFFFFFDF8), Color(0xFFE9E5DB)),
    start = Offset(0f, 0f),
    end = Offset(200f, 200f)
)
private val DICE_PIP_DARK = Color(0xFF1B1E23)
private val DICE_PIP_RED = Color(0xFFDC2626)

/**
 * 仿真物理 3D 滚动骰子组件（像素级复刻 Web 端纯 CSS 3D DiceCube 翻滚质感）
 *
 * @param value 目标点数（1~6，服务端下发）
 * @param animate 是否播放翻滚动画（历史消息传 false 直接呈现点数）
 * @param size 骰子尺寸
 * @param onRollEnd 动画结束回调
 */
@Composable
fun DiceCube(
    value: Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    size: Dp = 54.dp,
    onRollEnd: ((Int) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val targetVal = value.coerceIn(1, 6)

    var currentDisplayVal by remember { mutableIntStateOf(if (animate) 1 else targetVal) }
    var isRolling by remember { mutableStateOf(animate) }

    val rotationX = remember { Animatable(if (animate) 0f else -360f) }
    val rotationY = remember { Animatable(if (animate) 0f else -360f) }
    val scaleAnim = remember { Animatable(1f) }

    fun playRollAnimation() {
        scope.launch {
            isRolling = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            // Web 端贝塞尔曲线: cubic-bezier(.16, .9, .3, 1)
            val rollEasing = CubicBezierEasing(0.16f, 0.9f, 0.3f, 1f)
            val rollDuration = 1250

            launch {
                rotationX.snapTo(0f)
                rotationX.animateTo(
                    targetValue = -360f * 2 - (targetVal * 60f),
                    animationSpec = tween(durationMillis = rollDuration, easing = rollEasing)
                )
            }
            launch {
                rotationY.snapTo(0f)
                rotationY.animateTo(
                    targetValue = -360f * 2 - (targetVal * 90f),
                    animationSpec = tween(durationMillis = rollDuration, easing = rollEasing)
                )
            }

            // 滚动过程中高频闪动切换不同面（模拟真实物理翻滚）
            val startTime = System.currentTimeMillis()
            var stepDelay = 45L
            while (System.currentTimeMillis() - startTime < rollDuration - 200) {
                currentDisplayVal = Random.nextInt(1, 7)
                delay(stepDelay)
                stepDelay += 8L // 逐步减速
            }

            // 准确落地到最终点数
            currentDisplayVal = targetVal
            isRolling = false

            // 触底轻微回弹缩放（落地阻尼反馈）
            scaleAnim.animateTo(1.12f, animationSpec = tween(120, easing = FastOutSlowInEasing))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scaleAnim.animateTo(1f, animationSpec = tween(180, easing = FastOutSlowInEasing))

            onRollEnd?.invoke(targetVal)
        }
    }

    LaunchedEffect(targetVal, animate) {
        if (animate) {
            playRollAnimation()
        } else {
            currentDisplayVal = targetVal
            isRolling = false
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .scale(scaleAnim.value)
            .graphicsLayer {
                this.rotationX = rotationX.value
                this.rotationY = rotationY.value
                this.cameraDistance = 12f * density
            }
            .shadow(
                elevation = if (isRolling) 12.dp else 5.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color.Black.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.18f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(DICE_BG_GRADIENT)
            .border(1.5.dp, Color(0x33000000), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (!isRolling) {
                        playRollAnimation()
                    }
                }
            )
            .padding(size * 0.14f),
        contentAlignment = Alignment.Center
    ) {
        DiceFaceContent(
            faceValue = currentDisplayVal,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 绘制骰子单个面上的点阵
 */
@Composable
private fun DiceFaceContent(
    faceValue: Int,
    modifier: Modifier = Modifier
) {
    val activePips = remember(faceValue) {
        DICE_FACE_PIPS[faceValue.coerceIn(1, 6)] ?: listOf(4)
    }
    val isSingleRed = faceValue == 1

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cellWidth = size.width / 3f
        val cellHeight = size.height / 3f
        val pipRadius = (cellWidth * 0.38f).coerceAtLeast(3f)

        for (row in 0..2) {
            for (col in 0..2) {
                val index = row * 3 + col
                if (activePips.contains(index)) {
                    val cx = col * cellWidth + cellWidth / 2f
                    val cy = row * cellHeight + cellHeight / 2f

                    val pipColor = if (isSingleRed) DICE_PIP_RED else DICE_PIP_DARK

                    // 点阵圆点渐变（模拟深色圆孔与凹陷光影）
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.15f),
                        radius = pipRadius + 1.2f,
                        center = Offset(cx, cy + 0.8f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isSingleRed) Color(0xFFFF5252) else Color(0xFF4A4F57),
                                pipColor
                            ),
                            center = Offset(cx - pipRadius * 0.25f, cy - pipRadius * 0.25f),
                            radius = pipRadius
                        ),
                        radius = pipRadius,
                        center = Offset(cx, cy)
                    )
                    // 高光反光点
                    drawCircle(
                        color = Color.White.copy(alpha = 0.45f),
                        radius = pipRadius * 0.28f,
                        center = Offset(cx - pipRadius * 0.3f, cy - pipRadius * 0.3f)
                    )
                }
            }
        }
    }
}
