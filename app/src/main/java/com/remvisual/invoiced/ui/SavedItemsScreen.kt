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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remvisual.invoiced.data.Repository
import com.remvisual.invoiced.data.SavedItem
import com.remvisual.invoiced.domain.fmtMoney
import com.remvisual.invoiced.domain.fmtPlain
import com.remvisual.invoiced.domain.parseNum
import kotlinx.coroutines.launch

@Composable
fun SavedItemsScreen(repo: Repository, onDone: () -> Unit) {
    val items by repo.savedItems.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SavedItem?>(null) }
    var deleting by remember { mutableStateOf<SavedItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved items") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New item") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { editing = SavedItem(description = "") }
            )
        }
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Things you bill for often. On an invoice, start typing in an item and pick one to fill the description and price.")
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { it ->
                    Card(onClick = { editing = it }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it.description, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(fmtMoney(it.unitPrice), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { deleting = it }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        var desc by remember(item.id) { mutableStateOf(item.description) }
        var price by remember(item.id) { mutableStateOf(fmtPlain(item.unitPrice)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (item.id == 0L) "New item" else "Edit item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    NumberField("Unit price", price, { price = it }, Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = desc.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repo.saveItem(item.copy(description = desc.trim(), unitPrice = parseNum(price)))
                            editing = null
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${item.description}\"?") },
            text = { Text("Invoices that already use it are not affected.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.deleteSavedItem(item.id); deleting = null } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}
