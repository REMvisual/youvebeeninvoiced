@file:OptIn(ExperimentalMaterial3Api::class)

package com.remvisual.invoiced.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remvisual.invoiced.data.Client
import com.remvisual.invoiced.data.Repository
import com.remvisual.invoiced.domain.recipientsDisplay
import kotlinx.coroutines.launch

@Composable
fun ClientsScreen(repo: Repository, onDone: () -> Unit) {
    val clients by repo.clients.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Client?>(null) }
    var deleting by remember { mutableStateOf<Client?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clients") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New client") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { editing = Client(name = "") }
            )
        }
    ) { pad ->
        if (clients.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No clients yet. They are added automatically when you save an invoice.")
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(clients, key = { it.id }) { c ->
                    Card(onClick = { editing = c }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                if (c.email.isNotBlank()) Text(recipientsDisplay(c.email), style = MaterialTheme.typography.bodySmall)
                                if (c.details.isNotBlank()) Text(c.details, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            IconButton(onClick = { deleting = c }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { c ->
        ClientDialog(
            client = c,
            onDismiss = { editing = null },
            onSave = { saved -> scope.launch { repo.saveClient(saved); editing = null } }
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${c.name}?") },
            text = { Text("Existing invoices keep their client details.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.deleteClient(c.id); deleting = null } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ClientDialog(client: Client, onDismiss: () -> Unit, onSave: (Client) -> Unit) {
    var name by remember { mutableStateOf(client.name) }
    var email by remember { mutableStateOf(client.email) }
    var details by remember { mutableStateOf(client.details) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (client.id == 0L) "New client" else "Edit client") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                RecipientsEditor(value = email, onChange = { email = it })
                OutlinedTextField(details, { details = it }, label = { Text("Address / phone") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(client.copy(name = name.trim(), email = email.trim(), details = details.trim())) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
