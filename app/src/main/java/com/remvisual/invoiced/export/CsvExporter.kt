package com.remvisual.invoiced.export

import android.content.Context
import com.remvisual.invoiced.data.InvoiceWithItems
import com.remvisual.invoiced.data.sortedItems
import com.remvisual.invoiced.domain.computeTotals
import com.remvisual.invoiced.domain.isoDate
import com.remvisual.invoiced.domain.recipientsDisplay
import java.io.File
import java.time.LocalDate
import java.util.Locale

object CsvExporter {

    fun export(context: Context, all: List<InvoiceWithItems>): List<File> {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val stamp = LocalDate.now().toString()
        val invoicesFile = File(dir, "invoices-$stamp.csv")
        val itemsFile = File(dir, "invoice-items-$stamp.csv")

        val inv = StringBuilder("﻿")
        inv.appendLine(
            row(
                "Number", "Revision", "Status", "Issue date", "Due date", "Client", "Client email", "Client details", "Currency",
                "Subtotal", "Discount", "Tax rate %", "Tax", "Total",
                "Withholding rate %", "Withholding", "Expected net",
                "Paid date", "Amount received", "Notes"
            )
        )
        val items = StringBuilder("﻿")
        items.appendLine(row("Invoice number", "Client", "Issue date", "Currency", "Description", "Qty", "Unit price", "Amount"))

        for (iw in all) {
            val i = iw.invoice
            val t = computeTotals(i, iw.items)
            inv.appendLine(
                row(
                    i.number, i.revision.toString(), i.status.name, isoDate(i.issueDate), isoDate(i.dueDate),
                    i.clientName, recipientsDisplay(i.clientEmail), i.clientDetails, i.currency,
                    num(t.subtotal), num(t.discount),
                    if (i.taxEnabled) num(i.taxRate) else "0.00", num(t.tax), num(t.total),
                    num(i.withholdingRate), num(t.withholding), num(t.expectedNet),
                    isoDate(i.paidDate), i.amountReceived?.let { num(it) } ?: "", i.notes
                )
            )
            for (li in iw.sortedItems) {
                items.appendLine(
                    row(
                        i.number, i.clientName, isoDate(i.issueDate), i.currency,
                        li.description, num(li.quantity), num(li.unitPrice), num(li.quantity * li.unitPrice)
                    )
                )
            }
        }
        invoicesFile.writeText(inv.toString())
        itemsFile.writeText(items.toString())
        return listOf(invoicesFile, itemsFile)
    }

    private fun row(vararg cells: String): String = cells.joinToString(",") { esc(it) }

    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun num(d: Double): String = String.format(Locale.US, "%.2f", d)
}
