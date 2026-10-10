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

@Composable
fun MonsterView(monster: Monster, modifier: Modifier = Modifier) {
    val sleeping = monster.mood == MonsterMood.SLEEPING
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

    Box(modifier = modifier.size(240.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (sleeping) {
                        val breathScale = 1f + breathing.value * 0.018f
                        scaleX = breathScale
                        scaleY = breathScale
                        translationY = breathing.value * 1.5.dp.toPx()
                    } else {
                        translationY = -bobAmount.value * 3.dp.toPx()
                    }
                }
                .semantics { contentDescription = "Monstro ${monster.name}" },
        ) {
        val factor = size.minDimension / 240f

        withTransform({ scale(factor, factor, pivot = Offset.Zero) }) {
            drawOval(
                color = Color(0x242B642C),
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

            drawPath(
                path = body,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFC9F58E), Color(0xFF75CC62), Color(0xFF54AE52)),
                    center = Offset(91f, 72f),
                    radius = 205f,
                ),
            )
            drawPath(
                path = body,
                color = Color(0xFF438E46),
                style = Stroke(width = 4f, join = StrokeJoin.Round),
            )

            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x70FFFFFF), Color(0x00FFFFFF)),
                    startY = 45f,
                    endY = 115f,
                ),
                topLeft = Offset(67f, 43f),
                size = Size(91f, 51f),
            )

            drawCircle(Color(0x5577B957), radius = 9f, center = Offset(71f, 137f))
            drawCircle(Color(0x5577B957), radius = 9f, center = Offset(169f, 137f))

            val eyeOpenness = if (sleeping) 0f else 1f - blinkProgress.value
            listOf(Offset(91f, 112f), Offset(150f, 112f)).forEach { eye ->
                if (eyeOpenness > 0.1f) {
                    val eyeHeight = 26f * eyeOpenness
                    val pupilHeight = 14f * eyeOpenness
                    drawOval(
                        color = Color(0xFFF8FFF2),
                        topLeft = Offset(eye.x - 13f, eye.y - eyeHeight / 2f),
                        size = Size(26f, eyeHeight),
                    )
                    drawOval(
                        color = Color(0xFF285B35),
                        topLeft = Offset(eye.x - 5f, eye.y + 2f - pupilHeight / 2f),
                        size = Size(14f, pupilHeight),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.2f * eyeOpenness,
                        center = eye + Offset(-1f, -eyeOpenness),
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
                moveTo(108f, 146f)
                cubicTo(115f, 156f, 127f, 156f, 134f, 146f)
            }
            drawPath(
                path = smile,
                color = Color(0xFF285B35),
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
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
    }
}
