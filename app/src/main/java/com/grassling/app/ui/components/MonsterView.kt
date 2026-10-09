package com.grassling.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grassling.app.monster.Monster

@Composable
fun MonsterView(monster: Monster) {
    Canvas(
        modifier = Modifier
            .size(240.dp)
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

            listOf(Offset(91f, 112f), Offset(150f, 112f)).forEach { eye ->
                drawCircle(Color(0xFFF8FFF2), radius = 13f, center = eye)
                drawCircle(Color(0xFF285B35), radius = 7f, center = eye + Offset(2f, 2f))
                drawCircle(Color.White, radius = 2.2f, center = eye + Offset(-1f, -1f))
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
}
