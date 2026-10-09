package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.WineCellarViewModel
import com.example.data.AuthState

@Composable
fun SettingsScreen(viewModel: WineCellarViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val auth by viewModel.authState.collectAsState()
    val cellar by viewModel.cellar.collectAsState()
    val inviteCode by viewModel.inviteCode.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val legacyCount by viewModel.legacyCount.collectAsState()
    val importing by viewModel.importing.collectAsState()

    var showJoin by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }

    val email = (auth as? AuthState.SignedIn)?.email ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)

        // ---------------------------------------------------------------- account
        SettingsCard(title = "Account", icon = Icons.Rounded.Logout) {
            Text("Signed in as", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(email, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            OutlinedButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
        }

        // ---------------------------------------------------------------- sharing
        SettingsCard(title = "Shared cellar", icon = Icons.Rounded.Group) {
            val members = cellar?.memberEmails.orEmpty()
            Text(
                if (members.size <= 1) "Only you have access to this cellar." else "People with access:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (members.size > 1) {
                members.forEach { member ->
                    Text("• $member", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "To share, create an invite code and have the other person enter it after creating their own account. " +
                    "Codes work for 7 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            val code = inviteCode
            if (code != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        code,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy code")
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my wine cellar: install the Wine Cellar app, create an account, then choose " +
                                    "\"Join a cellar\" and enter code $code"
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Share invite code"))
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share code")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.createInvite() }, enabled = !busy) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (code == null) "Create invite code" else "New code")
                }
                OutlinedButton(onClick = { showJoin = true }, enabled = !busy) { Text("Join another") }
            }
        }

        // ---------------------------------------------------------------- sensor
        SettingsCard(title = "Temperature sensor", icon = Icons.Rounded.Sensors) {
            Text(
                "Your ESP32 sends readings straight to the cloud, so both phones see the same history. " +
                    "Copy these into the sketch in the repo's firmware folder:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            val c = cellar
            if (c != null) {
                CopyableValue("CELLAR_ID", c.id) { clipboard.setText(AnnotatedString(c.id)) }
                CopyableValue("SENSOR_KEY", c.sensorKey) { clipboard.setText(AnnotatedString(c.sensorKey)) }
                Text(
                    "Keep the sensor key private: anyone with it can add readings to this cellar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        // ---------------------------------------------------------------- data
        SettingsCard(title = "Data from the previous version", icon = Icons.Rounded.Download) {
            Text(
                if (legacyCount > 0) {
                    "This phone still has $legacyCount bottle${if (legacyCount == 1) "" else "s"} saved by the previous version of the app. " +
                        "They were copied in when you created your cellar. Run the import again any time; bottles already in the cellar are skipped."
                } else {
                    "No data from the previous version was found on this phone."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (legacyCount > 0) {
                Button(onClick = { viewModel.importLegacy() }, enabled = !importing) {
                    if (importing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Importing…")
                    } else {
                        Text("Import again")
                    }
                }
            }
        }

        Text(
            "Wine Cellar 2.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    if (showJoin) {
        AlertDialog(
            onDismissRequest = { showJoin = false },
            title = { Text("Join another cellar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This phone will switch to the other cellar. Your current cellar isn't deleted; " +
                            "you can get back to it with an invite code from it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase() },
                        label = { Text("Invite code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.joinCellar(joinCode)
                    showJoin = false
                    joinCode = ""
                }) { Text("Join") }
            },
            dismissButton = { TextButton(onClick = { showJoin = false }) { Text("Cancel") } }
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your cellar stays safe in the cloud. Sign back in with the same email to see it again.") },
            confirmButton = {
                Button(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            content()
        }
    }
}

@Composable
private fun CopyableValue(label: String, value: String, onCopy: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
            .padding(start = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $label")
        }
    }
}
