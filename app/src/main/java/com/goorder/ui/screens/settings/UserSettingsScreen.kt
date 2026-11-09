package com.goorder.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.goorder.BuildConfig
import com.goorder.domain.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsScreen(
    user: User?,
    viewModel: UserSettingsViewModel
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf<EditField?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                viewModel.uploadAvatar(bytes, "profile.jpg")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Picture Section
        Box(contentAlignment = Alignment.BottomEnd) {
            val avatarUrl = user?.avatar?.let { 
                if (it.startsWith("http")) it 
                else BuildConfig.BASE_URL.replace("/graphql", "") + "/" + it 
            }
            
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Default.Person, 
                        contentDescription = "Profile Picture",
                        modifier = Modifier.padding(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            FilledIconButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.size(32.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Change Photo", modifier = Modifier.size(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Profile Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Update your personal information", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingsItem(
                    label = "Full Name",
                    value = user?.fname ?: "Not set",
                    onClick = { showEditDialog = EditField.Fname(user?.fname ?: "") }
                )
                
                SettingsItem(
                    label = "Nick Name",
                    value = user?.name ?: "Not set",
                    isEditable = false
                )
                
                SettingsItem(
                    label = "Location",
                    value = user?.address ?: "Not set",
                    onClick = { showEditDialog = EditField.Address(user?.address ?: "") }
                )
                
                SettingsItem(
                    label = "Language",
                    value = when(user?.language) {
                        0 -> "English"
                        1 -> "Malay"
                        2 -> "Tamil"
                        3 -> "Mandarin"
                        else -> "English"
                    },
                    onClick = { showEditDialog = EditField.Language(user?.language ?: 0) }
                )
                
                SettingsItem(
                    label = "Role",
                    value = user?.role?.option?.replaceFirstChar { it.uppercase() } ?: "User",
                    isEditable = false
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Bio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        user?.bio ?: "Tell us about yourself...",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { showEditDialog = EditField.Bio(user?.bio ?: "") }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Bio", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        
        if (viewModel.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        
        viewModel.error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    // Edit Dialogs
    showEditDialog?.let { field ->
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { Text("Update ${field.name}") },
            text = {
                when (field) {
                    is EditField.Language -> {
                        Column {
                            val options = listOf("English", "Malay", "Tamil", "Mandarin")
                            options.forEachIndexed { index, label ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(selected = field.value == index, onClick = { 
                                        showEditDialog = field.copy(value = index) 
                                    })
                                    Text(label)
                                }
                            }
                        }
                    }
                    else -> {
                        var text by remember { mutableStateOf(field.stringValue) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { 
                                text = it
                                showEditDialog = field.updateValue(it)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when (field) {
                        is EditField.Fname -> viewModel.updateFname(field.value)
                        is EditField.Address -> viewModel.updateAddress(field.value)
                        is EditField.Bio -> viewModel.updateBio(field.value)
                        is EditField.Language -> viewModel.updateLanguage(field.value)
                    }
                    showEditDialog = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsItem(
    label: String,
    value: String,
    isEditable: Boolean = true,
    onClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (isEditable) {
                IconButton(onClick = onClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit $label", modifier = Modifier.size(20.dp))
                }
            }
        }
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
    }
}

sealed class EditField(val name: String, open val stringValue: String = "") {
    data class Fname(val value: String) : EditField("Full Name", value)
    data class Address(val value: String) : EditField("Location", value)
    data class Bio(val value: String) : EditField("Bio", value)
    data class Language(val value: Int) : EditField("Language", value.toString())
    
    fun updateValue(newVal: String): EditField = when(this) {
        is Fname -> copy(value = newVal)
        is Address -> copy(value = newVal)
        is Bio -> copy(value = newVal)
        is Language -> copy(value = newVal.toIntOrNull() ?: 0)
    }
}
