package com.remvisual.invoiced.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class InvoiceStatus { DRAFT, SENT, UPDATED, PAID }

enum class DiscountType { NONE, PERCENT, FIXED }

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val issueDate: Long,
    val dueDate: Long?,
    val clientName: String = "",
    val clientDetails: String = "",
    val currency: String = "USD",
    val discountType: DiscountType = DiscountType.NONE,
    val discountValue: Double = 0.0,
    val taxEnabled: Boolean = false,
    val taxRate: Double = 0.0,
    val withholdingRate: Double = 0.0,
    val showWithholdingOnPdf: Boolean = false,
    val notes: String = "",
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val paidDate: Long? = null,
    val amountReceived: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // added in db version 2
    val clientEmail: String = "",
    val revision: Int = 1,
    val sentRevision: Int = 0,
    val sentHash: String = "",
    val sentAt: Long? = null
)

@Entity(
    tableName = "line_items",
    foreignKeys = [ForeignKey(
        entity = Invoice::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("invoiceId")]
)
data class LineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long = 0,
    val position: Int = 0,
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0
)

data class InvoiceWithItems(
    @Embedded val invoice: Invoice,
    @Relation(parentColumn = "id", entityColumn = "invoiceId")
    val items: List<LineItem>
)

val InvoiceWithItems.sortedItems: List<LineItem> get() = items.sortedBy { it.position }

@Entity(tableName = "clients", indices = [Index("name")])
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String = "",
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Catalogue of things you often bill for. Filled in on its own page, never auto-saved from invoices. */
@Entity(tableName = "saved_items", indices = [Index("description")])
data class SavedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val unitPrice: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
