package com.francodev.appops.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.francodev.appops.ui.screens.AllAppsScreen
import com.francodev.appops.ui.screens.PermissionsScreen
import com.francodev.appops.ui.screens.SettingsScreen

private enum class MainTab(val label: String, val icon: ImageVector) {
    ALL_APPS("All Apps", Icons.Filled.List),
    PERMISSIONS("Permissions", Icons.Filled.Lock)
}

@Composable
fun AppRoot() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (MainTab.entries[selectedTab]) {
            MainTab.ALL_APPS -> AllAppsScreen(onOpenSettings = { showSettings = true }, modifier = modifier)
            MainTab.PERMISSIONS -> PermissionsScreen(modifier = modifier)
        }
    }
}
