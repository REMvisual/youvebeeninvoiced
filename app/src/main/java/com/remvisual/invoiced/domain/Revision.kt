package com.remvisual.invoiced.domain

import com.remvisual.invoiced.data.Invoice
import com.remvisual.invoiced.data.InvoiceStatus
import com.remvisual.invoiced.data.LineItem
import java.security.MessageDigest

/** Fingerprint of everything the client would see on the PDF. */
fun contentHash(inv: Invoice, items: List<LineItem>): String {
    val sb = StringBuilder()
    listOf(
        inv.number, inv.issueDate, inv.dueDate, inv.clientName, inv.clientEmail, inv.clientDetails,
        inv.currency, inv.discountType, inv.discountValue, inv.taxEnabled, inv.taxRate,
        inv.withholdingRate, inv.showWithholdingOnPdf, inv.notes
    ).forEach { sb.append(it).append('|') }
    for (it in items) sb.append("||").append(it.description).append('|').append(it.quantity).append('|').append(it.unitPrice)
    val digest = MessageDigest.getInstance("SHA-1").digest(sb.toString().toByteArray())
    return digest.joinToString("") { b -> "%02x".format(b) }
}

/**
 * Applied on every save. If the invoice was already sent and the content changed,
 * it becomes UPDATED and gets a new revision number (once per send cycle).
 */
fun Invoice.afterEdit(hash: String): Invoice {
    if (status == InvoiceStatus.PAID) return this
    if (sentRevision == 0) return copy(status = InvoiceStatus.DRAFT)
    val changed = sentHash.isNotEmpty() && hash != sentHash
    return if (changed) {
        copy(status = InvoiceStatus.UPDATED, revision = if (revision == sentRevision) revision + 1 else revision)
    } else {
        copy(status = InvoiceStatus.SENT)
    }
}

/** Applied when the PDF is emailed or shared. */
fun Invoice.afterSend(hash: String): Invoice {
    val base = copy(sentHash = hash, sentRevision = revision, sentAt = System.currentTimeMillis())
    return if (status == InvoiceStatus.PAID) base else base.copy(status = InvoiceStatus.SENT)
}

fun Invoice.revisionLabel(): String = if (revision > 1) "Rev $revision" else ""
