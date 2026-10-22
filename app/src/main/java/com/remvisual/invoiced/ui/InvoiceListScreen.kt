@file:OptIn(ExperimentalMaterial3Api::class)

package com.remvisual.invoiced.ui

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remvisual.invoiced.data.InvoiceStatus
import com.remvisual.invoiced.data.InvoiceWithItems
import com.remvisual.invoiced.data.Repository
import com.remvisual.invoiced.domain.computeTotals
import com.remvisual.invoiced.domain.formatDate
import com.remvisual.invoiced.domain.money
import com.remvisual.invoiced.export.CsvExporter
import com.remvisual.invoiced.export.shareFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ListFilter(val label: String) { ALL("All"), UNPAID("Unpaid"), PAID("Paid") }

@Composable
fun InvoiceListScreen(
    repo: Repository,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onClients: () -> Unit,
    onItems: () -> Unit
) {
    val invoices by repo.invoices.collectAsState(initial = emptyList())
    val settings by repo.settings.state.collectAsState()
    var filter by remember { mutableStateOf(ListFilter.ALL) }
    var payTarget by remember { mutableStateOf<InvoiceWithItems?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val shown = invoices.filter {
        when (filter) {
            ListFilter.ALL -> true
            ListFilter.UNPAID -> it.invoice.status != InvoiceStatus.PAID
            ListFilter.PAID -> it.invoice.status == InvoiceStatus.PAID
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoices") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Clients") }, onClick = { menuOpen = false; onClients() })
                        DropdownMenuItem(text = { Text("Saved items") }, onClick = { menuOpen = false; onItems() })
                        DropdownMenuItem(text = { Text("Export CSV") }, onClick = { menuOpen = false; scope.launch { exportCsv(ctx, repo) } })
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New invoice") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onNew
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (settings.businessName.isBlank()) {
                Card(onClick = onSettings, modifier = Modifier.padding(16.dp, 8.dp).fillMaxWidth()) {
                    Text(
                        "Tap to set up your business details. They appear on every invoice PDF.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ListFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            val unpaid = invoices.filter { it.invoice.status != InvoiceStatus.PAID }
            if (unpaid.isNotEmpty()) {
                val summary = unpaid.groupBy { it.invoice.currency }
                    .map { (cur, list) -> money(list.sumOf { computeTotals(it.invoice, it.items).total }, cur) }
                    .joinToString("  |  ")
                Text(
                    "Outstanding: $summary",
                    Modifier.padding(16.dp, 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (invoices.isEmpty()) "No invoices yet.\nTap New invoice to create one." else "Nothing here.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.invoice.id }) { iw ->
                        InvoiceCard(iw, onClick = { onOpen(iw.invoice.id) }, onMarkPaid = { payTarget = iw })
                    }
                }
            }
        }
    }

    payTarget?.let { target ->
        MarkPaidDialog(
            invoice = target.invoice,
            totals = computeTotals(target.invoice, target.items),
            onDismiss = { payTarget = null },
            onConfirm = { date, amount ->
                scope.launch {
                    repo.update(target.invoice.copy(status = InvoiceStatus.PAID, paidDate = date, amountReceived = amount))
                    payTarget = null
                }
            }
        )
    }
}

@Composable
private fun InvoiceCard(iw: InvoiceWithItems, onClick: () -> Unit, onMarkPaid: () -> Unit) {
    val inv = iw.invoice
    val t = computeTotals(inv, iw.items)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp, 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        inv.number + (if (inv.revision > 1) " (rev ${inv.revision})" else "") + (if (inv.clientName.isNotBlank()) "  -  ${inv.clientName}" else ""),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        formatDate(inv.issueDate) + (if (inv.dueDate != null) "   due ${formatDate(inv.dueDate)}" else ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(t.total, inv.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusLabel(inv.status)
                }
            }
            if (t.withholding > 0 && inv.status != InvoiceStatus.PAID) {
                Text("Expected net ${money(t.expectedNet, inv.currency)}", style = MaterialTheme.typography.bodySmall)
            }
            if (inv.status == InvoiceStatus.PAID && inv.amountReceived != null) {
                Text(
                    "Received ${money(inv.amountReceived, inv.currency)} on ${formatDate(inv.paidDate)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (inv.status != InvoiceStatus.PAID) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onMarkPaid) { Text("Mark paid") }
                }
            }
        }
    }
}

@Composable
fun StatusLabel(status: InvoiceStatus) {
    val color = when (status) {
        InvoiceStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
        InvoiceStatus.SENT -> Color(0xFF1E5BA8)
        InvoiceStatus.UPDATED -> Color(0xFFB26A00)
        InvoiceStatus.PAID -> Color(0xFF2E7D32)
    }
    Text(status.name, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

suspend fun exportCsv(ctx: Context, repo: Repository) {
    val all = repo.getAllOnce()
    if (all.isEmpty()) {
        Toast.makeText(ctx, "No invoices to export yet", Toast.LENGTH_SHORT).show()
        return
    }
    val files = withContext(Dispatchers.IO) { CsvExporter.export(ctx, all) }
    shareFiles(ctx, files, "text/csv", "Invoices CSV export")
}
