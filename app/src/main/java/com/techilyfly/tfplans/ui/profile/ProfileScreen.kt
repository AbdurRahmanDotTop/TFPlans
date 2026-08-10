@file:Suppress("DEPRECATION", "DiscouragedApi")

package com.techilyfly.tfplans.ui.profile

import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.techilyfly.tfplans.ui.settings.InAppBrowserScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToTab: (String) -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val isEmailVerified by viewModel.isEmailVerified.collectAsState()
    val cloudBackup by viewModel.cloudBackup.collectAsState()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsState()
    val notesCount by viewModel.notesCount.collectAsState()

    val driveFolderRootId by viewModel.driveFolderRootId.collectAsState()
    val driveFolderImagesId by viewModel.driveFolderImagesId.collectAsState()
    val driveFolderRecordingsId by viewModel.driveFolderRecordingsId.collectAsState()

    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showRateAppDialog by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    
    // Dialog states for Profile Management
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showReAuthDialog by remember { mutableStateOf(false) }
    var showNoInternetDialog by remember { mutableStateOf(false) }
    var showForceLogoutDialog by remember { mutableStateOf(false) }
    var showForceDeleteDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isLoggingOut by remember { mutableStateOf(false) }
    
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    if (browserUrl != null) {
        InAppBrowserScreen(
            initialUrl = browserUrl!!,
            onDismiss = { browserUrl = null }
        )
    }

    var hasDrivePermission by remember { mutableStateOf(viewModel.checkDrivePermission(context)) }
    
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        hasDrivePermission = viewModel.checkDrivePermission(context)
        if (hasDrivePermission) {
            Toast.makeText(context, "Google Drive permission granted!", Toast.LENGTH_SHORT).show()
            viewModel.initializeDrive { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Google Drive permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val errorColor = MaterialTheme.colorScheme.error

    var isProcessingAccountDeletion by remember { mutableStateOf(false) }

    val networkObserver = remember { com.techilyfly.tfplans.util.NetworkConnectivityObserver(context) }
    val isOnline by networkObserver.isOnline.collectAsState(initial = com.techilyfly.tfplans.ui.auth.isOnline(context))

    if (showNoInternetDialog) {
        com.techilyfly.tfplans.ui.components.InternetRequiredDialog(
            message = "An active internet connection is required to manage your account.",
            onDismiss = { showNoInternetDialog = false }
        )
    }

    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]

    val creationDate = currentUser?.metadata?.creationTimestamp?.let {
        SimpleDateFormat("MMM dd, yyyy", locale).format(Date(it))
    } ?: "Unknown"

    val lastSyncString = if (lastSyncedTime > 0) {
        SimpleDateFormat("MMM dd, HH:mm", locale).format(Date(lastSyncedTime))
    } else {
        "Never"
    }
    
    val isEmailProvider = currentUser?.providerData?.any { it.providerId == "password" } == true
    val isGoogleProvider = currentUser?.providerData?.any { it.providerId == "google.com" } == true

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isOnline) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && isOnline) {
                viewModel.reloadUser()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profile", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = primaryColor,
                        navigationIconContentColor = primaryColor
                    )
                )
            },
        bottomBar = {
            com.techilyfly.tfplans.ui.components.AppBottomBar(
                currentTab = "Settings",
                onTabSelected = onNavigateToTab
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        AsyncImage(
                            model = currentUser?.photoUrl,
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        if (isEmailProvider) {
                            IconButton(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(primaryColor, CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "User",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = currentUser?.email ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondaryColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Active Account", color = primaryColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        
                        if (isEmailProvider && !isEmailVerified) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = errorColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable {
                                    if (isOnline) {
                                        viewModel.sendEmailVerification { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        showNoInternetDialog = true
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = errorColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verify Email", color = errorColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "ACCOUNT INFO",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = secondaryColor,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surfaceColor,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProfileStatRow(icon = Icons.Filled.DateRange, label = "Member Since", value = creationDate)
                        ProfileStatRow(icon = Icons.Filled.CloudSync, label = "Last Cloud Sync", value = lastSyncString)
                        ProfileStatRow(icon = Icons.Filled.Backup, label = "Cloud Backup", value = if (cloudBackup) "Enabled" else "Disabled")
                        ProfileStatRow(icon = Icons.Filled.Storage, label = "Total Notes", value = "$notesCount")
                    }
                }
            }

            item {
                Text(
                    text = "GOOGLE DRIVE BACKUP",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = secondaryColor,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surfaceColor,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (isGoogleProvider) {
                            if (hasDrivePermission) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = primaryColor)
                                    Text("Connected to Google Drive", style = MaterialTheme.typography.bodyLarge, color = primaryColor, fontWeight = FontWeight.SemiBold)
                                }
                                ProfileStatRow(icon = Icons.Filled.Folder, label = "TF Plans Folder", value = if (driveFolderRootId != null) "Created" else "Missing")
                                ProfileStatRow(icon = Icons.Filled.Image, label = "Images Backup", value = if (driveFolderImagesId != null) "Active" else "Missing")
                                ProfileStatRow(icon = Icons.Filled.Mic, label = "Audio Backup", value = if (driveFolderRecordingsId != null) "Active" else "Missing")
                                
                                if (driveFolderRootId == null || driveFolderImagesId == null || driveFolderRecordingsId == null) {
                                    Button(
                                        onClick = {
                                            viewModel.initializeDrive { _, msg ->
                                                showErrorDialog = msg
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                                    ) {
                                        Text("Initialize Backup Folders")
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = errorColor)
                                    Text("Google Drive Not Connected", style = MaterialTheme.typography.bodyLarge, color = errorColor, fontWeight = FontWeight.SemiBold)
                                }
                                Text("Connect to automatically back up your images and audio recordings.", style = MaterialTheme.typography.bodyMedium, color = secondaryColor)
                                Button(
                                    onClick = {
                                        if (isOnline) {
                                            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                                            val webClientId = if (resId != 0) context.getString(resId) else "174888666914-8jpbbn0oud27f1gifvn7gger77djsq7j.apps.googleusercontent.com"
                                            @Suppress("DEPRECATION")
                                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                .requestIdToken(webClientId)
                                                .requestEmail()
                                                .requestScopes(Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE))
                                                .build()
                                            @Suppress("DEPRECATION")
                                            val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        } else {
                                            showNoInternetDialog = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                                ) {
                                    Text("Connect Google Drive")
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = secondaryColor)
                                Text("Sign in with Google to enable media backups", style = MaterialTheme.typography.bodyMedium, color = secondaryColor)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "ACTIONS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = secondaryColor,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surfaceColor,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (isGoogleProvider) {
                            ProfileSettingItem(icon = Icons.Filled.ManageAccounts, title = "Manage Google Account") {
                                if (isOnline) {
                                    val intent = Intent(Intent.ACTION_VIEW, "https://myaccount.google.com/".toUri())
                                    context.startActivity(intent)
                                } else {
                                    showNoInternetDialog = true
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }
                        
                        if (isEmailProvider) {
                            ProfileSettingItem(icon = Icons.Filled.Edit, title = "Edit Profile") {
                                showEditProfileDialog = true
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            ProfileSettingItem(icon = Icons.Filled.Lock, title = "Change Password") {
                                showChangePasswordDialog = true
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        }

                        ProfileSettingItem(icon = Icons.Filled.Sync, title = "Sync Now") {
                            if (isOnline) {
                                viewModel.syncNow {
                                    Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                showNoInternetDialog = true
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ProfileSettingItem(icon = Icons.Filled.Share, title = "Share the App") {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Check out TF Plans - an elegant Notes app: https://play.google.com/store/apps/details?id=${context.packageName}")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via"))
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ProfileSettingItem(icon = Icons.Filled.Star, title = "Rate the App") {
                            showRateAppDialog = true
                        }
                    }
                }
            }

            item {
                Text(
                    text = "LEGAL & SUPPORT",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = secondaryColor,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surfaceColor,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ProfileSettingItem(icon = Icons.AutoMirrored.Filled.Help, title = "Help & Support") {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = "mailto:".toUri()
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@techilyfly.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "TF Plans Support Request")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ProfileSettingItem(icon = Icons.Filled.SupportAgent, title = "Contact Us") {
                            browserUrl = "https://techilyfly.com/tfplans/#contact"
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ProfileSettingItem(icon = Icons.Filled.PrivacyTip, title = "Privacy Policy") {
                            browserUrl = "https://techilyfly.com/tfplans/#privacy"
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ProfileSettingItem(icon = Icons.Filled.Gavel, title = "Terms & Conditions") {
                            showTermsDialog = true
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        val versionName = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        } catch (e: Exception) {
                            "Unknown"
                        }
                        ProfileSettingItem(icon = Icons.Filled.Info, title = "App Version", subtitle = versionName) {}
                    }
                }
            }
            item {
                Text(
                    text = "DEVELOPER",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = secondaryColor,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, top = 24.dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surfaceColor,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ProfileSettingItem(icon = Icons.Filled.Person, title = "Techily Fly") {
                            browserUrl = "https://www.techilyfly.com/"
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ProfileSettingItem(icon = Icons.Filled.PersonOutline, title = "AbdurRahman Dot Top") {
                            browserUrl = "https://abdurrahman.top/"
                        }
                    }
                }
            }

            item {
                Text(
                    text = "DANGER ZONE",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = errorColor,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = errorColor.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, errorColor.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ProfileSettingItem(icon = Icons.AutoMirrored.Filled.Logout, title = if (isLoggingOut) "Signing Out..." else "Sign Out", tint = errorColor) {
                            if (!isLoggingOut) {
                                isLoggingOut = true
                                viewModel.logout(context, force = false) { success, msg, requiresForceConfirm ->
                                    isLoggingOut = false
                                    if (success) {
                                        onNavigateToAuth()
                                    } else if (requiresForceConfirm) {
                                        showForceLogoutDialog = true
                                    } else if (msg != null) {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = errorColor.copy(alpha = 0.1f))
                        ProfileSettingItem(icon = Icons.Filled.DeleteForever, title = "Delete Account", tint = errorColor) {
                            showDeleteAccountDialog = true
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
    
    if (showErrorDialog != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Information", fontWeight = FontWeight.Bold, color = primaryColor) },
            text = { Text(showErrorDialog!!, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    if (showEditProfileDialog) {
        var name by remember { mutableStateOf(currentUser?.displayName ?: "") }
        var photoUrl by remember { mutableStateOf(currentUser?.photoUrl?.toString() ?: "") }
        var isSaving by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { if (!isSaving) showEditProfileDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold, color = primaryColor) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = photoUrl,
                        onValueChange = { photoUrl = it },
                        label = { Text("Photo URL (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (isOnline) {
                        isSaving = true
                        viewModel.updateProfile(name, photoUrl) { success, msg ->
                            isSaving = false
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) showEditProfileDialog = false
                        }
                    } else {
                        showNoInternetDialog = true
                    }
                }, enabled = !isSaving) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isSaving) showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showChangePasswordDialog) {
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { if (!isSaving) showChangePasswordDialog = false },
            title = { Text("Change Password", fontWeight = FontWeight.Bold, color = primaryColor) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword != confirmPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.length >= 6 && newPassword == confirmPassword) {
                            if (isOnline) {
                                isSaving = true
                                viewModel.updatePassword(newPassword) { success, msg ->
                                    isSaving = false
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        showChangePasswordDialog = false
                                    } else if (msg == "REAUTH_REQUIRED") {
                                        showChangePasswordDialog = false
                                        pendingAction = { showChangePasswordDialog = true }
                                        showReAuthDialog = true
                                    } else {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                showNoInternetDialog = true
                            }
                        } else {
                            Toast.makeText(context, "Passwords must match and be at least 6 characters", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isSaving) showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReAuthDialog) {
        var password by remember { mutableStateOf("") }
        var isAuthenticating by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { 
                if (!isAuthenticating) {
                    showReAuthDialog = false
                    pendingAction = null
                }
            },
            title = { Text("Re-authenticate", fontWeight = FontWeight.Bold, color = primaryColor) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("For your security, please re-enter your password to continue this action.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.isNotEmpty()) {
                            if (isOnline) {
                                isAuthenticating = true
                                viewModel.reauthenticate(password) { success, msg ->
                                    isAuthenticating = false
                                    if (success) {
                                        showReAuthDialog = false
                                        pendingAction?.invoke()
                                        pendingAction = null
                                    } else {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                showNoInternetDialog = true
                            }
                        }
                    },
                    enabled = !isAuthenticating
                ) {
                    if (isAuthenticating) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (!isAuthenticating) {
                        showReAuthDialog = false
                        pendingAction = null
                    }
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showForceLogoutDialog) {
        var isForcingLogout by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isForcingLogout) showForceLogoutDialog = false },
            title = { Text("Warning: Unsynced Data", fontWeight = FontWeight.Bold, color = errorColor) },
            text = { Text("You have unsynced notes. If you sign out now, this offline data will be permanently lost. Do you want to proceed?", color = secondaryColor) },
            confirmButton = {
                Button(
                    onClick = {
                        isForcingLogout = true
                        viewModel.logout(context, force = true) { success, msg, _ ->
                            isForcingLogout = false
                            showForceLogoutDialog = false
                            if (success) {
                                onNavigateToAuth()
                            } else if (msg != null) {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }, 
                    colors = ButtonDefaults.buttonColors(containerColor = errorColor),
                    enabled = !isForcingLogout
                ) {
                    if (isForcingLogout) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Proceed Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isForcingLogout) showForceLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showForceDeleteDialog) {
        var isForcingDelete by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isForcingDelete) showForceDeleteDialog = false },
            title = { Text("Warning: Unsynced Data", fontWeight = FontWeight.Bold, color = errorColor) },
            text = { Text("You have unsynced notes. If you delete your account now, this offline data will be permanently lost along with everything else. Do you want to proceed?", color = secondaryColor) },
            confirmButton = {
                Button(
                    onClick = {
                        isForcingDelete = true
                        viewModel.deleteAccount(context, force = true) { success, msg, _ ->
                            isForcingDelete = false
                            showForceDeleteDialog = false
                            if (success) {
                                onNavigateToAuth()
                            } else if (msg == "REAUTH_REQUIRED") {
                                showReAuthDialog = true
                            } else {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }, 
                    colors = ButtonDefaults.buttonColors(containerColor = errorColor),
                    enabled = !isForcingDelete
                ) {
                    if (isForcingDelete) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Proceed Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isForcingDelete) showForceDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service", fontWeight = FontWeight.Bold, color = primaryColor) },
            text = { Text("By using TF Plans, you agree to our terms of service. Do not use the app for any illegal activities. We reserve the right to modify these terms at any time.") },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, color = primaryColor) },
            text = { Text("TF Plans respects user privacy. Note contents are stored locally on device storage via Room SQLite and synced to Firebase Firestore if logged in. No third-party data tracking is performed.") },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showRateAppDialog) {
        AlertDialog(
            onDismissRequest = { showRateAppDialog = false },
            title = { Text("Rate on App Store", fontWeight = FontWeight.Bold, color = primaryColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose an App Store to rate us:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            showRateAppDialog = false
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri()))
                            } catch (_: Exception) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Google Play Store")
                    }
                    Button(
                        onClick = {
                            showRateAppDialog = false
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, "amzn://apps/android?p=${context.packageName}".toUri()))
                            } catch (_: Exception) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.amazon.com/gp/mas/dl/android?p=${context.packageName}".toUri()))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.8f))
                    ) {
                        Text("Amazon Appstore")
                    }
                    Button(
                        onClick = {
                            showRateAppDialog = false
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, "indus://details?id=${context.packageName}".toUri())
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallback = Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
                                    context.startActivity(fallback)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "App Store not found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.6f))
                    ) {
                        Text("Indus AppStore")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRateAppDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessingAccountDeletion) showDeleteAccountDialog = false },
            title = { Text("Delete Account", fontWeight = FontWeight.Bold, color = errorColor) },
            text = { Text("Are you sure you want to delete your account? This action cannot be undone. All your notes and settings will be permanently removed.", color = secondaryColor) },
            confirmButton = {
                Button(onClick = {
                    isProcessingAccountDeletion = true
                    viewModel.deleteAccount(context, force = false) { success, msg, requiresForceConfirm ->
                        isProcessingAccountDeletion = false
                        if (success) {
                            showDeleteAccountDialog = false
                            onNavigateToAuth()
                        } else if (requiresForceConfirm) {
                            showDeleteAccountDialog = false
                            showForceDeleteDialog = true
                        } else if (msg == "REAUTH_REQUIRED") {
                            showDeleteAccountDialog = false
                            showReAuthDialog = true
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = errorColor), enabled = !isProcessingAccountDeletion) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isProcessingAccountDeletion) showDeleteAccountDialog = false }) {
                    Text("Cancel", color = secondaryColor)
                }
            }
        )
    }
    
    if (isLoggingOut || isProcessingAccountDeletion) {
        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = primaryColor,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (isLoggingOut) "Signing out safely..." else "Deleting your account...",
                    color = primaryColor,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please wait a moment.",
                    color = secondaryColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
}

@Composable
fun ProfileStatRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ProfileSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onSurface else tint)
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}
