package com.remvisual.invoiced.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

fun fileUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

fun shareFiles(context: Context, files: List<File>, mime: String, subject: String, body: String? = null) {
    val uris = files.map { fileUri(context, it) }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
    } else {
        val list = ArrayList<Uri>(uris)
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, list) }
    }
    intent.type = mime
    intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    if (body != null) intent.putExtra(Intent.EXTRA_TEXT, body)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(intent, subject).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(chooser)
}

/**
 * Opens Gmail directly when it is installed (it fills To/Subject/Body from a plain ACTION_SEND),
 * otherwise any email app via a mailto selector, otherwise the share sheet.
 */
fun emailPdf(context: Context, file: File, to: List<String>, subject: String, body: String) {
    val uri = fileUri(context, file)
    fun base(): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        if (to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val gmail = base().setPackage("com.google.android.gm")
    if (gmail.resolveActivity(context.packageManager) != null) {
        try {
            context.startActivity(gmail)
            return
        } catch (e: ActivityNotFoundException) {
            // fall through
        }
    }
    val generic = base().apply {
        selector = Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:" + Uri.encode(to.joinToString(","))))
    }
    try {
        context.startActivity(generic)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No email app found, pick another app", Toast.LENGTH_SHORT).show()
        shareFiles(context, listOf(file), "application/pdf", subject, body)
    }
}

/** Opens the PDF in whatever viewer the phone has. */
fun openPdf(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(fileUri(context, file), "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
    }
}
