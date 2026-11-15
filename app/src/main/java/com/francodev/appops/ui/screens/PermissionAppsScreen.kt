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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.francodev.appops.data.AppPermissionState
import com.francodev.appops.data.ManagedPermission
import com.francodev.appops.data.loadAppsUsingPermission
import com.francodev.appops.shell.ShizukuShell
import com.francodev.appops.shell.ShizukuStatus
import com.francodev.appops.ui.components.AppRow

private sealed interface UsageState {
    data object Loading : UsageState
    data class Ready(val apps: List<AppPermissionState>) : UsageState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionAppsScreen(
    permission: ManagedPermission,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usage by produceState<UsageState>(UsageState.Loading, permission) {
        value = UsageState.Loading
        value = UsageState.Ready(loadAppsUsingPermission(context, permission))
    }
    val shizukuReady = remember(permission) { ShizukuShell.status() == ShizukuStatus.READY }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(permission.title) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column {
                    Text(
                        text = permission.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = permission.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!shizukuReady) {
                item { ShizukuNotice() }
            }

            when (val state = usage) {
                UsageState.Loading -> item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UsageState.Ready -> {
                    if (state.apps.isEmpty()) {
                        item {
                            Text(
                                text = "Ninguna app instalada declara este permiso.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(state.apps, key = { it.app.packageName }) { entry ->
                            AppRow(
                                app = entry.app,
                                trailing = { StatusLabel(active = entry.active) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(active: Boolean) {
    Text(
        text = if (active) "Activo" else "Inactivo",
        style = MaterialTheme.typography.labelLarge,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun ShizukuNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Shizuku no está conectado",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sin Shizuku el estado se toma del permiso concedido y puede no reflejar " +
                    "los bloqueos hechos con app ops. Actívalo en Ajustes › Modo de Trabajo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}