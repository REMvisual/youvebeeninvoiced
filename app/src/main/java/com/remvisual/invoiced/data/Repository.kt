package com.remvisual.invoiced.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class Repository(private val db: AppDatabase, val settings: SettingsStore) {
    private val dao = db.invoiceDao()
    private val clientDao = db.clientDao()
    private val savedItemDao = db.savedItemDao()

    val invoices: Flow<List<InvoiceWithItems>> = dao.observeAll()
    val clients: Flow<List<Client>> = clientDao.observeAll()
    val savedItems: Flow<List<SavedItem>> = savedItemDao.observeAll()

    suspend fun get(id: Long): InvoiceWithItems? = dao.get(id)

    suspend fun getAllOnce(): List<InvoiceWithItems> = dao.getAllOnce()

    /** Saves the invoice and its items, and keeps the client list in sync with what was typed. */
    suspend fun save(invoice: Invoice, items: List<LineItem>): Long = db.withTransaction {
        val id = if (invoice.id == 0L) {
            dao.insertInvoice(invoice)
        } else {
            dao.updateInvoice(invoice)
            invoice.id
        }
        dao.deleteItemsFor(id)
        dao.insertItems(items.mapIndexed { i, item -> item.copy(id = 0, invoiceId = id, position = i) })
        rememberClient(invoice.clientName, invoice.clientEmail, invoice.clientDetails)
        id
    }

    private suspend fun rememberClient(name: String, email: String, details: String) {
        val n = name.trim()
        if (n.isBlank()) return
        val existing = clientDao.findByName(n)
        if (existing == null) {
            clientDao.insert(Client(name = n, email = email.trim(), details = details.trim()))
        } else {
            val updated = existing.copy(
                email = email.trim().ifBlank { existing.email },
                details = details.trim().ifBlank { existing.details }
            )
            if (updated != existing) clientDao.update(updated)
        }
    }

    suspend fun update(invoice: Invoice) = dao.updateInvoice(invoice)

    suspend fun delete(id: Long) = dao.deleteInvoice(id)

    suspend fun saveClient(client: Client) {
        if (client.id == 0L) clientDao.insert(client) else clientDao.update(client)
    }

    suspend fun deleteClient(id: Long) = clientDao.delete(id)

    suspend fun saveItem(item: SavedItem) {
        if (item.id == 0L) savedItemDao.insert(item) else savedItemDao.update(item)
    }

    suspend fun deleteSavedItem(id: Long) = savedItemDao.delete(id)

    suspend fun invoiceNumberExists(number: String): Boolean = dao.countByNumber(number) > 0
    suspend fun clientExists(name: String): Boolean = clientDao.findByName(name) != null
    suspend fun savedItemExists(description: String): Boolean = savedItemDao.countByDescription(description) > 0
    suspend fun allClients(): List<Client> = clientDao.allOnce()
    suspend fun allSavedItems(): List<SavedItem> = savedItemDao.allOnce()
}
