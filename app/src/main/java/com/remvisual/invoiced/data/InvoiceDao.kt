package com.remvisual.invoiced.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Transaction
    @Query("SELECT * FROM invoices ORDER BY issueDate DESC, id DESC")
    fun observeAll(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun get(id: Long): InvoiceWithItems?

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY issueDate ASC, id ASC")
    suspend fun getAllOnce(): List<InvoiceWithItems>

    @Insert
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Insert
    suspend fun insertItems(items: List<LineItem>)

    @Query("DELETE FROM line_items WHERE invoiceId = :invoiceId")
    suspend fun deleteItemsFor(invoiceId: Long)

    @Query("SELECT COUNT(*) FROM invoices WHERE number = :number")
    suspend fun countByNumber(number: String): Int

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoice(id: Long)
}
