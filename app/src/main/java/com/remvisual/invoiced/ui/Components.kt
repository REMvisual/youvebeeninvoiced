@file:OptIn(ExperimentalMaterial3Api::class)

package com.remvisual.invoiced.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.remvisual.invoiced.data.Invoice
import com.remvisual.invoiced.domain.CURRENCIES
import com.remvisual.invoiced.domain.Recipient
import com.remvisual.invoiced.domain.encodeRecipients
import com.remvisual.invoiced.domain.parseRecipients
import com.remvisual.invoiced.domain.Totals
import com.remvisual.invoiced.domain.fmtPlain
import com.remvisual.invoiced.domain.formatDate
import com.remvisual.invoiced.domain.money
import com.remvisual.invoiced.domain.parseNum
import com.remvisual.invoiced.domain.symbol
import com.remvisual.invoiced.domain.today

private const val DAY_MS = 86_400_000L

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null
) {
    val suffixSlot: (@Composable () -> Unit)? = if (suffix == null) null else { { Text(suffix) } }
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.length <= 14) onValueChange(s) },
        label = { Text(label) },
        singleLine = true,
        suffix = suffixSlot,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = formatDate(epochDay),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true }
        )
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (epochDay ?: today()) * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.selectedDateMillis?.let { Math.floorDiv(it, DAY_MS) })
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun CurrencyField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(6)) },
            label = { Text("Currency") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose currency")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CURRENCIES.forEach { c ->
                DropdownMenuItem(
                    text = { Text("$c  (${symbol(c)})") },
                    onClick = { onChange(c); open = false }
                )
            }
        }
    }
}

@Composable
fun MarkPaidDialog(
    invoice: Invoice,
    totals: Totals,
    onDismiss: () -> Unit,
    onConfirm: (paidDate: Long, amountReceived: Double) -> Unit
) {
    var date by remember { mutableStateOf(today()) }
    var amount by remember { mutableStateOf(fmtPlain(totals.expectedNet)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark ${invoice.number} as paid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField("Paid on", date, { d -> if (d != null) date = d })
                NumberField("Amount received", amount, { amount = it }, Modifier.fillMaxWidth(), suffix = symbol(invoice.currency).trim())
                Text(
                    "Invoice total ${money(totals.total, invoice.currency)}" +
                        if (totals.withholding > 0) ", expected net ${money(totals.expectedNet, invoice.currency)}" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(date, parseNum(amount)) }) { Text("Mark paid") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Edits a list of "Name <email>" recipients stored in one string. Rows with blank fields
 * stay visible while editing even though they are not part of the encoded value.
 */
@Composable
fun RecipientsEditor(value: String, onChange: (String) -> Unit) {
    val rows = remember { mutableStateListOf<Recipient>() }
    androidx.compose.runtime.LaunchedEffect(value) {
        if (encodeRecipients(rows) != value) {
            rows.clear()
            rows.addAll(parseRecipients(value))
            if (rows.isEmpty()) rows.add(Recipient())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, r ->
            androidx.compose.runtime.key(i) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = r.name, onValueChange = { rows[i] = r.copy(name = it); onChange(encodeRecipients(rows)) },
                        label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(0.9f)
                    )
                    OutlinedTextField(
                        value = r.email, onValueChange = { rows[i] = r.copy(email = it); onChange(encodeRecipients(rows)) },
                        label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.weight(1.4f)
                    )
                    IconButton(onClick = {
                        rows.removeAt(i)
                        if (rows.isEmpty()) rows.add(Recipient())
                        onChange(encodeRecipients(rows))
                    }) { Icon(Icons.Default.Clear, contentDescription = "Remove") }
                }
            }
        }
        TextButton(onClick = { rows.add(Recipient()) }) { Text("+ Add another email") }
    }
}
