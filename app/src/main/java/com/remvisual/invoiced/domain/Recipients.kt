package com.remvisual.invoiced.domain

/** One email recipient. Stored as "Name <email>" lines in a single text column. */
data class Recipient(val name: String = "", val email: String = "")

private val angle = Regex("""^\s*(.*?)\s*<\s*([^<>]+?)\s*>\s*$""")

fun parseRecipients(raw: String): List<Recipient> =
    raw.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
        val m = angle.find(entry)
        if (m != null) Recipient(m.groupValues[1].trim(), m.groupValues[2].trim())
        else if (entry.contains('@')) Recipient("", entry)
        else Recipient(entry, "")
    }

fun encodeRecipients(list: List<Recipient>): String =
    list.filter { it.name.isNotBlank() || it.email.isNotBlank() }
        .joinToString("\n") { r ->
            val n = r.name.trim()
            val e = r.email.trim()
            when {
                n.isBlank() -> e
                e.isBlank() -> n
                else -> "$n <$e>"
            }
        }

fun recipientEmails(raw: String): List<String> = parseRecipients(raw).map { it.email.trim() }.filter { it.contains('@') }

fun recipientsDisplay(raw: String): String =
    parseRecipients(raw).joinToString(", ") { r ->
        if (r.name.isBlank()) r.email else if (r.email.isBlank()) r.name else "${r.name} (${r.email})"
    }

/** "Hi Dana," when there is exactly one recipient with a name, otherwise "Hello!". */
fun greeting(raw: String): String {
    val list = parseRecipients(raw)
    if (list.size == 1) {
        val n = list[0].name.trim().substringBefore(' ')
        if (n.isNotBlank()) return "Hi $n,"
    }
    return "Hello!"
}
