package com.remvisual.invoiced.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.remvisual.invoiced.data.BusinessSettings
import com.remvisual.invoiced.data.DiscountType
import com.remvisual.invoiced.data.Invoice
import com.remvisual.invoiced.data.InvoiceStatus
import com.remvisual.invoiced.data.LineItem
import com.remvisual.invoiced.domain.computeTotals
import com.remvisual.invoiced.domain.fmtMoney
import com.remvisual.invoiced.domain.fmtQty
import com.remvisual.invoiced.domain.fmtRate
import com.remvisual.invoiced.domain.formatDate
import com.remvisual.invoiced.domain.money
import com.remvisual.invoiced.domain.recipientsDisplay
import java.io.File

/** One set of knobs per template; the layout code is shared. */
private data class Style(
    val band: Boolean = false,          // full-width colour band behind the header
    val bandHeight: Float = 96f,
    val bandColorDark: Boolean = false, // band in near-black instead of the accent
    val serif: Boolean = false,
    val zebra: Boolean = true,
    val headerFill: String = "grey",    // grey | accent | dark | none
    val titleAccent: Boolean = true,
    val titleSize: Float = 26f,
    val centeredTitle: Boolean = false,
    val boxedTotals: Boolean = false,
    val scale: Float = 1f
)

private fun styleFor(name: String): Style = when (name) {
    "MODERN" -> Style(band = true, headerFill = "accent")
    "MINIMAL" -> Style(zebra = false, headerFill = "none", titleAccent = false, titleSize = 22f)
    "BOLD" -> Style(band = true, bandHeight = 120f, bandColorDark = true, zebra = false, headerFill = "dark", titleSize = 34f)
    "ELEGANT" -> Style(serif = true, zebra = false, headerFill = "none", titleAccent = false, titleSize = 24f, centeredTitle = true, boxedTotals = true)
    "COMPACT" -> Style(scale = 0.86f, titleSize = 20f)
    else -> Style()
}

