package com.canonreader.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** App destinations; preferences live in Settings. */
@Composable
fun OverflowMenu(
    tint: Color = Color.Black,
    onOpenSaved: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    fun closeAnd(action: () -> Unit) {
        expanded = false
        action()
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onOpenSaved?.let {
                DropdownMenuItem(
                    text = { Text("Saved") },
                    onClick = { closeAnd(it) },
                    leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                )
            }
            onOpenSettings?.let {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = { closeAnd(it) },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                )
            }
            onOpenAbout?.let {
                DropdownMenuItem(
                    text = { Text("About") },
                    onClick = { closeAnd(it) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                )
            }
        }
    }
}
