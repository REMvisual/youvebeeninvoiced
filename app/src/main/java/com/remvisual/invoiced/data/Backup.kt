package com.remvisual.invoiced.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Whole-app backup as one JSON document: settings, logo, clients, saved items and
 * invoices with their line items. Import merges: settings and logo are replaced,
 * everything else is added unless the same client name, item description or
 * invoice number already exists.
 */
object Backup {
    const val FORMAT = 1

    data class ImportResult(val clients: Int, val items: Int, val invoices: Int, val skipped: Int)

    fun suggestedFileName(): String = "invoiced-backup-${LocalDate.now()}.json"

    suspend fun export(repo: Repository): String {
        val s = repo.settings.state.value
        val root = JSONObject()
        root.put("app", "invoiced")
        root.put("format", FORMAT)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", JSONObject().apply {
            put("businessName", s.businessName); put("address", s.address); put("phone", s.phone)
            put("email", s.email); put("taxId", s.taxId); put("defaultCurrency", s.defaultCurrency)
            put("defaultTaxRate", s.defaultTaxRate); put("defaultWithholdingRate", s.defaultWithholdingRate)
            put("invoicePrefix", s.invoicePrefix); put("nextNumber", s.nextNumber); put("defaultNotes", s.defaultNotes)
            put("paymentInstructions", s.paymentInstructions); put("pdfTemplate", s.pdfTemplate)
            put("accentColor", s.accentColor); put("emailTemplate", s.emailTemplate)
        })
        val logo = repo.settings.logoFile
        root.put("logoPng", if (logo.exists()) Base64.encodeToString(logo.readBytes(), Base64.NO_WRAP) else JSONObject.NULL)
        root.put("clients", JSONArray().apply {
            repo.allClients().forEach { c ->
                put(JSONObject().apply { put("name", c.name); put("email", c.email); put("details", c.details); put("createdAt", c.createdAt) })
            }
        })
        root.put("savedItems", JSONArray().apply {
            repo.allSavedItems().forEach { i ->
                put(JSONObject().apply { put("description", i.description); put("unitPrice", i.unitPrice); put("createdAt", i.createdAt) })
            }
        })
        root.put("invoices", JSONArray().apply {
            repo.getAllOnce().forEach { iw ->
                val i = iw.invoice
                put(JSONObject().apply {
                    put("number", i.number); put("issueDate", i.issueDate); put("dueDate", i.dueDate ?: JSONObject.NULL)
                    put("clientName", i.clientName); put("clientEmail", i.clientEmail); put("clientDetails", i.clientDetails)
                    put("currency", i.currency); put("discountType", i.discountType.name); put("discountValue", i.discountValue)
                    put("taxEnabled", i.taxEnabled); put("taxRate", i.taxRate); put("withholdingRate", i.withholdingRate)
                    put("showWithholdingOnPdf", i.showWithholdingOnPdf); put("notes", i.notes); put("status", i.status.name)
                    put("paidDate", i.paidDate ?: JSONObject.NULL); put("amountReceived", i.amountReceived ?: JSONObject.NULL)
                    put("createdAt", i.createdAt); put("revision", i.revision); put("sentRevision", i.sentRevision)
                    put("sentHash", i.sentHash); put("sentAt", i.sentAt ?: JSONObject.NULL)
                    put("items", JSONArray().apply {
                        iw.sortedItems.forEach { li ->
                            put(JSONObject().apply { put("description", li.description); put("quantity", li.quantity); put("unitPrice", li.unitPrice) })
                        }
                    })
                })
            }
        })
        return root.toString(2)
    }

    suspend fun import(repo: Repository, json: String): ImportResult {
        val root = JSONObject(json)
        require(root.optString("app") == "invoiced" || root.has("invoices")) { "Not an Invoiced backup" }
        var skipped = 0

        root.optJSONObject("settings")?.let { s ->
            val cur = repo.settings.state.value
            repo.settings.save(
                cur.copy(
                    businessName = s.optString("businessName", cur.businessName),
                    address = s.optString("address", cur.address),
                    phone = s.optString("phone", cur.phone),
                    email = s.optString("email", cur.email),
                    taxId = s.optString("taxId", cur.taxId),
                    defaultCurrency = s.optString("defaultCurrency", cur.defaultCurrency),
                    defaultTaxRate = s.optDouble("defaultTaxRate", cur.defaultTaxRate),
                    defaultWithholdingRate = s.optDouble("defaultWithholdingRate", cur.defaultWithholdingRate),
                    invoicePrefix = s.optString("invoicePrefix", cur.invoicePrefix),
                    nextNumber = maxOf(cur.nextNumber, s.optInt("nextNumber", cur.nextNumber)),
                    defaultNotes = s.optString("defaultNotes", cur.defaultNotes),
                    paymentInstructions = s.optString("paymentInstructions", cur.paymentInstructions),
                    pdfTemplate = s.optString("pdfTemplate", cur.pdfTemplate),
                    accentColor = s.optInt("accentColor", cur.accentColor),
                    emailTemplate = s.optString("emailTemplate", cur.emailTemplate)
                )
            )
        }
        if (!root.isNull("logoPng")) {
            runCatching { repo.settings.setLogoBytes(Base64.decode(root.getString("logoPng"), Base64.NO_WRAP)) }
        }

        var clients = 0
        root.optJSONArray("clients")?.let { arr ->
            for (k in 0 until arr.length()) {
                val c = arr.getJSONObject(k)
                val name = c.optString("name").trim()
                if (name.isBlank() || repo.clientExists(name)) { skipped++; continue }
                repo.saveClient(Client(name = name, email = c.optString("email"), details = c.optString("details"), createdAt = c.optLong("createdAt", System.currentTimeMillis())))
                clients++
            }
        }

        var items = 0
        root.optJSONArray("savedItems")?.let { arr ->
            for (k in 0 until arr.length()) {
                val i = arr.getJSONObject(k)
                val d = i.optString("description").trim()
                if (d.isBlank() || repo.savedItemExists(d)) { skipped++; continue }
                repo.saveItem(SavedItem(description = d, unitPrice = i.optDouble("unitPrice", 0.0), createdAt = i.optLong("createdAt", System.currentTimeMillis())))
                items++
            }
        }

        var invoices = 0
        root.optJSONArray("invoices")?.let { arr ->
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                val number = o.optString("number").trim()
                if (number.isBlank() || repo.invoiceNumberExists(number)) { skipped++; continue }
                val inv = Invoice(
                    number = number,
                    issueDate = o.optLong("issueDate", LocalDate.now().toEpochDay()),
                    dueDate = if (o.isNull("dueDate")) null else o.optLong("dueDate"),
                    clientName = o.optString("clientName"),
                    clientEmail = o.optString("clientEmail"),
                    clientDetails = o.optString("clientDetails"),
                    currency = o.optString("currency", "USD"),
                    discountType = enumOr(o.optString("discountType"), DiscountType.NONE),
                    discountValue = o.optDouble("discountValue", 0.0),
                    taxEnabled = o.optBoolean("taxEnabled", false),
                    taxRate = o.optDouble("taxRate", 0.0),
                    withholdingRate = o.optDouble("withholdingRate", 0.0),
                    showWithholdingOnPdf = o.optBoolean("showWithholdingOnPdf", false),
                    notes = o.optString("notes"),
                    status = enumOr(o.optString("status"), InvoiceStatus.DRAFT),
                    paidDate = if (o.isNull("paidDate")) null else o.optLong("paidDate"),
                    amountReceived = if (o.isNull("amountReceived")) null else o.optDouble("amountReceived"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    revision = o.optInt("revision", 1),
                    sentRevision = o.optInt("sentRevision", 0),
                    sentHash = o.optString("sentHash"),
                    sentAt = if (o.isNull("sentAt")) null else o.optLong("sentAt")
                )
                val lines = ArrayList<LineItem>()
                o.optJSONArray("items")?.let { la ->
                    for (j in 0 until la.length()) {
                        val li = la.getJSONObject(j)
                        lines.add(LineItem(description = li.optString("description"), quantity = li.optDouble("quantity", 1.0), unitPrice = li.optDouble("unitPrice", 0.0)))
                    }
                }
                repo.save(inv, lines)
                repo.settings.noteNumberUsed(number)
                invoices++
            }
        }
        return ImportResult(clients, items, invoices, skipped)
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
