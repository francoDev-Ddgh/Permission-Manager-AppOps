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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.francodev.appops.data.AppPermissionEntry
import com.francodev.appops.data.canTogglePermission
import com.francodev.appops.data.loadPermissionsForApp
import com.francodev.appops.data.setPermissionState
import com.francodev.appops.shell.ShizukuShell
import com.francodev.appops.shell.ShizukuStatus
import com.francodev.appops.ui.components.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var label by remember(packageName) { mutableStateOf(packageName) }
    // null = cargando
    var entries by remember(packageName) { mutableStateOf<List<AppPermissionEntry>?>(null) }
    var pending by remember(packageName) { mutableStateOf<Set<String>>(emptySet()) }
    val shizukuReady = remember(packageName) { ShizukuShell.status() == ShizukuStatus.READY }

    LaunchedEffect(packageName) {
        label = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0))
                    .toString()
            }.getOrDefault(packageName)
        }
        entries = loadPermissionsForApp(context, packageName)
    }

    fun changeState(entry: AppPermissionEntry, target: Boolean) {
        val key = entry.permission.name
        if (key in pending) return
        pending = pending + key
        scope.launch {
            val result = setPermissionState(packageName, entry.permission, target)
            if (result.success) {
                entries = entries?.map {
                    if (it.permission.name == key) it.copy(active = target) else it
                }
                result.message?.let { snackbarHostState.showSnackbar(it) }
            } else {
                snackbarHostState.showSnackbar(
                    result.message ?: "No se pudo cambiar el permiso."
                )
            }
            pending = pending - key
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
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
                AppHeader(packageName = packageName, label = label)
            }

            if (!shizukuReady) {
                item { ShizukuNotice() }
            }

            val current = entries
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
                        text = "Esta app no usa ninguno de los permisos administrables.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                else -> items(current, key = { it.permission.name }) { entry ->
                    PermissionToggleRow(
                        entry = entry,
                        enabled = canTogglePermission(entry.permission) &&
                            entry.permission.name !in pending,
                        onChange = { target -> changeState(entry, target) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader(packageName: String, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = packageName, size = 72.dp, corner = 20.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionToggleRow(
    entry: AppPermissionEntry,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.permission.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.permission.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = entry.active,
                onCheckedChange = onChange,
                enabled = enabled
            )
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