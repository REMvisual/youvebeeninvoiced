@file:OptIn(ExperimentalMaterial3Api::class)

package com.remvisual.invoiced.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.remvisual.invoiced.data.Client
import com.remvisual.invoiced.data.DiscountType
import com.remvisual.invoiced.data.Invoice
import com.remvisual.invoiced.data.InvoiceStatus
import com.remvisual.invoiced.data.LineItem
import com.remvisual.invoiced.data.Repository
import com.remvisual.invoiced.data.SavedItem
import com.remvisual.invoiced.data.sortedItems
import com.remvisual.invoiced.domain.Totals
import com.remvisual.invoiced.domain.afterEdit
import com.remvisual.invoiced.domain.afterSend
import com.remvisual.invoiced.domain.computeTotals
import com.remvisual.invoiced.domain.contentHash
import com.remvisual.invoiced.domain.fmtPlain
import com.remvisual.invoiced.domain.fmtRate
import com.remvisual.invoiced.domain.formatDate
import com.remvisual.invoiced.domain.money
import com.remvisual.invoiced.domain.parseNum
import com.remvisual.invoiced.domain.parseNumOrNull
import com.remvisual.invoiced.domain.recipientEmails
import com.remvisual.invoiced.domain.recipientsDisplay
import com.remvisual.invoiced.domain.symbol
import com.remvisual.invoiced.domain.today
import com.remvisual.invoiced.export.PdfGenerator
import com.remvisual.invoiced.export.emailBody
import com.remvisual.invoiced.export.emailPdf
import com.remvisual.invoiced.export.emailSubject
import com.remvisual.invoiced.export.shareFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ItemDraft(
    val uid: Long,
    val description: String = "",
    val qty: String = "1",
    val price: String = ""
)

private fun ItemDraft.toLineItem() = LineItem(
    description = description.trim(),
    quantity = parseNumOrNull(qty) ?: 1.0,
    unitPrice = parseNum(price)
)

private fun ItemDraft.amount(): Double = (parseNumOrNull(qty) ?: 1.0) * parseNum(price)

