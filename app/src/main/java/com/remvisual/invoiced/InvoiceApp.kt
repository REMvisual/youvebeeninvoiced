package com.remvisual.invoiced

import android.app.Application
import androidx.room.Room
import java.io.File
import com.remvisual.invoiced.data.AppDatabase
import com.remvisual.invoiced.data.Repository
import com.remvisual.invoiced.data.SettingsStore

class InvoiceApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "invoices.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        repo = Repository(db, SettingsStore(this))
        cleanShareCache()
    }

    /** PDFs and CSVs handed to other apps live in cache/share; drop anything older than a day. */
    private fun cleanShareCache() {
        val dir = File(cacheDir, "share")
        if (!dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        dir.listFiles()?.forEach { f -> if (f.isFile && f.lastModified() < cutoff) f.delete() }
    }
}
