@file:Suppress("DEPRECATION")
package com.techilyfly.tfplans.ui.auth

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.techilyfly.tfplans.ui.theme.*
import com.techilyfly.tfplans.ui.components.AdBanner
import com.techilyfly.tfplans.ui.components.AdManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Patterns

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedVisibility

fun isOnline(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onNavigateToHome()
        }
    }

    val scrollState = rememberScrollState()
    
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
    )

    val networkObserver = remember { com.techilyfly.tfplans.util.NetworkConnectivityObserver(context) }
    val isOnline by networkObserver.isOnline.collectAsState(initial = isOnline(context))

    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { viewModel.handleGoogleIdToken(it) }
            } catch (e: ApiException) {
                val msg = e.message ?: ""
                if (e.statusCode == com.google.android.gms.common.api.CommonStatusCodes.CANCELED || msg.contains("12501")) {
                     // user canceled, ignore
                } else {
                     viewModel.setAuthError("Google Sign-In failed.")
                }
            }
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }

    // Email/Password States
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    
    val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val isPasswordValid = password.length >= 6
    val isFormValid = isEmailValid && isPasswordValid

    BackHandler(enabled = true) {
        showExitDialog = true
    }

    if (!isOnline) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss without internet */ },
            title = { Text("Internet Required", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = PrimaryColor) },
            text = { Text("An internet connection is required for the initial login. Please enable internet connectivity and sign in.", color = SecondaryColor, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = { 
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Text("Open Settings", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit TF Plans?", color = PrimaryColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("Are you sure you want to exit?", color = SecondaryColor) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel", color = SecondaryColor)
                }
            }
        )
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset Password", color = PrimaryColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your email address to receive a password reset link.", color = SecondaryColor, modifier = Modifier.padding(bottom = 16.dp))
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (Patterns.EMAIL_ADDRESS.matcher(forgotEmail).matches()) {
                            viewModel.resetPassword(forgotEmail) { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                if (success) showForgotDialog = false
                            }
                        } else {
                            Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Text("Send Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Cancel", color = SecondaryColor)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(backgroundGradient),
        bottomBar = { 
            com.techilyfly.tfplans.ui.components.AdBanner(
                modifier = Modifier.navigationBarsPadding()
            ) 
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.techilyfly.tfplans.R.drawable.tf_plans_logo),
                    contentDescription = "TF Plans Logo",
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(80.dp)
                        .clip(MaterialTheme.shapes.large)
                )
                Text(
                    text = if (isSignUpMode) "Create an Account" else "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = PrimaryColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Organize your thoughts, plan your future.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryColor,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (authState is AuthState.Error) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = ErrorColor.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (authState as AuthState.Error).message,
                                color = ErrorColor,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Email Input
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        viewModel.clearError() 
                    },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = email.isNotEmpty() && !isEmailValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryColor,
                        focusedLabelColor = PrimaryColor
                    )
                )

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        viewModel.clearError() 
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = password.isNotEmpty() && !isPasswordValid,
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = SecondaryColor)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryColor,
                        focusedLabelColor = PrimaryColor
                    )
                )
                
                if (password.isNotEmpty() && !isPasswordValid) {
                    Text(
                        text = "Password must be at least 6 characters",
                        color = ErrorColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, bottom = 8.dp)
                    )
                }

                AnimatedVisibility(visible = !isSignUpMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showForgotDialog = true }) {
                            Text("Forgot Password?", color = PrimaryColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(color = PrimaryColor)
                } else {
                    Button(
                        onClick = {
                            if (isOnline && isFormValid) {
                                AdManager.incrementActivity(context)
                                if (isSignUpMode) {
                                    viewModel.signUpWithEmail(email, password)
                                } else {
                                    viewModel.signInWithEmail(email, password)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOnline && isFormValid) PrimaryColor else Color.Gray,
                            contentColor = if (isOnline && isFormValid) MaterialTheme.colorScheme.onPrimary else Color.White
                        ),
                        enabled = isOnline && isFormValid
                    ) {
                        Text(if (isSignUpMode) "Sign Up" else "Sign In", style = MaterialTheme.typography.titleMedium)
                    }
                    
                    val isPlayServicesAvailable = remember {
                        com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
                    }

                    if (isPlayServicesAvailable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = SecondaryColor.copy(alpha = 0.3f))
                            Text("OR", color = SecondaryColor, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = SecondaryColor.copy(alpha = 0.3f))
                        }
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = PrimaryColor.copy(alpha = 0.1f),
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(24.dp).padding(end = 8.dp))
                                Text(
                                    text = "Want to sync images and audio across devices? Please continue with Google Sign-in to automatically back them up to your personal Google Drive.",
                                    color = SecondaryColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        OutlinedButton(
                            onClick = {
                                if (isOnline) {
                                    AdManager.incrementActivity(context)
                                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                                    val webClientId = if (resId != 0) context.getString(resId) else "174888666914-8jpbbn0oud27f1gifvn7gger77djsq7j.apps.googleusercontent.com"
                                    
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestIdToken(webClientId)
                                        .requestEmail()
                                        .requestScopes(Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE))
                                        .build()
                                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("google_login_button"),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isOnline) SecondaryColor else Color.Gray
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isOnline) SecondaryColor.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f)),
                            enabled = isOnline
                        ) {
                            Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isSignUpMode) "Already have an account?" else "Don't have an account?", 
                            color = SecondaryColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = {
                                isSignUpMode = !isSignUpMode
                                viewModel.clearError()
                            }
                        ) {
                            Text(
                                text = if (isSignUpMode) "Sign In" else "Sign Up",
                                color = PrimaryColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
