package dev.lookup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lookup.ui.terminal.Term

/**
 * the floor plan: terminal on the left, scrying floor on the right.
 * a black scaffold with a hairline nav — no colour down here either.
 */
@Composable
fun HomeScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Term.Bg,
        bottomBar = {
            Box(
                modifier = Modifier
                    .background(Term.Bg)
                    .drawBehind {
                        drawLine(Term.Hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f)
                    },
            ) {
                NavigationBar(containerColor = Term.Bg) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = {
                            Text(
                                "TERMINAL",
                                fontFamily = Term.Mono,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Term.InvertInk,
                            selectedTextColor = Term.Ink,
                            indicatorColor = Term.InvertBg,
                            unselectedIconColor = Term.Faint,
                            unselectedTextColor = Term.Faint,
                        ),
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                        label = {
                            Text(
                                "FLOOR",
                                fontFamily = Term.Mono,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Term.InvertInk,
                            selectedTextColor = Term.Ink,
                            indicatorColor = Term.InvertBg,
                            unselectedIconColor = Term.Faint,
                            unselectedTextColor = Term.Faint,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        // keep the content on pure black, edge to edge
        Box(modifier = Modifier.padding(innerPadding).background(Color.Black)) {
            when (tab) {
                0 -> DashboardScreen()
                else -> DebugScreen()
            }
        }
    }
}
