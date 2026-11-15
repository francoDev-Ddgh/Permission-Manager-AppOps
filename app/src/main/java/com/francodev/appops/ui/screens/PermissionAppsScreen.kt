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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.francodev.appops.data.AppPermissionState
import com.francodev.appops.data.ManagedPermission
import com.francodev.appops.data.canTogglePermission
import com.francodev.appops.data.loadAppsUsingPermission
import com.francodev.appops.data.setPermissionState
import com.francodev.appops.shell.ShizukuShell
import com.francodev.appops.shell.ShizukuStatus
import com.francodev.appops.ui.components.AppRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionAppsScreen(
    permission: ManagedPermission,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // null = todavía cargando
    var apps by remember(permission) { mutableStateOf<List<AppPermissionState>?>(null) }
    var pending by remember(permission) { mutableStateOf<Set<String>>(emptySet()) }
    val shizukuReady = remember(permission) { ShizukuShell.status() == ShizukuStatus.READY }
    val canToggle = remember(permission) { canTogglePermission(permission) }

    LaunchedEffect(permission) {
        apps = loadAppsUsingPermission(context, permission)
    }

    fun changeState(entry: AppPermissionState, target: Boolean) {
        if (entry.app.packageName in pending) return
        pending = pending + entry.app.packageName
        scope.launch {
            val result = setPermissionState(entry.app.packageName, permission, target)
            if (result.success) {
                apps = apps?.map {
                    if (it.app.packageName == entry.app.packageName) it.copy(active = target) else it
                }
            } else {
                snackbarHostState.showSnackbar(
                    result.message ?: "No se pudo cambiar el permiso."
                )
            }
            pending = pending - entry.app.packageName
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            val current = apps
            when {
                current == null -> item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                current.isEmpty() -> item {
                    Text(
                        text = "Ninguna app instalada declara este permiso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                else -> items(current, key = { it.app.packageName }) { entry ->
                    AppRow(
                        app = entry.app,
                        trailing = {
                            Switch(
                                checked = entry.active,
                                onCheckedChange = { target -> changeState(entry, target) },
                                enabled = canToggle && entry.app.packageName !in pending
                            )
                        }
                    )
                }
            }
        }
    }
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
                text = "Sin Shizuku no se pueden leer ni cambiar los estados. " +
                    "Actívalo en Ajustes › Modo de Trabajo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}