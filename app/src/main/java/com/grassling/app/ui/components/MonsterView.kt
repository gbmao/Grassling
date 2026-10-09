package com.grassling.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.grassling.app.monster.Monster

@Composable
fun MonsterView(monster: Monster) {
    Text(text = monster.name)
}
