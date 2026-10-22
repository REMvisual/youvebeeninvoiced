@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.remvisual.invoiced.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.remvisual.invoiced.data.Backup
import com.remvisual.invoiced.data.Invoice
import androidx.compose.material3.AlertDialog
import com.remvisual.invoiced.data.Repository
import com.remvisual.invoiced.domain.Totals
import com.remvisual.invoiced.domain.today
import com.remvisual.invoiced.export.DEFAULT_EMAIL_TEMPLATE
import com.remvisual.invoiced.export.PLACEHOLDERS
import com.remvisual.invoiced.export.emailSubject
import com.remvisual.invoiced.export.renderEmail
import androidx.compose.material3.CardDefaults
import com.remvisual.invoiced.domain.fmtPlain
import com.remvisual.invoiced.domain.parseNum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(repo: Repository, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s0 = remember { repo.settings.state.value }

    var businessName by remember { mutableStateOf(s0.businessName) }
    var address by remember { mutableStateOf(s0.address) }
    var phone by remember { mutableStateOf(s0.phone) }
    var email by remember { mutableStateOf(s0.email) }
    var taxId by remember { mutableStateOf(s0.taxId) }
    var currency by remember { mutableStateOf(s0.defaultCurrency) }
    var taxRate by remember { mutableStateOf(fmtPlain(s0.defaultTaxRate)) }
    var whRate by remember { mutableStateOf(fmtPlain(s0.defaultWithholdingRate)) }
    var prefix by remember { mutableStateOf(s0.invoicePrefix) }
    var nextNumber by remember { mutableStateOf(s0.nextNumber.toString()) }
    var payment by remember { mutableStateOf(s0.paymentInstructions) }
    var notes by remember { mutableStateOf(s0.defaultNotes) }
    var logo by remember { mutableStateOf<Bitmap?>(repo.settings.loadLogo()) }
    var template by remember { mutableStateOf(s0.pdfTemplate) }
    var accent by remember { mutableStateOf(s0.accentColor) }
    var pickerOpen by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var emailTemplate by remember { mutableStateOf(s0.emailTemplate.ifBlank { DEFAULT_EMAIL_TEMPLATE }) }
    var customHex by remember { mutableStateOf(if (ACCENTS.any { it.first == s0.accentColor }) "" else "#%06X".format(s0.accentColor)) }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { repo.settings.setLogo(uri) }.getOrDefault(false) }
                if (ok) logo = repo.settings.loadLogo()
                else Toast.makeText(ctx, "Could not read that image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = Backup.export(repo)
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("open failed")
                }.isSuccess
            }
            Toast.makeText(ctx, if (ok) "Backup saved" else "Could not write the backup", Toast.LENGTH_LONG).show()
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importUri = uri }

    fun save() {
        repo.settings.save(
            s0.copy(
                businessName = businessName.trim(),
                address = address.trim(),
                phone = phone.trim(),
                email = email.trim(),
                taxId = taxId.trim(),
                defaultCurrency = currency.trim().ifBlank { "USD" },
                defaultTaxRate = parseNum(taxRate),
                defaultWithholdingRate = parseNum(whRate),
                invoicePrefix = prefix,
                nextNumber = nextNumber.trim().toIntOrNull()?.coerceAtLeast(1) ?: s0.nextNumber,
                paymentInstructions = payment.trim(),
                pdfTemplate = template,
                emailTemplate = if (emailTemplate.trim() == DEFAULT_EMAIL_TEMPLATE.trim()) "" else emailTemplate,
                accentColor = accent,
                defaultNotes = notes.trim()
            )
        )
        Toast.makeText(ctx, "Settings saved", Toast.LENGTH_SHORT).show()
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { TextButton(onClick = { save() }) { Text("Save") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("Logo") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val l = logo
                    if (l != null) {
                        Image(
                            bitmap = l.asImageBitmap(), contentDescription = "Logo",
                            contentScale = ContentScale.Fit, modifier = Modifier.size(72.dp)
                        )
                    } else {
                        Text("No logo. It prints top-left on the PDF.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        OutlinedButton(onClick = {
                            pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(if (logo == null) "Choose image" else "Change") }
                        if (logo != null) {
                            TextButton(onClick = { repo.settings.clearLogo(); logo = null }) { Text("Remove") }
                        }
                    }
                }
            }

            SectionCard("Invoice design") {
                Text("Template", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TEMPLATES.forEach { (id, name) ->
                        FilterChip(selected = template == id, onClick = { template = id }, label = { Text(name) })
                    }
                }
                Text(TEMPLATE_HINTS[template] ?: "", style = MaterialTheme.typography.bodySmall)
                Text("Accent colour", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEach { (rgb, name) ->
                        val selected = accent == rgb
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF000000.toInt() or rgb))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { accent = rgb; customHex = "" }
                                .semantics { contentDescription = name }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customHex,
                        onValueChange = { v ->
                            customHex = v.take(7)
                            parseHex(v)?.let { accent = it }
                        },
                        label = { Text("Custom colour (hex)") }, singleLine = true,
                        placeholder = { Text("#1E5BA8") },
                        isError = customHex.isNotBlank() && parseHex(customHex) == null,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or accent))
                            .clickable { pickerOpen = true }
                            .semantics { contentDescription = "Open colour wheel" }
                    )
                }
                OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Open colour wheel") }
                Text("Use Preview PDF on any invoice to see the result.", style = MaterialTheme.typography.bodySmall)
            }

            SectionCard("Your business (printed on every invoice)") {
                OutlinedTextField(businessName, { businessName = it }, label = { Text("Business / your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(taxId, { taxId = it }, label = { Text("Tax ID / business number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            SectionCard("Payment instructions") {
                Text(
                    "Bank details, PayPal, Bit, whatever you want paid to. Printed on every PDF and included in the email text.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(payment, { payment = it }, label = { Text("How to pay you") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }

            SectionCard("Email text") {
                Text(
                    "This is the message that opens in Gmail. The placeholders in braces are filled in for each invoice.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(emailTemplate, { emailTemplate = it }, label = { Text("Template") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { emailTemplate = DEFAULT_EMAIL_TEMPLATE }) { Text("Reset to default") }
                Text("Preview", style = MaterialTheme.typography.labelLarge)
                val sampleBiz = s0.copy(businessName = businessName.trim(), paymentInstructions = payment.trim(), emailTemplate = emailTemplate)
                val sampleInv = Invoice(
                    number = prefix + nextNumber.trim().ifBlank { "1" }.padStart(3, '0'), issueDate = today(), dueDate = today() + 14,
                    clientName = "Dana Levi", clientEmail = "Dana <dana@example.com>", currency = currency.trim().ifBlank { "USD" }
                )
                val sampleTotals = Totals(3500.0, 0.0, 0.0, 3500.0, 0.0, 3500.0)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Subject: " + emailSubject(sampleInv, sampleBiz), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(renderEmail(emailTemplate, sampleInv, sampleTotals, sampleBiz), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Placeholders", style = MaterialTheme.typography.labelLarge)
                PLACEHOLDERS.forEach { (k, v) -> Text("$k  -  $v", style = MaterialTheme.typography.bodySmall) }
            }

            SectionCard("Defaults for new invoices") {
                CurrencyField(value = currency, onChange = { currency = it }, modifier = Modifier.fillMaxWidth())
                NumberField("Add-on tax rate (used only when switched on)", taxRate, { taxRate = it }, Modifier.fillMaxWidth(), suffix = "%")
                NumberField("Withholding rate", whRate, { whRate = it }, Modifier.fillMaxWidth(), suffix = "%")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(prefix, { prefix = it }, label = { Text("Number prefix") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        nextNumber, { nextNumber = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Next number") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Default notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }

            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }

            SectionCard("Data") {
                Text(
                    "Backup is one JSON file with settings, logo, clients, saved items and every invoice. Use it to move to a new phone or to the renamed app.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { exportBackup.launch(Backup.suggestedFileName()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export all data (backup)")
                }
                OutlinedButton(onClick = { pickBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import backup")
                }
                OutlinedButton(onClick = { scope.launch { exportCsv(ctx, repo) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export all invoices to CSV")
                }
                Text(
                    "Everything is stored only on this phone and survives app updates. The CSV export is your backup.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("Import backup?") },
            text = { Text("Settings and logo are replaced. Clients, saved items and invoices are added; anything with the same name, description or invoice number already here is skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val json = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("open failed")
                                Backup.import(repo, json)
                            }
                        }
                        importUri = null
                        result.onSuccess { r ->
                            Toast.makeText(ctx, "Imported  invoices,  clients,  items. Skipped .", Toast.LENGTH_LONG).show()
                            onDone()
                        }.onFailure { e ->
                            Toast.makeText(ctx, "Import failed: ", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text("Cancel") } }
        )
    }

    if (pickerOpen) {
        ColorPickerDialog(
            initial = accent,
            onDismiss = { pickerOpen = false },
            onPick = { rgb -> accent = rgb; customHex = "#%06X".format(rgb); pickerOpen = false }
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

val ACCENTS: List<Pair<Int, String>> = listOf(
    0x1E5BA8 to "Blue",
    0x2E7D32 to "Green",
    0x6A1B9A to "Purple",
    0xC62828 to "Red",
    0xEF6C00 to "Orange",
    0x00838F to "Teal",
    0x212121 to "Black"
)

val TEMPLATES: List<Pair<String, String>> = listOf(
    "CLASSIC" to "Classic",
    "MODERN" to "Modern",
    "MINIMAL" to "Minimal",
    "BOLD" to "Bold",
    "ELEGANT" to "Elegant",
    "COMPACT" to "Compact"
)

val TEMPLATE_HINTS: Map<String, String> = mapOf(
    "CLASSIC" to "Grey table header, title in the accent colour, striped rows.",
    "MODERN" to "Colour band across the top, coloured table header, striped rows.",
    "MINIMAL" to "No fills, hairlines only, quiet grey headings.",
    "BOLD" to "Tall dark header with a large title, heavy table header.",
    "ELEGANT" to "Serif type, centred title between thin rules, boxed totals.",
    "COMPACT" to "Smaller type and tighter spacing, fits many items on one page."
)

fun parseHex(s: String): Int? {
    val m = Regex("^#?([0-9a-fA-F]{6})$").find(s.trim()) ?: return null
    return m.groupValues[1].toInt(16)
}
