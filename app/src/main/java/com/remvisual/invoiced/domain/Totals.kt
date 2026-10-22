package com.remvisual.invoiced.domain

import com.remvisual.invoiced.data.DiscountType
import com.remvisual.invoiced.data.Invoice
import com.remvisual.invoiced.data.LineItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Totals(
    val subtotal: Double,
    val discount: Double,
    val tax: Double,
    val total: Double,
    val withholding: Double,
    val expectedNet: Double
)

fun computeTotals(invoice: Invoice, items: List<LineItem>): Totals {
    val subtotal = items.sumOf { it.quantity * it.unitPrice }
    val rawDiscount = when (invoice.discountType) {
        DiscountType.NONE -> 0.0
        DiscountType.PERCENT -> subtotal * invoice.discountValue / 100.0
        DiscountType.FIXED -> invoice.discountValue
    }
    val discount = rawDiscount.coerceIn(0.0, maxOf(subtotal, 0.0))
    val afterDiscount = subtotal - discount
    val tax = if (invoice.taxEnabled) afterDiscount * invoice.taxRate / 100.0 else 0.0
    val total = afterDiscount + tax
    val withholding = (total * invoice.withholdingRate / 100.0).coerceAtLeast(0.0)
    return Totals(subtotal, discount, tax, total, withholding, total - withholding)
}

val CURRENCIES = listOf("USD", "ILS", "EUR", "GBP")

fun symbol(currency: String): String = when (currency.trim().uppercase()) {
    "USD" -> "$"
    "ILS", "NIS" -> "₪"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> if (currency.length <= 1) currency else "$currency "
}

fun fmtMoney(amount: Double): String = String.format(Locale.US, "%,.2f", amount)

fun money(amount: Double, currency: String): String {
    val s = symbol(currency)
    return if (amount < 0) "-$s${fmtMoney(-amount)}" else "$s${fmtMoney(amount)}"
}

fun fmtRate(rate: Double): String =
    if (rate % 1.0 == 0.0) rate.toLong().toString() else String.format(Locale.US, "%.2f", rate).trimEnd('0')

fun fmtQty(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString() else String.format(Locale.US, "%.2f", q).trimEnd('0')

/** Plain number for editing in a text field: no grouping, no trailing zeros, blank for zero. */
fun fmtPlain(d: Double): String = when {
    d == 0.0 -> ""
    d % 1.0 == 0.0 -> d.toLong().toString()
    else -> String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')
}

fun parseNumOrNull(s: String): Double? = s.trim().replace(",", "").toDoubleOrNull()

fun parseNum(s: String): Double = parseNumOrNull(s) ?: 0.0

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

fun formatDate(epochDay: Long?): String =
    epochDay?.let { LocalDate.ofEpochDay(it).format(dateFmt) } ?: ""

fun isoDate(epochDay: Long?): String =
    epochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: ""

fun today(): Long = LocalDate.now().toEpochDay()