private fun sentAtText(millis: Long?): String =
    millis?.let { DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: ""

@Composable
fun InvoiceEditScreen(repo: Repository, invoiceId: Long?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clients by repo.clients.collectAsState(initial = emptyList())
    val savedItems by repo.savedItems.collectAsState(initial = emptyList())

    var loaded by remember { mutableStateOf(false) }
    var inv by remember { mutableStateOf(Invoice(number = "", issueDate = today(), dueDate = null)) }
    val items = remember { mutableStateListOf<ItemDraft>() }
    var discountText by remember { mutableStateOf("") }
    var taxText by remember { mutableStateOf("") }
    var whText by remember { mutableStateOf("") }
    var isNew by remember { mutableStateOf(invoiceId == null) }
    var nextUid by remember { mutableStateOf(1L) }
    var menuOpen by remember { mutableStateOf(false) }
    var payDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(invoiceId) {
        if (invoiceId == null) {
            val s = repo.settings.state.value
            inv = Invoice(
                number = repo.settings.previewNextNumber(),
                issueDate = today(),
                dueDate = today() + 14,
                currency = s.defaultCurrency,
                taxRate = s.defaultTaxRate,
                withholdingRate = s.defaultWithholdingRate,
                notes = s.defaultNotes
            )
            items.clear()
            items.add(ItemDraft(uid = nextUid++))
        } else {
            val iw = repo.get(invoiceId)
            if (iw == null) {
                onDone()
                return@LaunchedEffect
            }
            inv = iw.invoice
            items.clear()
            for (li in iw.sortedItems) {
                items.add(ItemDraft(nextUid++, li.description, fmtPlain(li.quantity).ifBlank { "0" }, fmtPlain(li.unitPrice)))
            }
        }
        discountText = fmtPlain(inv.discountValue)
        taxText = fmtPlain(inv.taxRate)
        whText = fmtPlain(inv.withholdingRate)
        loaded = true
    }

    fun currentInvoice(): Invoice = inv.copy(
        number = inv.number.trim(),
        clientName = inv.clientName.trim(),
        clientEmail = inv.clientEmail.trim(),
        discountValue = parseNum(discountText),
        taxRate = parseNum(taxText),
        withholdingRate = parseNum(whText)
    )

    fun currentItems(): List<LineItem> =
        items.filter { it.description.isNotBlank() || parseNum(it.price) != 0.0 }.map { it.toLineItem() }

    /** Saves the current state. Bumps the revision and marks UPDATED when a sent invoice changed. */
    suspend fun persist(): Invoice {
        val lines = currentItems()
        val i = currentInvoice().let { it.afterEdit(contentHash(it, lines)) }
        val id = repo.save(i, lines)
        if (isNew) {
            repo.settings.noteNumberUsed(i.number)
            isNew = false
        }
        inv = i.copy(id = id)
        return inv
    }

    suspend fun buildPdf(saved: Invoice): File {
        val biz = repo.settings.state.value
        val lines = currentItems()
        return withContext(Dispatchers.IO) {
            val logo = repo.settings.loadLogo()
            PdfGenerator.generate(ctx, biz, saved, lines, logo)
        }
    }

    suspend fun recordSent(saved: Invoice) {
        val sent = saved.afterSend(contentHash(saved, currentItems()))
        repo.update(sent)
        inv = sent
    }

    fun save() {
        scope.launch {
            persist()
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    fun sendByEmail() {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val saved = persist()
                val file = buildPdf(saved)
                recordSent(saved)
                val biz = repo.settings.state.value
                val to = recipientEmails(saved.clientEmail)
                Toast.makeText(
                    ctx,
                    if (to.isEmpty()) "No client email on this invoice" else "To: " + to.joinToString(", "),
                    Toast.LENGTH_LONG
                ).show()
                emailPdf(ctx, file, to, emailSubject(saved, biz), emailBody(saved, computeTotals(saved, currentItems()), biz))
            } finally {
                busy = false
            }
        }
    }

    fun shareElsewhere() {
        scope.launch {
            val saved = persist()
            val file = buildPdf(saved)
            recordSent(saved)
            val biz = repo.settings.state.value
            shareFiles(ctx, listOf(file), "application/pdf", emailSubject(saved, biz))
        }
    }

    fun preview() {
        scope.launch {
            val saved = persist()
            previewFile = buildPdf(saved)
        }
    }

    val totals = computeTotals(currentInvoice(), currentItems())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (invoiceId == null) "New invoice" else inv.number) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Preview PDF") }, onClick = { menuOpen = false; preview() })
                        DropdownMenuItem(text = { Text("Share PDF (WhatsApp, Drive...)") }, onClick = { menuOpen = false; shareElsewhere() })
                        HorizontalDivider()
                        if (inv.status != InvoiceStatus.PAID) {
                            DropdownMenuItem(text = { Text("Mark paid") }, onClick = {
                                menuOpen = false
                                scope.launch { persist(); payDialog = true }
                            })
                        } else {
                            DropdownMenuItem(text = { Text("Mark unpaid") }, onClick = {
                                menuOpen = false
                                scope.launch {
                                    inv = currentInvoice().copy(status = InvoiceStatus.SENT, paidDate = null, amountReceived = null)
                                    persist()
                                }
                            })
                        }
                        if (inv.id != 0L) {
                            DropdownMenuItem(text = { Text("Delete invoice") }, onClick = { menuOpen = false; deleteDialog = true })
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.navigationBarsPadding().padding(16.dp, 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Total", style = MaterialTheme.typography.labelSmall)
                        Text(money(totals.total, inv.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { save() }) { Text("Save") }
                    Button(onClick = { sendByEmail() }, enabled = !busy) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (inv.status == InvoiceStatus.UPDATED) "Send again" else "Send")
                    }
                }
            }
        }
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusBanner(inv, onSendAgain = { sendByEmail() })
                EditBody(
                    inv = inv, onInv = { inv = it },
                    clients = clients, savedItems = savedItems, startCollapsed = invoiceId != null,
                    items = items, onAddItem = { items.add(ItemDraft(uid = nextUid++)) },
                    discountText = discountText, onDiscount = { discountText = it },
                    taxText = taxText, onTax = { taxText = it },
                    whText = whText, onWh = { whText = it },
                    totals = totals
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    previewFile?.let { f ->
        PdfPreviewDialog(
            file = f,
            title = inv.number + (if (inv.revision > 1) "  rev ${inv.revision}" else ""),
            onDismiss = { previewFile = null },
            onSend = { previewFile = null; sendByEmail() }
        )
    }
    if (payDialog) {
        MarkPaidDialog(
            invoice = inv, totals = totals,
            onDismiss = { payDialog = false },
            onConfirm = { date, amount ->
                scope.launch {
                    inv = currentInvoice().copy(status = InvoiceStatus.PAID, paidDate = date, amountReceived = amount)
                    persist()
                    payDialog = false
                }
            }
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete ${inv.number}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (inv.id != 0L) repo.delete(inv.id)
                        deleteDialog = false
                        onDone()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatusBanner(inv: Invoice, onSendAgain: () -> Unit) {
    val container = when (inv.status) {
        InvoiceStatus.UPDATED -> MaterialTheme.colorScheme.tertiaryContainer
        InvoiceStatus.PAID -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusLabel(inv.status)
                    if (inv.revision > 1) Text("rev ${inv.revision}", style = MaterialTheme.typography.labelMedium)
                }
                val line = when (inv.status) {
                    InvoiceStatus.DRAFT -> "Not sent yet."
                    InvoiceStatus.SENT -> "Sent ${sentAtText(inv.sentAt)}" + (if (inv.sentRevision > 1) " (rev ${inv.sentRevision})" else "")
                    InvoiceStatus.UPDATED -> "Changed since rev ${inv.sentRevision} was sent ${sentAtText(inv.sentAt)}. Send the new version."
                    InvoiceStatus.PAID -> "Paid on ${formatDate(inv.paidDate)}" +
                        (if (inv.amountReceived != null) ", received ${money(inv.amountReceived, inv.currency)}" else "")
                }
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
            if (inv.status == InvoiceStatus.UPDATED) {
                TextButton(onClick = onSendAgain) { Text("Send again") }
            }
        }
    }
}

@Composable
private fun ClientNameField(value: String, clients: List<Client>, onChange: (String) -> Unit, onPick: (Client) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val matches = remember(value, clients) {
        if (value.isBlank()) emptyList()
        else clients.filter { it.name.contains(value.trim(), ignoreCase = true) && !it.name.equals(value.trim(), ignoreCase = true) }.take(5)
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); open = true },
            label = { Text("Client name") },
            singleLine = true,
            supportingText = if (clients.isEmpty()) null else ({ Text("Start typing to pick a saved client") }),
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = open && matches.isNotEmpty(),
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = false)
        ) {
            matches.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(c.name)
                            if (c.email.isNotBlank()) Text(recipientsDisplay(c.email), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = { onPick(c); open = false }
                )
            }
        }
    }
}

