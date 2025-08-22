package com.fluortronix.fluortronixapp.presentation.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluortronix.fluortronixapp.R

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Profile Header with Background Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 29.dp, start = 0.dp, end = 0.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(16.dp))

            ) {
                // Background Image
                Image(
                    painter = painterResource(id = R.drawable.profilebg),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentScale = ContentScale.Crop
                )
                
                // Content Row - Centered in the Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(50.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Profile",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        
        item {
            // Contact Us Section
            ProfileSection(
                title = "Contact Us",
                icon = Icons.Default.Email
            ) {
                ContactItem(
                    icon = Icons.Default.Email,
                    text = "sales@fluortronix.com",
                    onClick = {
                        openEmail(context, "sales@fluortronix.com")
                    }
                )
                ContactItem(
                    icon = Icons.Default.Phone,
                    text = "+91-8368348606",
                    onClick = {
                        openPhone(context, "+918368348606")
                    }
                )
            }
        }
        
        item {
            // About Section
            ProfileSection(
                title = "About Fluortronix",
                icon = Icons.Default.Info
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openWebsite(context, "https://www.fluortronix.com")
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Website",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "www.fluortronix.com",
                        fontSize = 16.sp,
                        color = Color.Black,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
        
        item {
            // App Version Section
            ProfileSection(
                title = "App Information",
                icon = Icons.Default.Info
            ) {
                Text(
                    text = "App Version: ${getAppVersion(context)}",
                    fontSize = 16.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        
        item {
            // Delete Account Section
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Red
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .border(
                                width = 1.dp,
                                color = Color(0xFFBDBDBD),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete account data")
                    }
        }
    }
    
    // Delete Account Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Account",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )
            },
            text = {
                Text(
                    text = "This will permanently delete your account and clear all app data including cache, preferences, and stored information. This action cannot be undone.\n\nAre you sure you want to continue?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteAccountAndClearData(context)
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFFFFFEFE)
        )
    }
}

@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
//            .border(
//                width = 1.dp,
//                color = Color(0xFFBDBDBD),
//                shape = RoundedCornerShape(12.dp)
//            )
                ,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
            content()
        }
    }
}

@Composable
private fun ContactItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.Black,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            color = Color.Black
        )
    }
}

private fun openEmail(context: Context, email: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "Fluortronix App Inquiry")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle error - no email app available
    }
}

private fun openPhone(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phone")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle error - no phone app available
    }
}

private fun openWebsite(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle error - no browser available
    }
}

private fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}

private fun deleteAccountAndClearData(context: Context) {
    try {
        // Option 2: Immediate clearing of app data
        
        // Clear all shared preferences files
        val sharedPrefsDir = context.filesDir.parentFile?.resolve("shared_prefs")
        sharedPrefsDir?.listFiles()?.forEach { prefsFile ->
            try {
                prefsFile.delete()
            } catch (e: Exception) {
                android.util.Log.w("ProfileScreen", "Failed to delete prefs file: ${prefsFile.name}")
            }
        }
        
        // Clear DataStore preferences directory
        val dataStoreDir = context.filesDir.parentFile?.resolve("datastore")
        if (dataStoreDir?.exists() == true) {
            deleteRecursive(dataStoreDir)
        }
        
        // Clear app databases
        val databasesDir = context.filesDir.parentFile?.resolve("databases")
        if (databasesDir?.exists() == true) {
            deleteRecursive(databasesDir)
        }
        
        // Clear app cache
        val cacheDir = context.cacheDir
        if (cacheDir.exists()) {
            deleteRecursive(cacheDir)
        }
        
        // Clear external cache
        context.externalCacheDir?.let { externalCache ->
            if (externalCache.exists()) {
                deleteRecursive(externalCache)
            }
        }
        
        // Clear app files directory
        val filesDir = context.filesDir
        if (filesDir.exists()) {
            filesDir.listFiles()?.forEach { file ->
                try {
                    deleteRecursive(file)
                } catch (e: Exception) {
                    android.util.Log.w("ProfileScreen", "Failed to delete file: ${file.name}")
                }
            }
        }
        
        // Show completion message and restart app
        android.util.Log.i("ProfileScreen", "App data cleared successfully")
        
        // Restart the application
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        android.os.Process.killProcess(android.os.Process.myPid())
        
    } catch (e: Exception) {
        // Handle error - show toast or log
        android.util.Log.e("ProfileScreen", "Error clearing data: ${e.message}")
        
        // Fallback to system settings if immediate clearing fails
        try {
            val intent = Intent().apply {
                action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (fallbackError: Exception) {
            android.util.Log.e("ProfileScreen", "Fallback also failed: ${fallbackError.message}")
        }
    }
}

private fun deleteRecursive(fileOrDirectory: java.io.File) {
    try {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        fileOrDirectory.delete()
    } catch (e: Exception) {
        // Ignore deletion errors
    }
}

