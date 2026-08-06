package com.techilyfly.tfplans.ui.home

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import com.techilyfly.tfplans.ui.components.AdManager
import com.techilyfly.tfplans.ui.components.NativeAdCard
import com.google.android.gms.ads.nativead.NativeAd
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techilyfly.tfplans.data.Note
import com.techilyfly.tfplans.ui.components.AdBanner
import com.techilyfly.tfplans.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun isNetworkAvailable(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddNote: () -> Unit,
    onNavigateToEditNote: (String) -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onLogout: () -> Unit
) {
    val pinnedNotes by viewModel.pinnedNotes.collectAsState()
    val otherNotes by viewModel.otherNotes.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val defaultView by viewModel.defaultView.collectAsState()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAccountDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (isSearchExpanded) {
            isSearchExpanded = false
            viewModel.setSearchQuery("")
        } else if (currentTab != "Notes") {
            viewModel.setTab("Notes")
        } else {
            showExitDialog = true
        }
    }

    val handleNoteAction = { action: () -> Unit ->
        action()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setSearchQuery("")
        }
    }

    val userEmail = viewModel.getUserEmail()
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val photoUrl = auth.currentUser?.photoUrl
    val userInitials = if (userEmail.isNotBlank()) userEmail.take(2).uppercase() else "U"

    val backgroundGradient = ComposeBrush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
    )

    val networkObserver = remember { com.techilyfly.tfplans.util.NetworkConnectivityObserver(context) }
    val isOnline by networkObserver.isOnline.collectAsState(initial = com.techilyfly.tfplans.ui.auth.isOnline(context))
    var showNoInternetDialog by remember { mutableStateOf(false) }

    if (currentTab == "Settings") {
        val app = LocalContext.current.applicationContext as com.techilyfly.tfplans.TFPlansApplication
        val settingsViewModel: com.techilyfly.tfplans.ui.settings.SettingsViewModel = viewModel(factory = com.techilyfly.tfplans.ui.settings.SettingsViewModel.provideFactory(app))
        com.techilyfly.tfplans.ui.settings.SettingsScreen(
            viewModel = settingsViewModel,
            onNavigateBack = { viewModel.setTab("Notes") },
            onNavigateToTab = { tab -> viewModel.setTab(tab) },
            onLogout = {
                if (isOnline) {
                    viewModel.logout(context) {
                        onLogout()
                    }
                } else {
                    showNoInternetDialog = true
                }
            },
            onNavigateToAuth = {
                onNavigateToAuth()
            },
            onNavigateToProfile = {
                onNavigateToProfile()
            }
        )
        return
    }

    if (showNoInternetDialog) {
        com.techilyfly.tfplans.ui.components.InternetRequiredDialog(
            message = "An active internet connection is required to manage your account.",
            onDismiss = { showNoInternetDialog = false }
        )
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Account Profile", color = PrimaryColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Signed in as:", style = MaterialTheme.typography.bodySmall, color = SecondaryColor)
                    Text(userEmail, style = MaterialTheme.typography.titleMedium, color = PrimaryColor, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountDialog = false
                        if (isOnline) {
                            viewModel.logout(context) {
                                onLogout()
                            }
                        } else {
                            showNoInternetDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Close", color = SecondaryColor)
                }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit TF Plans?", color = PrimaryColor, fontWeight = FontWeight.Bold) },
            text = { Text("Your notes are safely saved. Are you sure you want to exit?", color = SecondaryColor) },
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)) {
                    Text(
                        "TF Plans",
                        style = MaterialTheme.typography.titleLarge,
                        color = PrimaryColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryColor
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp), color = PrimaryColor.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Description, contentDescription = "Notes") },
                    label = { Text("Notes") },
                    selected = currentTab == "Notes",
                    onClick = { 
                        viewModel.setTab("Notes")
                        scope.launch { drawerState.close() } 
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = PrimaryColor.copy(alpha = 0.15f),
                        selectedIconColor = PrimaryColor,
                        selectedTextColor = PrimaryColor,
                        unselectedIconColor = SecondaryColor,
                        unselectedTextColor = SecondaryColor
                    )
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = "Reminders") },
                    label = { Text("Reminders") },
                    selected = currentTab == "Reminders",
                    onClick = { 
                        viewModel.setTab("Reminders")
                        scope.launch { drawerState.close() } 
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = PrimaryColor.copy(alpha = 0.15f),
                        selectedIconColor = PrimaryColor,
                        selectedTextColor = PrimaryColor,
                        unselectedIconColor = SecondaryColor,
                        unselectedTextColor = SecondaryColor
                    )
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Archive, contentDescription = "Archive") },
                    label = { Text("Archive") },
                    selected = currentTab == "Archive",
                    onClick = { 
                        viewModel.setTab("Archive")
                        scope.launch { drawerState.close() } 
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = PrimaryColor.copy(alpha = 0.15f),
                        selectedIconColor = PrimaryColor,
                        selectedTextColor = PrimaryColor,
                        unselectedIconColor = SecondaryColor,
                        unselectedTextColor = SecondaryColor
                    )
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentTab == "Settings",
                    onClick = { 
                        viewModel.setTab("Settings")
                        scope.launch { drawerState.close() } 
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = PrimaryColor.copy(alpha = 0.15f),
                        selectedIconColor = PrimaryColor,
                        selectedTextColor = PrimaryColor,
                        unselectedIconColor = SecondaryColor,
                        unselectedTextColor = SecondaryColor
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp), color = PrimaryColor.copy(alpha = 0.2f))
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = ErrorColor) },
                    label = { Text("Sign Out", color = ErrorColor) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (isOnline) {
                            viewModel.logout(context) {
                                onLogout()
                            }
                        } else {
                            showNoInternetDialog = true
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.background(backgroundGradient),
            topBar = {
                // Glass-styled Header matching HTML template
                Surface(
                    color = SurfaceColor.copy(alpha = 0.95f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 800.dp)
                                .fillMaxWidth()
                                .height(52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (!isSearchExpanded) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Menu,
                                            contentDescription = "Menu",
                                            tint = PrimaryColor
                                        )
                                    }

                                    Text(
                                        text = currentTab,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        ),
                                        color = PrimaryColor
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val newView = if (defaultView == "grid") "list" else "grid"
                                            viewModel.setDefaultView(newView)
                                        },
                                        modifier = Modifier.size(40.dp).testTag("quick_view_toggle_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (defaultView == "grid") Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                            contentDescription = "Switch View Mode",
                                            tint = PrimaryColor
                                        )
                                    }

                                    IconButton(
                                        onClick = { isSearchExpanded = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = "Search",
                                            tint = PrimaryColor
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                ComposeBrush.linearGradient(
                                                    listOf(PrimaryColor, TertiaryColor)
                                                )
                                            )
                                            .clickable { onNavigateToProfile() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (photoUrl != null) {
                                            coil.compose.AsyncImage(
                                                model = photoUrl,
                                                contentDescription = "Profile Photo",
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                userInitials,
                                                color = BackgroundColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Search Input Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryColor.copy(alpha = 0.08f))
                                        .border(1.dp, PrimaryColor.copy(alpha = 0.2f), CircleShape)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = PrimaryColor,
                                        modifier = Modifier.padding(start = 4.dp).size(20.dp)
                                    )

                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(
                                            color = PrimaryColor,
                                            fontSize = 15.sp
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                            .testTag("search_input"),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search notes, tags...",
                                                    color = SecondaryColor.copy(alpha = 0.6f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )

                                    IconButton(
                                        onClick = {
                                            viewModel.setSearchQuery("")
                                            isSearchExpanded = false
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Close Search",
                                            tint = SecondaryColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { handleNoteAction { onNavigateToAddNote() } },
                    modifier = Modifier.testTag("add_note_fab"),
                    containerColor = Color.Transparent,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                ComposeBrush.linearGradient(
                                    listOf(PrimaryColor, TertiaryColor)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add Note",
                            tint = BackgroundColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            bottomBar = {
                com.techilyfly.tfplans.ui.components.AppBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.setTab(it) }
                )
            }
        ) { padding ->
            if (pinnedNotes.isEmpty() && otherNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryColor.copy(alpha = 0.1f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (currentTab == "Reminders") Icons.Filled.NotificationsActive else Icons.Filled.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = PrimaryColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No notes found" else if (currentTab == "Archive") "No archived notes" else if (currentTab == "Reminders") "No reminders set" else "No notes yet",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try searching for something else" else if (currentTab == "Reminders") "Set a reminder on any note to view it here" else "Tap '+' below to create your first note",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = if (defaultView == "list") StaggeredGridCells.Fixed(1) else StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = PrimaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Pinned",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryColor
                                )
                            }
                        }
                        items(pinnedNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                fontSize = fontSize,
                                onClick = { handleNoteAction { onNavigateToEditNote(note.id) } }
                            )
                        }
                        

                        if (otherNotes.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Text(
                                    text = "Others",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryColor,
                                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
                                )
                            }
                        }
                        
                        val firstBatch = otherNotes.take(4)
                        val remainingBatch = otherNotes.drop(4)

                        items(firstBatch, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                fontSize = fontSize,
                                onClick = { handleNoteAction { onNavigateToEditNote(note.id) } }
                            )
                        }
                        
                        if (firstBatch.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                val context = LocalContext.current
                                var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
                                LaunchedEffect(Unit) {
                                    nativeAd = AdManager.getNativeAdAndLoadNext(context)
                                }
                                nativeAd?.let {
                                    NativeAdCard(nativeAd = it, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }

                        items(remainingBatch, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                fontSize = fontSize,
                                onClick = { handleNoteAction { onNavigateToEditNote(note.id) } }
                            )
                        }
                    } else {
                        val firstBatch = otherNotes.take(4)
                        val remainingBatch = otherNotes.drop(4)

                        items(firstBatch, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                fontSize = fontSize,
                                onClick = { handleNoteAction { onNavigateToEditNote(note.id) } }
                            )
                        }

                        if (firstBatch.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                val context = LocalContext.current
                                var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
                                LaunchedEffect(Unit) {
                                    nativeAd = AdManager.getNativeAdAndLoadNext(context)
                                }
                                nativeAd?.let {
                                    NativeAdCard(nativeAd = it, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }

                        items(remainingBatch, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                fontSize = fontSize,
                                onClick = { handleNoteAction { onNavigateToEditNote(note.id) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NoteCard(note: Note, fontSize: String = "16", onClick: () -> Unit) {
    val dateString = remember(note.updatedAt) {
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        sdf.format(Date(note.updatedAt))
    }

    // Determine card container background & styling based on color or content
    val surfaceColor = MaterialTheme.colorScheme.surface
    val bgCol = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bgCol.red + 0.587f * bgCol.green + 0.114f * bgCol.blue) < 0.5f
    val cardBg = remember(note.color, note.isPinned, surfaceColor, isDark) {
        when {
            note.color != 0 && note.color != -1 && note.color != 0xFFFFFFFF.toInt() && note.color != 0xFFFFFBFE.toInt() -> {
                val r = android.graphics.Color.red(note.color) / 255f
                val g = android.graphics.Color.green(note.color) / 255f
                val b = android.graphics.Color.blue(note.color) / 255f
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (isDark && lum > 0.8f) {
                    surfaceColor
                } else {
                    Color(note.color)
                }
            }
            else -> surfaceColor
        }
    }

    val parsedBlocks = remember(note.content) {
        try {
            com.techilyfly.tfplans.ui.edit.parseBlocks(note.content)
        } catch (e: Exception) {
            emptyList<com.techilyfly.tfplans.ui.edit.NoteBlock>()
        }
    }

    val imageUris = remember(parsedBlocks) {
        parsedBlocks.filterIsInstance<com.techilyfly.tfplans.ui.edit.ImageBlock>().map { it.uri }
    }
    val hasImageAttachment = imageUris.isNotEmpty()
    
    val hasAudioAttachment = remember(parsedBlocks) {
        parsedBlocks.any { it is com.techilyfly.tfplans.ui.edit.AudioBlock }
    }
    
    val isQuote = remember(parsedBlocks, note.title) {
        val firstText = (parsedBlocks.firstOrNull { it is com.techilyfly.tfplans.ui.edit.TextBlock } as? com.techilyfly.tfplans.ui.edit.TextBlock)?.textFieldValue?.text ?: ""
        firstText.startsWith("\"") || note.title.lowercase().contains("quote")
    }
    
    val checklistBlocks = remember(parsedBlocks) {
        parsedBlocks.filterIsInstance<com.techilyfly.tfplans.ui.edit.ChecklistBlock>()
    }
    
    val previewAnnotatedString = remember(parsedBlocks) {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        for (block in parsedBlocks.take(4)) {
            when (block) {
                is com.techilyfly.tfplans.ui.edit.TextBlock -> {
                    if (block.textFieldValue.text.isNotBlank()) {
                        if (builder.length > 0) builder.append(" ")
                        builder.append(block.textFieldValue.annotatedString)
                    }
                }
                is com.techilyfly.tfplans.ui.edit.ChecklistBlock -> {
                    if (builder.length > 0) builder.append(" ")
                    builder.append(if (block.isChecked) "[x] " else "[ ] ")
                    builder.append(block.textFieldValue.annotatedString)
                }
                else -> {}
            }
        }
        builder.toAnnotatedString()
    }

    val fallbackTitle = remember(parsedBlocks) {
        val firstBlock = parsedBlocks.firstOrNull { 
            (it is com.techilyfly.tfplans.ui.edit.TextBlock && it.textFieldValue.text.isNotBlank()) ||
            (it is com.techilyfly.tfplans.ui.edit.ChecklistBlock && it.textFieldValue.text.isNotBlank())
        }
        when (firstBlock) {
            is com.techilyfly.tfplans.ui.edit.TextBlock -> firstBlock.textFieldValue.text
            is com.techilyfly.tfplans.ui.edit.ChecklistBlock -> firstBlock.textFieldValue.text
            else -> ""
        }
    }

    // Extract tags/keywords
    val extractedTags = remember(note.title, note.content, note.category) {
        val tags = mutableListOf<String>()
        if (note.category.isNotBlank()) {
            tags.add(note.category.removePrefix("#").trim())
        }
        val words = "${note.title} ${note.content}".split(" ", "\n")
        words.filter { it.startsWith("#") && it.length > 1 }.forEach { tags.add(it.removePrefix("#")) }
        if (tags.isEmpty()) {
            if (note.title.contains("Sync", ignoreCase = true) || note.title.contains("Work", ignoreCase = true)) tags.add("Work")
            if (note.title.contains("Prep", ignoreCase = true) || note.title.contains("High", ignoreCase = true)) tags.add("High Priority")
            if (note.title.contains("Desk", ignoreCase = true) || note.title.contains("Idea", ignoreCase = true)) tags.add("Inspo")
            if (note.title.contains("Quote", ignoreCase = true) || isQuote) tags.add("Quotes")
        }
        tags.distinct().take(2)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("note_card_${note.id}"),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (note.isPinned) 1.5.dp else 1.dp,
            color = if (note.isPinned) PrimaryColor.copy(alpha = 0.6f) else PrimaryColor.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Optional Image Attachment Preview Header
            if (imageUris.isNotEmpty()) {
                when (imageUris.size) {
                    1 -> {
                        coil.compose.AsyncImage(
                            model = imageUris[0],
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(110.dp)
                        )
                    }
                    2 -> {
                        Row(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                            coil.compose.AsyncImage(
                                model = imageUris[0],
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            coil.compose.AsyncImage(
                                model = imageUris[1],
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                    3 -> {
                        Row(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                            coil.compose.AsyncImage(
                                model = imageUris[0],
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                coil.compose.AsyncImage(
                                    model = imageUris[1],
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                coil.compose.AsyncImage(
                                    model = imageUris[2],
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                    else -> { // 4 or more
                        Row(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                coil.compose.AsyncImage(
                                    model = imageUris[0],
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                coil.compose.AsyncImage(
                                    model = imageUris[1],
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                coil.compose.AsyncImage(
                                    model = imageUris[2],
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    coil.compose.AsyncImage(
                                        model = imageUris[3],
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (imageUris.size > 4) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "+${imageUris.size - 4}",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (hasImageAttachment) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            ComposeBrush.linearGradient(
                                listOf(PrimaryColor.copy(alpha = 0.35f), TertiaryColor.copy(alpha = 0.25f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = "Image Attachment",
                            tint = PrimaryColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Photo Attachment",
                            fontSize = 11.sp,
                            color = PrimaryColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth()
            ) {
                // Top Row: Title & Pinned Pin Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    val titleSp = FontSizeManager.getCardTitleSp(fontSize)
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = titleSp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            color = PrimaryColor
                        )
                    } else if (fallbackTitle.isNotBlank()) {
                        Text(
                            text = fallbackTitle.trim(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = titleSp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            color = PrimaryColor
                        )
                    }

                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = PrimaryColor,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                    }
                }

                if (note.title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val previewSp = FontSizeManager.getPreviewSp(fontSize)

                // Checklist View OR Standard Text View
                if (checklistBlocks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        checklistBlocks.take(3).forEach { block ->
                            val isChecked = block.isChecked
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isChecked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (isChecked) PrimaryColor else SecondaryColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = block.textFieldValue.annotatedString,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = previewSp,
                                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (isChecked) SecondaryColor.copy(alpha = 0.5f) else SecondaryColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (previewAnnotatedString.text.isNotBlank()) {
                    Text(
                        text = previewAnnotatedString,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = previewSp,
                            fontStyle = if (isQuote) FontStyle.Italic else FontStyle.Normal
                        ),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = SecondaryColor,
                        lineHeight = (previewSp.value + 4f).sp
                    )
                }

                // Audio Note Badge
                if (hasAudioAttachment) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryColor.copy(alpha = 0.1f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(14.dp))
                            Text("Audio Recording", fontSize = 11.sp, color = PrimaryColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Footer: Tags & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tag Chips & Reminder Badge
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val reminderTime = note.reminderTime
                        if (reminderTime != null && reminderTime > 0) {
                            val reminderFormatted = remember(reminderTime) {
                                val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                sdf.format(Date(reminderTime))
                            }
                            Surface(
                                shape = CircleShape,
                                color = PrimaryColor.copy(alpha = 0.18f),
                                border = BorderStroke(0.5.dp, PrimaryColor.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsActive,
                                        contentDescription = "Reminder",
                                        tint = PrimaryColor,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = reminderFormatted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryColor
                                    )
                                }
                            }
                        }

                        extractedTags.forEach { tag ->
                            Surface(
                                shape = CircleShape,
                                color = PrimaryColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Date Label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = SecondaryColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = dateString,
                            fontSize = 11.sp,
                            color = SecondaryColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
