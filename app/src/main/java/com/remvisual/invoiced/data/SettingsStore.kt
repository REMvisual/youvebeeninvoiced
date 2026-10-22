package com.remvisual.invoiced.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class BusinessSettings(
    val businessName: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val taxId: String = "",
    val defaultCurrency: String = "USD",
    val defaultTaxRate: Double = 0.0,
    val defaultWithholdingRate: Double = 0.0,
    val invoicePrefix: String = "INV-",
    val nextNumber: Int = 1,
    val defaultNotes: String = "",
    val paymentInstructions: String = "",
    /** PDF look: CLASSIC, MODERN or MINIMAL. */
    val pdfTemplate: String = "CLASSIC",
    /** Email body with placeholders; blank means the built-in default. */
    val emailTemplate: String = "",
    /** Accent colour for the PDF as 0xRRGGBB. */
    val accentColor: Int = 0x1E5BA8,
    /** Not persisted; bumped whenever the logo file changes so the UI refreshes. */
    val logoVersion: Int = 0
)

class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<BusinessSettings> get() = _state

    val logoFile: File get() = File(context.filesDir, "logo.png")
    val hasLogo: Boolean get() = logoFile.exists()

    private fun load(): BusinessSettings = BusinessSettings(
        businessName = prefs.getString("businessName", "") ?: "",
        address = prefs.getString("address", "") ?: "",
        phone = prefs.getString("phone", "") ?: "",
        email = prefs.getString("email", "") ?: "",
        taxId = prefs.getString("taxId", "") ?: "",
        defaultCurrency = prefs.getString("defaultCurrency", "USD") ?: "USD",
        defaultTaxRate = prefs.getFloat("defaultTaxRate", 0f).toDouble(),
        defaultWithholdingRate = prefs.getFloat("defaultWithholdingRate", 0f).toDouble(),
        invoicePrefix = prefs.getString("invoicePrefix", "INV-") ?: "INV-",
        nextNumber = prefs.getInt("nextNumber", 1),
        defaultNotes = prefs.getString("defaultNotes", "") ?: "",
        paymentInstructions = prefs.getString("paymentInstructions", "") ?: "",
        pdfTemplate = prefs.getString("pdfTemplate", "CLASSIC") ?: "CLASSIC",
        emailTemplate = prefs.getString("emailTemplate", "") ?: "",
        accentColor = prefs.getInt("accentColor", 0x1E5BA8)
    )

    fun save(s: BusinessSettings) {
        prefs.edit()
            .putString("businessName", s.businessName)
            .putString("address", s.address)
            .putString("phone", s.phone)
            .putString("email", s.email)
            .putString("taxId", s.taxId)
            .putString("defaultCurrency", s.defaultCurrency)
            .putFloat("defaultTaxRate", s.defaultTaxRate.toFloat())
            .putFloat("defaultWithholdingRate", s.defaultWithholdingRate.toFloat())
            .putString("invoicePrefix", s.invoicePrefix)
            .putInt("nextNumber", s.nextNumber)
            .putString("defaultNotes", s.defaultNotes)
            .putString("paymentInstructions", s.paymentInstructions)
            .putString("pdfTemplate", s.pdfTemplate)
            .putString("emailTemplate", s.emailTemplate)
            .putInt("accentColor", s.accentColor)
            .apply()
        _state.value = s.copy(logoVersion = _state.value.logoVersion)
    }

    fun previewNextNumber(): String {
        val s = _state.value
        return s.invoicePrefix + s.nextNumber.toString().padStart(3, '0')
    }

    /** After a new invoice is saved, bump the counter past whatever number was used. */
    fun noteNumberUsed(number: String) {
        val s = _state.value
        if (!number.startsWith(s.invoicePrefix)) return
        val digits = number.removePrefix(s.invoicePrefix).takeLastWhile { it.isDigit() }
        val n = digits.toIntOrNull() ?: return
        if (n >= s.nextNumber) save(s.copy(nextNumber = n + 1))
    }

    /** Copies a picked image into app storage, downscaled to at most 800px, as PNG. */
    fun setLogo(uri: Uri): Boolean {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val scale = minOf(1f, 800f / maxOf(raw.width, raw.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
        logoFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _state.value = _state.value.copy(logoVersion = _state.value.logoVersion + 1)
        return true
    }

    fun setLogoBytes(png: ByteArray) {
        logoFile.writeBytes(png)
        _state.value = _state.value.copy(logoVersion = _state.value.logoVersion + 1)
    }

    fun clearLogo() {
        if (logoFile.exists()) logoFile.delete()
        _state.value = _state.value.copy(logoVersion = _state.value.logoVersion + 1)
    }

    fun loadLogo(): Bitmap? = if (hasLogo) BitmapFactory.decodeFile(logoFile.path) else null
}
