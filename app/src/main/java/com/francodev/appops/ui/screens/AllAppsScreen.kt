package com.francodev.appops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.francodev.appops.data.UserApp
import com.francodev.appops.data.loadUserApps
import com.francodev.appops.ui.components.AppRow
import com.francodev.appops.ui.components.SearchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AllAppsScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }

    // Se carga una sola vez y se conserva al entrar y salir del detalle.
    val apps by produceState(initialValue = emptyList<UserApp>(), context) {
        value = withContext(Dispatchers.IO) { loadUserApps(context.packageManager) }
    }

    val current = selectedPackage
    if (current != null) {
        AppDetailScreen(
            packageName = current,
            onBack = { selectedPackage = null },
            modifier = modifier
        )
        return
    }

    val visibleApps = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Buscar apps",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    onClick = { selectedPackage = app.packageName }
                )
            }
        }
    }
}
