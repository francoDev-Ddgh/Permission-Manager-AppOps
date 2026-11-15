package com.francodev.appops.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.francodev.appops.data.SettingsStore
import com.francodev.appops.data.WorkMode
import com.francodev.appops.shell.ShizukuShell
import com.francodev.appops.ui.screens.AllAppsScreen
import com.francodev.appops.ui.screens.PermissionsScreen
import com.francodev.appops.ui.screens.SettingsScreen

private enum class MainTab(val label: String, val icon: ImageVector) {
    ALL_APPS("All Apps", Icons.Filled.List),
    PERMISSIONS("Permissions", Icons.Filled.Lock)
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var workMode by remember { mutableStateOf(store.getWorkMode()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Primera vez: obligar a elegir el modo de trabajo.
    if (workMode == null) {
        WorkModeChooserDialog(
            onSelect = { mode ->
                store.setWorkMode(mode)
                workMode = mode
                if (mode == WorkMode.SHIZUKU) {
                    ShizukuShell.requestPermission()
                }
            }
        )
    }

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

@Composable
private fun WorkModeChooserDialog(onSelect: (WorkMode) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Elige tu modo de trabajo") },
        text = {
            Column {
                Text(
                    text = "Define cómo la app ejecutará los cambios. Puedes cambiarlo después en Ajustes.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                WorkMode.entries.forEach { mode ->
                    Surface(
                        onClick = { onSelect(mode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (mode.experimental) {
                                Text(
                                    text = "Experimental · sin probar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
