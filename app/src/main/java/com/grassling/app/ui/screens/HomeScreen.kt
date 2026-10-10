package com.grassling.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.grassling.app.monster.Monster
import com.grassling.app.monster.MonsterEngine
import com.grassling.app.testandoAPP.EnergyController
import com.grassling.app.ui.components.MonsterView
import kotlin.math.roundToInt

@Composable
fun HomeScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val energyController = remember { EnergyController() }
    val monsterEngine = remember { MonsterEngine() }
    val energy = energyController.energy

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                QuestScreen(isVisible = drawerState.isOpen)
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF0F8EC), Color(0xFFE3F1DD), Color(0xFFD5EACF)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonsterView(
                    monster = Monster(mood = monsterEngine.moodForEnergy(energy)),
                    modifier = Modifier.size(240.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Energy: $energy / 100",
                    color = Color(0xFF285B35),
                )
                Slider(
                    value = energy.toFloat(),
                    onValueChange = { energyController.changeEnergy(it.roundToInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(260.dp),
                )
            }
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(24.dp),
            ) {
                val strokeWidth = 2.dp.toPx()
                val centerY = size.height / 2f
                val tipX = size.width - 3.dp.toPx()
                val headStartX = tipX - 7.dp.toPx()
                val headHalfHeight = 6.dp.toPx()
                val arrowColor = Color(0xFF285B35)

                drawLine(
                    color = arrowColor,
                    start = Offset(2.dp.toPx(), centerY),
                    end = Offset(tipX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = arrowColor,
                    start = Offset(headStartX, centerY - headHalfHeight),
                    end = Offset(tipX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = arrowColor,
                    start = Offset(tipX, centerY),
                    end = Offset(headStartX, centerY + headHalfHeight),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
