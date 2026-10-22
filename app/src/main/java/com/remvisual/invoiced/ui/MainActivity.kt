package com.remvisual.invoiced.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.remvisual.invoiced.InvoiceApp
import com.remvisual.invoiced.data.Repository

sealed interface Screen {
    data object List : Screen
    data class Edit(val id: Long?) : Screen
    data object Settings : Screen
    data object Clients : Screen
    data object Items : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as InvoiceApp).repo
        setContent {
            AppTheme {
                AppRoot(repo)
            }
        }
    }
}

@Composable
fun AppRoot(repo: Repository) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    when (val s = screen) {
        Screen.List -> InvoiceListScreen(
            repo = repo,
            onOpen = { screen = Screen.Edit(it) },
            onNew = { screen = Screen.Edit(null) },
            onSettings = { screen = Screen.Settings },
            onClients = { screen = Screen.Clients },
            onItems = { screen = Screen.Items }
        )
        is Screen.Edit -> {
            BackHandler { screen = Screen.List }
            InvoiceEditScreen(repo = repo, invoiceId = s.id, onDone = { screen = Screen.List })
        }
        Screen.Clients -> {
            BackHandler { screen = Screen.List }
            ClientsScreen(repo = repo, onDone = { screen = Screen.List })
        }
        Screen.Items -> {
            BackHandler { screen = Screen.List }
            SavedItemsScreen(repo = repo, onDone = { screen = Screen.List })
        }
        Screen.Settings -> {
            BackHandler { screen = Screen.List }
            SettingsScreen(repo = repo, onDone = { screen = Screen.List })
        }
    }
}
