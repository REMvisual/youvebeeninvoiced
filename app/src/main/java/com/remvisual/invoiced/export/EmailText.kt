package com.remvisual.invoiced.export

import com.remvisual.invoiced.data.BusinessSettings
import com.remvisual.invoiced.data.Invoice
import com.remvisual.invoiced.domain.Totals
import com.remvisual.invoiced.domain.formatDate
import com.remvisual.invoiced.domain.greeting
import com.remvisual.invoiced.domain.money

/** Default body. Placeholders are filled in at send time; see [PLACEHOLDERS]. */
const val DEFAULT_EMAIL_TEMPLATE = "{greeting}\n\n{attached_line}\n\n{signoff}"

val PLACEHOLDERS: List<Pair<String, String>> = listOf(
    "{greeting}" to "\"Hi Dana,\" for one named recipient, otherwise \"Hello!\"",
    "{attached_line}" to "\"Please find invoice INV-003 attached.\" (mentions the revision on a resend)",
    "{signoff}" to "\"Thanks,\" plus your business name",
    "{number}" to "Invoice number",
    "{rev}" to "Revision number",
    "{client}" to "Client name",
    "{total}" to "Invoice total with currency",
    "{due}" to "Due date",
    "{business}" to "Your business name",
    "{payment}" to "Your payment instructions"
)

fun emailSubject(inv: Invoice, biz: BusinessSettings): String {
    val rev = if (inv.revision > 1) " (rev ${inv.revision})" else ""
    return if (biz.businessName.isBlank()) "Invoice ${inv.number}$rev" else "Invoice ${inv.number}$rev from ${biz.businessName}"
}

fun attachedLine(inv: Invoice): String = if (inv.revision > 1)
    "Please find the updated invoice ${inv.number} (rev ${inv.revision}) attached. It replaces the earlier version."
else
    "Please find invoice ${inv.number} attached."

fun signoff(biz: BusinessSettings): String = if (biz.businessName.isBlank()) "Thanks!" else "Thanks,\n${biz.businessName}"

fun renderEmail(template: String, inv: Invoice, totals: Totals, biz: BusinessSettings): String {
    val t = template.ifBlank { DEFAULT_EMAIL_TEMPLATE }
    return t
        .replace("{greeting}", greeting(inv.clientEmail))
        .replace("{attached_line}", attachedLine(inv))
        .replace("{signoff}", signoff(biz))
        .replace("{number}", inv.number)
        .replace("{rev}", inv.revision.toString())
        .replace("{client}", inv.clientName)
        .replace("{total}", money(totals.total, inv.currency))
        .replace("{due}", formatDate(inv.dueDate))
        .replace("{business}", biz.businessName)
        .replace("{payment}", biz.paymentInstructions)
        .trimEnd() + "\n"
}

fun emailBody(inv: Invoice, totals: Totals, biz: BusinessSettings): String =
    renderEmail(biz.emailTemplate, inv, totals, biz)
