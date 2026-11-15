package com.francodev.appops.ui.screens

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.francodev.appops.data.ManagedPermission
import com.francodev.appops.data.managedPermissions
import com.francodev.appops.ui.components.SearchField

@Composable
fun PermissionsScreen(modifier: Modifier = Modifier) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val selected = selectedIndex?.let { managedPermissions.getOrNull(it) }
    if (selected != null) {
        PermissionAppsScreen(
            permission = selected,
            onBack = { selectedIndex = null },
            modifier = modifier
        )
        return
    }

    val visiblePermissions = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            managedPermissions
        } else {
            managedPermissions.filter {
                it.name.contains(q, ignoreCase = true) || it.title.contains(q, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Permisos",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    showSearch = !showSearch
                    if (!showSearch) query = ""
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "Buscar permisos")
                }
            }
        }

        if (showSearch) {
            item {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Buscar permisos"
                )
            }
        }

        if (visiblePermissions.isEmpty()) {
            item {
                Text(
                    text = "Sin resultados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(visiblePermissions, key = { it.name }) { permission ->
            PermissionCard(
                permission = permission,
                onClick = { selectedIndex = managedPermissions.indexOf(permission) }
            )
        }
    }
}

@Composable
private fun PermissionCard(permission: ManagedPermission, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = permission.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = permission.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = permission.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}