@Composable
private fun EditBody(
    inv: Invoice, onInv: (Invoice) -> Unit,
    clients: List<Client>,
    savedItems: List<SavedItem>,
    startCollapsed: Boolean,
    items: MutableList<ItemDraft>, onAddItem: () -> Unit,
    discountText: String, onDiscount: (String) -> Unit,
    taxText: String, onTax: (String) -> Unit,
    whText: String, onWh: (String) -> Unit,
    totals: Totals
) {
    val cur = inv.currency
    var showClient by remember { mutableStateOf(!startCollapsed) }
    var showInvoice by remember { mutableStateOf(!startCollapsed) }
    var showNotes by remember { mutableStateOf(!startCollapsed) }

    val clientSummary = listOf(inv.clientName.ifBlank { "No client yet" }, recipientsDisplay(inv.clientEmail)).filter { it.isNotBlank() }.joinToString("  -  ")
    CollapsibleCard("Client", clientSummary, showClient, { showClient = !showClient }) {
        ClientNameField(
            value = inv.clientName, clients = clients,
            onChange = { onInv(inv.copy(clientName = it)) },
            onPick = { c ->
                onInv(inv.copy(
                    clientName = c.name,
                    clientEmail = c.email.ifBlank { inv.clientEmail },
                    clientDetails = c.details.ifBlank { inv.clientDetails }
                ))
            }
        )
        Text("Email recipients (name is used for the greeting)", style = MaterialTheme.typography.labelLarge)
        RecipientsEditor(value = inv.clientEmail, onChange = { onInv(inv.copy(clientEmail = it)) })
        OutlinedTextField(
            value = inv.clientDetails, onValueChange = { onInv(inv.copy(clientDetails = it)) },
            label = { Text("Address / phone") }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )
    }

    val invoiceSummary = inv.number.ifBlank { "No number" } + "  -  " + formatDate(inv.issueDate) + (if (inv.dueDate != null) "  -  due " + formatDate(inv.dueDate) else "") + "  -  " + cur
    CollapsibleCard("Invoice", invoiceSummary, showInvoice, { showInvoice = !showInvoice }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = inv.number, onValueChange = { onInv(inv.copy(number = it)) },
                label = { Text("Invoice #") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            CurrencyField(value = cur, onChange = { onInv(inv.copy(currency = it)) }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField("Date", inv.issueDate, { d -> if (d != null) onInv(inv.copy(issueDate = d)) }, Modifier.weight(1f))
            DateField("Due", inv.dueDate, { d -> onInv(inv.copy(dueDate = d)) }, Modifier.weight(1f))
        }
    }

    SectionCard("Items") {
        items.forEachIndexed { idx, item ->
            key(item.uid) {
                ItemRow(
                    item = item, currency = cur, index = idx + 1, savedItems = savedItems,
                    onChange = { items[idx] = it },
                    onDelete = { items.removeAt(idx) }
                )
            }
        }
        OutlinedButton(onClick = onAddItem, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add item")
        }
    }

    AdjustmentsCard(inv, onInv, discountText, onDiscount, taxText, onTax, whText, onWh, totals)

    CollapsibleCard("Notes", inv.notes.lineSequence().firstOrNull { it.isNotBlank() } ?: "No notes", showNotes, { showNotes = !showNotes }) {
        OutlinedTextField(
            value = inv.notes, onValueChange = { onInv(inv.copy(notes = it)) },
            label = { Text("Printed on the PDF under the totals") }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )
    }

    TotalsCard(inv, totals)
}

@Composable
private fun AdjustmentsCard(
    inv: Invoice, onInv: (Invoice) -> Unit,
    discountText: String, onDiscount: (String) -> Unit,
    taxText: String, onTax: (String) -> Unit,
    whText: String, onWh: (String) -> Unit,
    totals: Totals
) {
    val cur = inv.currency
    val parts = ArrayList<String>()
    if (totals.discount > 0) parts.add("discount -" + money(totals.discount, cur))
    if (inv.taxEnabled) parts.add("tax ${fmtRate(inv.taxRate)}%")
    if (totals.withholding > 0) parts.add("withholding ${fmtRate(inv.withholdingRate)}%")
    val summary = if (parts.isEmpty()) "None applied" else parts.joinToString(", ")
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Discount, tax & withholding", style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Discount", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = inv.discountType == DiscountType.NONE, onClick = { onInv(inv.copy(discountType = DiscountType.NONE)) }, label = { Text("None") })
                        FilterChip(selected = inv.discountType == DiscountType.PERCENT, onClick = { onInv(inv.copy(discountType = DiscountType.PERCENT)) }, label = { Text("%") })
                        FilterChip(selected = inv.discountType == DiscountType.FIXED, onClick = { onInv(inv.copy(discountType = DiscountType.FIXED)) }, label = { Text("Fixed") })
                    }
                    if (inv.discountType != DiscountType.NONE) {
                        NumberField(
                            label = if (inv.discountType == DiscountType.PERCENT) "Discount %" else "Discount amount",
                            value = discountText, onValueChange = onDiscount, modifier = Modifier.fillMaxWidth(),
                            suffix = if (inv.discountType == DiscountType.PERCENT) "%" else symbol(cur).trim()
                        )
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Add tax on top (e.g. VAT)", style = MaterialTheme.typography.labelLarge)
                        Switch(checked = inv.taxEnabled, onCheckedChange = { onInv(inv.copy(taxEnabled = it)) })
                    }
                    if (inv.taxEnabled) NumberField("Tax rate", taxText, onTax, Modifier.fillMaxWidth(), suffix = "%")
                    HorizontalDivider()
                    Text("Withholding (deducted by the payer)", style = MaterialTheme.typography.labelLarge)
                    NumberField("Withholding rate", whText, onWh, Modifier.fillMaxWidth(), suffix = "%")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Show withholding on the PDF", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = inv.showWithholdingOnPdf, onCheckedChange = { onInv(inv.copy(showWithholdingOnPdf = it)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ItemDraft, currency: String, index: Int, savedItems: List<SavedItem>, onChange: (ItemDraft) -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val matches = remember(item.description, savedItems) {
        val q = item.description.trim()
        if (q.isBlank()) emptyList()
        else savedItems.filter { it.description.contains(q, ignoreCase = true) && !it.description.equals(q, ignoreCase = true) }.take(5)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = item.description, onValueChange = { onChange(item.copy(description = it)); open = true },
                    label = { Text("Item $index") }, modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = open && matches.isNotEmpty(),
                    onDismissRequest = { open = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    matches.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.description + "   " + money(s.unitPrice, currency)) },
                            onClick = {
                                onChange(item.copy(description = s.description, price = fmtPlain(s.unitPrice)))
                                open = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remove item") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Qty", item.qty, { onChange(item.copy(qty = it)) }, Modifier.weight(0.8f))
            NumberField("Unit price", item.price, { onChange(item.copy(price = it)) }, Modifier.weight(1.2f))
            Text(
                money(item.amount(), currency),
                Modifier.weight(1.1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun TotalsCard(inv: Invoice, t: Totals) {
    val cur = inv.currency
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TotalLine("Subtotal", money(t.subtotal, cur))
            if (t.discount > 0) TotalLine("Discount", "-" + money(t.discount, cur))
            if (inv.taxEnabled) TotalLine("Tax (${fmtRate(inv.taxRate)}%)", money(t.tax, cur))
            TotalLine("Total (on invoice)", money(t.total, cur), bold = true)
            if (t.withholding > 0) {
                TotalLine("Withholding (${fmtRate(inv.withholdingRate)}%)", "-" + money(t.withholding, cur))
                TotalLine("Expected net to you", money(t.expectedNet, cur), bold = true)
            }
            if (inv.status == InvoiceStatus.PAID && inv.amountReceived != null) {
                TotalLine("Actually received", money(inv.amountReceived, cur), bold = true)
            }
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (!expanded) Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}
