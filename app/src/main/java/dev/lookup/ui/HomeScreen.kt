package dev.lookup.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Main screen: the dashboard over the always-on service, plus the debug view.
 */
@Composable
fun HomeScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Dashboard") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    label = { Text("Debug") },
                )
            }
        },
    ) { innerPadding ->
        when (tab) {
            0 -> DashboardScreen(Modifier.padding(innerPadding))
            else -> DebugScreen(Modifier.padding(innerPadding))
        }
    }
}