object PdfGenerator {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    fun generate(context: Context, biz: BusinessSettings, inv: Invoice, items: List<LineItem>, logo: Bitmap? = null): File {
        val st = styleFor(biz.pdfTemplate)
        val totals = computeTotals(inv, items)
        val cur = inv.currency
        val doc = PdfDocument()
        val ar = (biz.accentColor shr 16) and 0xFF
        val ag = (biz.accentColor shr 8) and 0xFF
        val ab = biz.accentColor and 0xFF
        val accent = Color.rgb(ar, ag, ab)
        val dark = Color.rgb(33, 33, 33)
        val zebra = Color.argb(if (st.headerFill == "accent") 22 else 14, ar, ag, ab)
        val labelColor = if (st.titleAccent) accent else Color.DKGRAY
        val sc = st.scale
        val face = if (st.serif) Typeface.SERIF else Typeface.DEFAULT
        val faceBold = if (st.serif) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.DEFAULT_BOLD

        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT): TextPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * sc
                this.color = color
                textAlign = align
                typeface = if (bold) faceBold else face
            }
        fun fill(color: Int) = Paint().apply { this.color = color; style = Paint.Style.FILL }
        fun stroke(color: Int, width: Float) = Paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }

        val body = paint(10.5f)
        val bodyR = paint(10.5f, align = Paint.Align.RIGHT)
        val boldR = paint(10.5f, bold = true, align = Paint.Align.RIGHT)
        val label = paint(8.5f, bold = true, color = labelColor)
        val muted = paint(9.5f, color = Color.DKGRAY)
        val mutedR = paint(9.5f, color = Color.DKGRAY, align = Paint.Align.RIGHT)
        val totalR = paint(14f, bold = true, color = if (st.titleAccent) accent else Color.BLACK, align = Paint.Align.RIGHT)
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f }
        val accentRule = Paint().apply { color = if (st.titleAccent) accent else Color.DKGRAY; strokeWidth = 1.2f }

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var y = M
        val right = W - M

        fun c(): Canvas = page.canvas

        fun ensure(space: Float) {
            if (y + space > H - M) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                y = M
            }
        }

        fun layoutOf(text: String, width: Int, p: TextPaint): StaticLayout {
            val t = text.ifBlank { " " }
            return StaticLayout.Builder.obtain(t, 0, t.length, p, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        }

        fun multiline(text: String, x: Float, top: Float, width: Int, p: TextPaint): Float {
            if (text.isBlank()) return top
            val layout = layoutOf(text, width, p)
            val cv = c()
            cv.save()
            cv.translate(x, top)
            layout.draw(cv)
            cv.restore()
            return top + layout.height
        }

        fun drawLogo(top: Float, maxH: Float): Float {
            if (logo == null || logo.width <= 0 || logo.height <= 0) return 0f
            val s = minOf(150f / logo.width, maxH / logo.height, 1f)
            val lw = logo.width * s
            val lh = logo.height * s
            c().drawBitmap(logo, null, RectF(M, top, M + lw, top + lh), Paint(Paint.FILTER_BITMAP_FLAG))
            return lw
        }

        // ---- Header ----
        val bizName = biz.businessName.ifBlank { "Invoice" }
        val bizLines = listOf(
            biz.address, biz.phone, biz.email,
            if (biz.taxId.isNotBlank()) "Tax ID: ${biz.taxId}" else ""
        ).filter { it.isNotBlank() }.joinToString("\n")
        val numberText = inv.number + if (inv.revision > 1) "  (rev ${inv.revision})" else ""

        if (st.band) {
            val bandColor = if (st.bandColorDark) dark else accent
            c().drawRect(0f, 0f, W.toFloat(), st.bandHeight, fill(bandColor))
            val mid = st.bandHeight / 2f
            val lw = drawLogo(mid - 26f, 52f)
            val nameX = if (lw > 0f) M + lw + 14f else M
            c().drawText(bizName, nameX, mid + 6f, paint(18f, bold = true, color = Color.WHITE))
            c().drawText("INVOICE", right, mid + st.titleSize * sc / 2.6f, paint(st.titleSize, bold = true, color = Color.WHITE, align = Paint.Align.RIGHT))
            y = st.bandHeight + 22f
        } else if (st.centeredTitle) {
            val lw = drawLogo(y, 56f)
            val nameX = if (lw > 0f) M + lw + 14f else M
            c().drawText(bizName, nameX, y + 18f, paint(17f, bold = true))
            y += if (lw > 0f) 68f else 36f
            c().drawLine(M, y, right, y, rule)
            y += st.titleSize * sc + 6f
            c().drawText("INVOICE", W / 2f, y - 6f, paint(st.titleSize, color = Color.DKGRAY, align = Paint.Align.CENTER).apply { letterSpacing = 0.25f })
            c().drawLine(M, y + 4f, right, y + 4f, rule)
            y += 24f
        } else {
            val lw = drawLogo(y, 60f)
            val nameX = if (lw > 0f) M + lw + 14f else M
            c().drawText(bizName, nameX, y + 18f, paint(17f, bold = true))
            val titleP = if (st.titleAccent) paint(st.titleSize, bold = true, color = accent, align = Paint.Align.RIGHT)
            else paint(st.titleSize, color = Color.DKGRAY, align = Paint.Align.RIGHT)
            c().drawText("INVOICE", right, y + 22f, titleP)
            y += if (lw > 0f) 72f else 36f
        }

        val yLeft = multiline(bizLines, M, y, 280, muted)
        var yRight = y + 11f
        for ((k, v) in listOf("Invoice #" to numberText, "Date" to formatDate(inv.issueDate), "Due" to formatDate(inv.dueDate))) {
            if (v.isNotBlank()) {
                c().drawText(k, right - 130f, yRight, muted)
                c().drawText(v, right, yRight, boldR)
                yRight += 15f * sc
            }
        }
        y = maxOf(yLeft, yRight) + 26f * sc

        // ---- Bill to ----
        c().drawText("BILL TO", M, y, label)
        y += 7f
        val client = listOf(inv.clientName, recipientsDisplay(inv.clientEmail), inv.clientDetails)
            .filter { it.isNotBlank() }.joinToString("\n")
        y = multiline(client.ifBlank { "-" }, M, y, 320, body) + 26f * sc

        // ---- Items table ----
        val amountR = right - 8f
        val priceR = amountR - 95f
        val qtyR = priceR - 70f
        val descX = M + 8f
        val descW = (qtyR - 50f - descX).toInt()
        val headerTextColor = when (st.headerFill) { "accent", "dark" -> Color.WHITE; "none" -> Color.DKGRAY; else -> Color.BLACK }
        val headerText = paint(9.5f, bold = true, color = headerTextColor)
        val headerTextR = TextPaint(headerText).apply { textAlign = Paint.Align.RIGHT }

        fun tableHeader() {
            val h = 24f * sc
            when (st.headerFill) {
                "accent" -> c().drawRect(M, y, right, y + h, fill(accent))
                "dark" -> c().drawRect(M, y, right, y + h, fill(dark))
                "none" -> c().drawLine(M, y + h, right, y + h, accentRule)
                else -> c().drawRect(M, y, right, y + h, fill(Color.rgb(236, 236, 236)))
            }
            val b = y + 16f * sc
            c().drawText("DESCRIPTION", descX, b, headerText)
            c().drawText("QTY", qtyR, b, headerTextR)
            c().drawText("UNIT PRICE", priceR, b, headerTextR)
            c().drawText("AMOUNT", amountR, b, headerTextR)
            y += h + 4f
        }
        tableHeader()

        items.forEachIndexed { idx, item ->
            val before = pageNo
            ensure(44f)
            if (pageNo != before) tableHeader()
            val textTop = y + 6f * sc
            val descLayout = layoutOf(item.description, descW, body)
            val rowH = maxOf(descLayout.height.toFloat(), 14f) + 12f * sc
            if (st.zebra && idx % 2 == 1) c().drawRect(M, y, right, y + rowH, fill(zebra))
            val cv = c()
            cv.save(); cv.translate(descX, textTop); descLayout.draw(cv); cv.restore()
            val base = textTop + 11f * sc
            cv.drawText(fmtQty(item.quantity), qtyR, base, bodyR)
            cv.drawText(fmtMoney(item.unitPrice), priceR, base, bodyR)
            cv.drawText(fmtMoney(item.quantity * item.unitPrice), amountR, base, boldR)
            y += rowH
            if (!st.zebra) c().drawLine(M, y, right, y, rule)
        }
        if (st.zebra) c().drawLine(M, y, right, y, rule)

        // ---- Totals ----
        ensure(170f)
        y += 22f * sc
        val totalsTop = y
        val rows = ArrayList<Triple<String, String, Int>>() // label, value, kind: 0 normal, 1 total, 2 bold
        rows.add(Triple("Subtotal", money(totals.subtotal, cur), 0))
        if (totals.discount > 0) {
            val dl = if (inv.discountType == DiscountType.PERCENT) "Discount (${fmtRate(inv.discountValue)}%)" else "Discount"
            rows.add(Triple(dl, "-" + money(totals.discount, cur), 0))
        }
        if (inv.taxEnabled) rows.add(Triple("Tax (${fmtRate(inv.taxRate)}%)", money(totals.tax, cur), 0))
        rows.add(Triple("TOTAL", money(totals.total, cur), 1))
        if (inv.showWithholdingOnPdf && totals.withholding > 0) {
            rows.add(Triple("Less withholding tax (${fmtRate(inv.withholdingRate)}%)", "-" + money(totals.withholding, cur), 0))
            rows.add(Triple("Net payable", money(totals.expectedNet, cur), 2))
        }
        val boxLeft = qtyR - 20f
        if (st.boxedTotals) {
            var boxH = 14f
            for (r in rows) boxH += (if (r.third == 1) 30f else 17f) * sc
            c().drawRect(boxLeft, y - 14f, right, y - 14f + boxH, stroke(Color.LTGRAY, 0.8f))
        }
        val inset = if (st.boxedTotals) 8f else 0f
        for ((k, v, kind) in rows) {
            when (kind) {
                1 -> {
                    c().drawLine(boxLeft + inset, y - 9f, right - inset, y - 9f, accentRule)
                    y += 8f
                    c().drawText(k, priceR, y, paint(9.5f, bold = true, color = labelColor, align = Paint.Align.RIGHT))
                    c().drawText(v, amountR, y, totalR)
                    y += 22f * sc
                }
                2 -> {
                    c().drawText(k, priceR, y, boldR)
                    c().drawText(v, amountR, y, boldR)
                    y += 17f * sc
                }
                else -> {
                    c().drawText(k, priceR, y, mutedR)
                    c().drawText(v, amountR, y, bodyR)
                    y += 17f * sc
                }
            }
        }

        if (inv.status == InvoiceStatus.PAID) {
            val stamp = paint(42f, bold = true, color = Color.argb(120, 46, 125, 50))
            val cv = c()
            cv.save()
            cv.rotate(-12f, M + 40f, totalsTop + 30f)
            cv.drawText("PAID", M + 20f, totalsTop + 44f, stamp)
            cv.restore()
            if (inv.paidDate != null) c().drawText("Paid on ${formatDate(inv.paidDate)}", M + 24f, totalsTop + 62f, muted)
        }

        fun section(title: String, text: String) {
            if (text.isBlank()) return
            ensure(70f)
            y += 20f * sc
            c().drawText(title, M, y, label)
            y += 7f
            y = multiline(text, M, y, (W - 2 * M).toInt(), body)
        }
        section("PAYMENT", biz.paymentInstructions)
        section("NOTES", inv.notes)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val safe = inv.number.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "invoice" }
        val suffix = if (inv.revision > 1) "-rev${inv.revision}" else ""
        val file = File(dir, "Invoice-$safe$suffix.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
