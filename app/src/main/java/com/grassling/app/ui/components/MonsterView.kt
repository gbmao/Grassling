package com.grassling.app.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grassling.app.monster.Monster
import com.grassling.app.monster.MonsterMood
import kotlin.math.abs

@Composable
fun MonsterView(monster: Monster, modifier: Modifier = Modifier) {
    val sleeping = monster.mood == MonsterMood.SLEEPING
    val anxious = monster.mood == MonsterMood.ANXIOUS
    val raging = monster.mood == MonsterMood.EXPLODING_WITH_RAGE
    val idleTransition = rememberInfiniteTransition(label = "monster-idle")
    val bobAmount = idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idle-bob",
    )
    val blinkProgress = idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3_400
                0f at 0
                0f at 1_500
                1f at 1_540 using FastOutLinearInEasing
                1f at 1_600
                0f at 1_640 using LinearOutSlowInEasing
                0f at 3_400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "idle-blink",
    )
    val breathing = idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sleep-breathing",
    )
    val zzzFloat = idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_700, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sleep-zzz",
    )
    val anxiousShake = idleTransition.animateFloat(
        initialValue = -1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 560
                -1f at 0
                0.8f at 70
                -0.7f at 150
                1f at 240
                -0.4f at 330
                0.6f at 420
                0f at 560
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "anxious-shake",
    )
    val sweatCycle = idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "anxious-sweat",
    )
    val rageShake = idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 380
                0f at 0
                1f at 45
                -1f at 95
                0.85f at 150
                -0.8f at 210
                0.65f at 275
                -0.45f at 330
                0f at 380
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "rage-shake",
    )
    val ragePulse = idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rage-pulse",
    )

    Box(modifier = modifier.size(240.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (sleeping) {
                        val breathScale = 1f + breathing.value * 0.018f
                        scaleX = breathScale
                        scaleY = breathScale
                        translationX = 0f
                        translationY = breathing.value * 1.5.dp.toPx()
                        rotationZ = 0f
                    } else if (anxious) {
                        scaleX = 1f
                        scaleY = 1f
                        translationX = anxiousShake.value * 2.2.dp.toPx()
                        translationY = 0f
                        rotationZ = anxiousShake.value * 1.2f
                    } else if (raging) {
                        val rageScale = 1f + ragePulse.value * 0.055f
                        scaleX = rageScale
                        scaleY = 1f - ragePulse.value * 0.025f
                        translationX = rageShake.value * 8.dp.toPx()
                        translationY = abs(rageShake.value) * 3.dp.toPx()
                        rotationZ = rageShake.value * 4.5f
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                        translationX = 0f
                        translationY = -bobAmount.value * 3.dp.toPx()
                        rotationZ = 0f
                    }
                }
                .semantics { contentDescription = "Monster ${monster.name}" },
        ) {
        val factor = size.minDimension / 240f

        withTransform({ scale(factor, factor, pivot = Offset.Zero) }) {
            drawOval(
                color = if (raging) Color(0x665C1722) else Color(0x242B642C),
                topLeft = Offset(67f, 207f),
                size = Size(106f, 17f),
            )

            val body = Path().apply {
                moveTo(120f, 27f)
                cubicTo(164f, 23f, 203f, 53f, 211f, 96f)
                cubicTo(221f, 145f, 192f, 190f, 151f, 207f)
                cubicTo(111f, 224f, 65f, 208f, 40f, 176f)
                cubicTo(13f, 141f, 23f, 89f, 48f, 57f)
                cubicTo(66f, 34f, 91f, 25f, 120f, 27f)
                close()
            }

            if (raging) {
                val glowAlpha = 0.3f + ragePulse.value * 0.22f
                drawPath(
                    path = body,
                    color = Color(0xFFFF4938).copy(alpha = glowAlpha),
                    style = Stroke(width = 16f + ragePulse.value * 5f, join = StrokeJoin.Round),
                )
                drawPath(
                    path = body,
                    color = Color(0xFFFFB044).copy(alpha = glowAlpha * 0.72f),
                    style = Stroke(width = 7f, join = StrokeJoin.Round),
                )
            }

            drawPath(
                path = body,
                brush = Brush.radialGradient(
                    colors = if (raging) {
                        listOf(Color(0xFFFFB56E), Color(0xFFF4513F), Color(0xFFBC263C))
                    } else {
                        listOf(Color(0xFFC9F58E), Color(0xFF75CC62), Color(0xFF54AE52))
                    },
                    center = Offset(91f, 72f),
                    radius = 205f,
                ),
            )
            drawPath(
                path = body,
                color = if (raging) Color(0xFF8C2034) else Color(0xFF438E46),
                style = Stroke(width = 4f, join = StrokeJoin.Round),
            )

            drawOval(
                brush = Brush.verticalGradient(
                    colors = if (raging) {
                        listOf(Color(0x70FFF0CF), Color(0x00FFF0CF))
                    } else {
                        listOf(Color(0x70FFFFFF), Color(0x00FFFFFF))
                    },
                    startY = 45f,
                    endY = 115f,
                ),
                topLeft = Offset(67f, 43f),
                size = Size(91f, 51f),
            )

            val cheekColor = if (raging) Color(0xBFFF9A42) else Color(0x5577B957)
            drawCircle(cheekColor, radius = if (raging) 13f else 9f, center = Offset(71f, 137f))
            drawCircle(cheekColor, radius = if (raging) 13f else 9f, center = Offset(169f, 137f))

            if (raging) {
                drawLine(
                    color = Color(0xFF742031),
                    start = Offset(77f, 91f),
                    end = Offset(104f, 101f),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xFF742031),
                    start = Offset(136f, 101f),
                    end = Offset(164f, 91f),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round,
                )
            }

            val eyeOpenness = if (sleeping) {
                0f
            } else if (raging) {
                (0.9f + ragePulse.value * 0.1f).coerceAtMost(1f)
            } else {
                (1f - blinkProgress.value + if (anxious) 0.12f else 0f).coerceAtMost(1f)
            }
            val anxiousGaze = if (anxious) anxiousShake.value * 2.5f else 0f
            listOf(Offset(91f, 112f), Offset(150f, 112f)).forEach { eye ->
                if (eyeOpenness > 0.1f) {
                    val normalEyeHeight = when {
                        raging -> 19f
                        anxious -> 30f
                        else -> 26f
                    }
                    val eyeHeight = normalEyeHeight * eyeOpenness
                    val pupilHeight = 14f * eyeOpenness
                    drawOval(
                        color = if (raging) Color(0xFFFFF4DF) else Color(0xFFF8FFF2),
                        topLeft = Offset(eye.x - 13f, eye.y - eyeHeight / 2f),
                        size = Size(26f, eyeHeight),
                    )
                    drawOval(
                        color = if (raging) Color(0xFF70202D) else Color(0xFF285B35),
                        topLeft = Offset(eye.x - 5f + anxiousGaze, eye.y + 2f - pupilHeight / 2f),
                        size = Size(14f, pupilHeight),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.2f * eyeOpenness,
                        center = eye + Offset(-1f + anxiousGaze, -eyeOpenness),
                    )
                } else {
                    drawLine(
                        color = Color(0xFF285B35),
                        start = Offset(eye.x - 11f, eye.y),
                        end = Offset(eye.x + 11f, eye.y),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            val smile = Path().apply {
                if (raging) {
                    moveTo(105f, 149f)
                    cubicTo(113f, 145f, 129f, 145f, 137f, 149f)
                    lineTo(134f, 158f)
                    cubicTo(126f, 162f, 115f, 162f, 107f, 158f)
                    close()
                } else if (anxious) {
                    moveTo(110f, 151f)
                    cubicTo(116f, 143f, 123f, 159f, 130f, 150f)
                    cubicTo(132f, 148f, 134f, 149f, 136f, 152f)
                } else {
                    moveTo(108f, 146f)
                    cubicTo(115f, 156f, 127f, 156f, 134f, 146f)
                }
            }
            if (raging) {
                drawPath(path = smile, color = Color(0xFF642D35))
                drawPath(
                    path = Path().apply {
                        moveTo(108f, 148f)
                        quadraticTo(120f, 145f, 134f, 148f)
                        lineTo(132f, 158f)
                        quadraticTo(120f, 162f, 109f, 158f)
                        close()
                    },
                    color = Color(0xFFFFF1D6),
                )
                listOf(114f, 120f, 126f).forEach { toothDividerX ->
                    drawLine(
                        color = Color(0xFF8C2034),
                        start = Offset(toothDividerX, 148f),
                        end = Offset(toothDividerX, 159f),
                        strokeWidth = 1.7f,
                    )
                }
            } else {
                drawPath(
                    path = smile,
                    color = Color(0xFF285B35),
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
        if (sleeping) {
            Text(
                text = "ZzZzZz",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 34.dp)
                    .graphicsLayer {
                        translationY = -zzzFloat.value * 11.dp.toPx()
                        alpha = 0.78f + zzzFloat.value * 0.22f
                    },
                color = Color(0xFF6473B5),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (anxious) {
            val sweatAlpha = when {
                sweatCycle.value < 0.1f -> sweatCycle.value / 0.1f
                sweatCycle.value > 0.82f -> (1f - sweatCycle.value) / 0.18f
                else -> 1f
            }.coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-22).dp, y = 62.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        translationY = -sweatCycle.value * 9.dp.toPx()
                        alpha = sweatAlpha
                    },
            ) {
                val drop = Path().apply {
                    moveTo(size.width * 0.5f, 0f)
                    cubicTo(size.width * 0.38f, size.height * 0.22f, size.width * 0.1f, size.height * 0.47f, size.width * 0.1f, size.height * 0.65f)
                    cubicTo(size.width * 0.1f, size.height * 0.86f, size.width * 0.28f, size.height, size.width * 0.5f, size.height)
                    cubicTo(size.width * 0.75f, size.height, size.width * 0.9f, size.height * 0.84f, size.width * 0.9f, size.height * 0.63f)
                    cubicTo(size.width * 0.9f, size.height * 0.43f, size.width * 0.62f, size.height * 0.17f, size.width * 0.5f, 0f)
                    close()
                }
                drawPath(path = drop, color = Color(0xFF75CDE5))
                drawCircle(
                    color = Color(0xFFD9F8FF),
                    radius = size.width * 0.08f,
                    center = Offset(size.width * 0.35f, size.height * 0.61f),
                )
            }
        }
    }
}
