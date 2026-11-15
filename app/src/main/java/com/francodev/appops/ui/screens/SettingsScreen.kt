package com.francodev.appops.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.francodev.appops.data.SettingsStore
import com.francodev.appops.data.WorkMode
import com.francodev.appops.shell.ShizukuShell
import com.francodev.appops.shell.ShizukuStatus
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var openWorkMode by rememberSaveable { mutableStateOf(false) }

    if (openWorkMode) {
        BackHandler(onBack = { openWorkMode = false })
        WorkModeScreen(store = store, onBack = { openWorkMode = false })
    } else {
        BackHandler(onBack = onBack)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ajustes") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        onClick = { openWorkMode = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Modo de Trabajo",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = store.getWorkMode()?.label ?: "Sin definir",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkModeScreen(store: SettingsStore, onBack: () -> Unit) {
    var selected by remember { mutableStateOf(store.getWorkMode()) }
    var shizukuStatus by remember { mutableStateOf(ShizukuShell.status()) }

    DisposableEffect(Unit) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
            shizukuStatus = ShizukuShell.status()
        }
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            shizukuStatus = ShizukuShell.status()
            if (shizukuStatus == ShizukuStatus.NO_PERMISSION) {
                ShizukuShell.requestPermission()
            }
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            shizukuStatus = ShizukuShell.status()
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        // Si Shizuku ya está activo, pedimos permiso al entrar para que la app aparezca en Shizuku.
        if (shizukuStatus == ShizukuStatus.NO_PERMISSION) {
            ShizukuShell.requestPermission()
        }

        onDispose {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modo de Trabajo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(WorkMode.entries) { mode ->
                WorkModeOption(
                    mode = mode,
                    selected = selected == mode,
                    onSelect = {
                        selected = mode
                        store.setWorkMode(mode)
                        shizukuStatus = ShizukuShell.status()
                        if (mode == WorkMode.SHIZUKU && shizukuStatus == ShizukuStatus.NO_PERMISSION) {
                            ShizukuShell.requestPermission()
                        }
                    }
                )
            }
            if (selected == WorkMode.SHIZUKU) {
                item {
                    ShizukuStatusCard(
                        status = shizukuStatus,
                        onRequest = { ShizukuShell.requestPermission() }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkModeOption(mode: WorkMode, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            Spacer(Modifier.width(12.dp))
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ShizukuStatusCard(status: ShizukuStatus, onRequest: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Estado de Shizuku",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (status) {
                    ShizukuStatus.NOT_RUNNING -> "Shizuku no está conectado. Abre Shizuku, inícialo y vuelve a esta pantalla."
                    ShizukuStatus.NO_PERMISSION -> "Shizuku está activo, pero esta app todavía no tiene permiso."
                    ShizukuStatus.READY -> "Shizuku está listo para ejecutar comandos."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status == ShizukuStatus.NO_PERMISSION) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequest) {
                    Text("Pedir permiso")
                }
            }
        }
    }
}
