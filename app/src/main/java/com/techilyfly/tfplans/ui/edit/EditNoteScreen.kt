package com.techilyfly.tfplans.ui.edit

import android.content.Context
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techilyfly.tfplans.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import android.media.MediaRecorder
import android.media.MediaPlayer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditNoteScreen(
    viewModel: EditNoteViewModel,
    noteId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToAddNote: (() -> Unit)? = null,
    onNavigateToTab: (String) -> Unit
) {
    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    BackHandler {
        viewModel.saveNote()
        onNavigateBack()
    }

    val note by viewModel.note.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val noteReminderTime = note.reminderTime
    val hasReminder = noteReminderTime != null && noteReminderTime > 0
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val aiPreviewText by viewModel.aiPreviewText.collectAsState()
    val showColorPicker by viewModel.showColorPicker.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    
    var showAiBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    // Automatically stop alarm sound when existing note is opened
    LaunchedEffect(note.id) {
        if (noteId != null && note.id == noteId) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notificationManager?.cancel(note.id.hashCode())
            
            val reminderTime = note.reminderTime
            // Only clear the reminder if it has already expired and is not set to repeat
            if (reminderTime != null && reminderTime <= System.currentTimeMillis() && note.reminderRepeat == null) {
                viewModel.updateReminder(null, null)
                com.techilyfly.tfplans.reminders.ReminderScheduler.cancelReminder(context, note.id)
            }
        }
    }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Toggle between View Mode (Note Details) and Edit Mode (Note Editing)
    var isEditingMode by remember(noteId) { mutableStateOf(noteId == null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showAudioRecorderDialog by remember { mutableStateOf(false) }

    var contentState by remember(note.id) { mutableStateOf(note.content) }
    var isLocalChange by remember(note.id) { mutableStateOf(false) }
    val focusedBlockIndex = remember { mutableStateOf<Int?>(null) }
    var toggleChecklistTrigger by remember { mutableStateOf(0) }
    
    val mainScrollState = rememberScrollState()
    var browserUrl by remember { mutableStateOf<String?>(null) }
    
    val customUriHandler = remember {
        object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                browserUrl = uri
            }
        }
    }
    
    var formatTrigger by remember { mutableStateOf<com.techilyfly.tfplans.ui.edit.FormatTrigger?>(null) }
    var formatTriggerId by remember { mutableStateOf(0) }
    var showTextColorPicker by remember { mutableStateOf(false) }
    var showHighlightColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(note.content) {
        if (note.content == contentState) {
            isLocalChange = false
        } else {
            if (!isLocalChange) {
                contentState = note.content
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedUri = copyUriToInternalStorage(context, uri)
            if (savedUri != null) {
                val current = note.content
                val blocks = parseBlocks(current).toMutableList()
                val nextId = (blocks.maxOfOrNull { it.id } ?: 0) + 1
                blocks.add(ImageBlock(nextId, savedUri.toString()))
                val newContent = serializeBlocks(blocks)
                isLocalChange = true
                contentState = newContent
                viewModel.updateContent(newContent)
                Toast.makeText(context, "Image attached", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to attach image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showAudioRecorderDialog = true
        } else {
            Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    val networkObserver = remember { com.techilyfly.tfplans.util.NetworkConnectivityObserver(context) }
    val isOnline by networkObserver.isOnline.collectAsState(initial = com.techilyfly.tfplans.ui.auth.isOnline(context))
    var showNoInternetDialog by remember { mutableStateOf(false) }

    if (showNoInternetDialog) {
        com.techilyfly.tfplans.ui.components.InternetRequiredDialog(
            message = "An active internet connection is required to use AI features.",
            onDismiss = { showNoInternetDialog = false }
        )
    }

    if (showAudioRecorderDialog) {
        AudioRecorderDialog(
            onDismiss = { showAudioRecorderDialog = false },
            onAudioRecorded = { path ->
                val current = note.content
                val blocks = parseBlocks(current).toMutableList()
                val nextId = (blocks.maxOfOrNull { it.id } ?: 0) + 1
                blocks.add(AudioBlock(nextId, path))
                val newContent = serializeBlocks(blocks)
                isLocalChange = true
                contentState = newContent
                viewModel.updateContent(newContent)
                Toast.makeText(context, "Audio recording attached", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val bgCol = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bgCol.red + 0.587f * bgCol.green + 0.114f * bgCol.blue) < 0.5f
    val noteBg = when {
        note.color != 0 && note.color != -1 && note.color != 0xFFFFFFFF.toInt() && note.color != 0xFFFFFBFE.toInt() -> {
            val r = android.graphics.Color.red(note.color) / 255f
            val g = android.graphics.Color.green(note.color) / 255f
            val b = android.graphics.Color.blue(note.color) / 255f
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            if (isDark && lum > 0.8f) MaterialTheme.colorScheme.surface else Color(note.color)
        }
        else -> MaterialTheme.colorScheme.background
    }
    
    val noteBgLum = 0.299f * noteBg.red + 0.587f * noteBg.green + 0.114f * noteBg.blue
    val isNoteBgDark = noteBgLum < 0.5f
    val dynamicTextColor = if (isNoteBgDark) Color.White else SecondaryColor
    val dynamicIconColor = if (isNoteBgDark) Color.White else PrimaryColor

    val backgroundGradient = ComposeBrush.verticalGradient(
        colors = listOf(noteBg, MaterialTheme.colorScheme.surface)
    )

    val lastEditedTime = remember(note.updatedAt) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        "Last edited ${sdf.format(Date(note.updatedAt))}"
    }

    val updatedTime = remember(note.updatedAt) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        "Updated ${sdf.format(Date(note.updatedAt))}"
    }

    // Category Picker Dialog
    if (showCategoryDialog) {
        CategorySelectionDialog(
            currentCategory = note.category,
            onCategorySelected = { newCat ->
                viewModel.updateCategory(newCat)
            },
            onDismiss = { showCategoryDialog = false }
        )
    }

    // Reminder Picker Dialog
    if (showReminderDialog) {
        ReminderSelectionDialog(
            currentReminderTime = note.reminderTime,
            currentRepeat = note.reminderRepeat,
            onReminderSelected = { time, repeat ->
                viewModel.updateReminder(time, repeat)
                showReminderDialog = false
            },
            onReminderCleared = {
                viewModel.updateReminder(null, null)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    // AI Bottom Sheet and Preview
    if (showAiBottomSheet) {
        AiAssistantBottomSheet(
            onActionSelected = { action -> 
                if (isOnline) {
                    viewModel.performAiAction(action)
                } else {
                    showAiBottomSheet = false
                    showNoInternetDialog = true
                }
            },
            onDismiss = { showAiBottomSheet = false }
        )
    }
    
    if (aiPreviewText != null) {
        AiPreviewDialog(
            previewText = aiPreviewText!!,
            onAccept = { 
                keyboardController?.hide()
                focusManager.clearFocus()
                viewModel.acceptAiSuggestion() 
                isLocalChange = false
            },
            onCancel = { viewModel.dismissAiPreview() }
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalUriHandler provides customUriHandler
    ) {
        if (isEditingMode) {
            // ================= EDIT MODE SCREEN ("Note Editing") =================
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.background(backgroundGradient),
            topBar = {
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
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.saveNote()
                                        if (noteId == null) {
                                            onNavigateBack()
                                        } else {
                                            isEditingMode = false
                                        }
                                    },
                                    modifier = Modifier.size(40.dp).testTag("back_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = PrimaryColor
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Note Editing",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PrimaryColor
                                    )
                                    Text(
                                        text = lastEditedTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondaryColor.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // AI Assistant Button
                                if (isAiLoading) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp).padding(4.dp),
                                        color = PrimaryColor,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = { showAiBottomSheet = true },
                                        modifier = Modifier.size(36.dp).testTag("edit_ai_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = "AI Assistant",
                                            tint = PrimaryColor
                                        )
                                    }
                                }

                                // Done Button
                                Button(
                                    onClick = {
                                        viewModel.saveNote()
                                        Toast.makeText(context, "Note Saved", Toast.LENGTH_SHORT).show()
                                        if (noteId == null) {
                                            onNavigateBack()
                                        } else {
                                            isEditingMode = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = MaterialTheme.colorScheme.onPrimary),
                                    shape = CircleShape,
                                    contentPadding = if (LocalConfiguration.current.screenWidthDp < 360) PaddingValues(12.dp) else PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                                    modifier = Modifier.testTag("done_save_btn")
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Save",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    if (LocalConfiguration.current.screenWidthDp >= 360) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Save",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
                Column(modifier = Modifier.imePadding()) {
                    // Formatting Toolbar
                    Surface(
                    color = SurfaceColor.copy(alpha = 0.95f),
                    shadowElevation = 12.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, PrimaryColor.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 800.dp)
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Format Tools: Checklist
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.08f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            toggleChecklistTrigger++
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Filled.Checklist,
                                            contentDescription = "Checklist",
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.08f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            showTextColorPicker = true
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Filled.FormatColorText,
                                            contentDescription = "Text Color",
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.08f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            showHighlightColorPicker = true
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Filled.Brush,
                                            contentDescription = "Highlight Color",
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.08f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            formatTriggerId++
                                            formatTrigger = com.techilyfly.tfplans.ui.edit.FormatTrigger(formatTriggerId, androidx.compose.ui.text.SpanStyle(), isClearFormatting = true)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Filled.FormatClear,
                                            contentDescription = "Clear Formatting",
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            VerticalDivider(
                                modifier = Modifier
                                    .height(24.dp)
                                    .padding(horizontal = 4.dp),
                                color = PrimaryColor.copy(alpha = 0.2f)
                            )

                            // Right Format Tools: Image, Mic, Palette, AI
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.08f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            imagePickerLauncher.launch("image/*")
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Filled.Image,
                                            contentDescription = "Image",
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.12f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.TopEnd) {
                                        IconButton(
                                            onClick = {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    showAudioRecorderDialog = true
                                                } else {
                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                Icons.Filled.Mic,
                                                contentDescription = "Mic",
                                                tint = PrimaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .padding(3.dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(ErrorColor)
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.08f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.toggleColorPicker() },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Filled.Palette,
                                            contentDescription = "Palette",
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }


                            }
                        }
                    }
                }
                    if (!isImeVisible) {
                        com.techilyfly.tfplans.ui.components.AppBottomBar(
                            currentTab = "",
                            onTabSelected = onNavigateToTab
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxSize()
                        .verticalScroll(mainScrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (showColorPicker) {
                        AdvancedColorPicker(
                            currentColor = note.color,
                            onColorSelected = { viewModel.updateColor(it) },
                            onDismiss = { viewModel.hideColorPicker() }
                        )
                    }

                    if (showTextColorPicker) {
                        AdvancedColorPicker(
                            currentColor = 0,
                            onColorSelected = { colorInt ->
                                formatTriggerId++
                                formatTrigger = com.techilyfly.tfplans.ui.edit.FormatTrigger(formatTriggerId, androidx.compose.ui.text.SpanStyle(color = Color(colorInt)))
                                showTextColorPicker = false
                            },
                            onDismiss = { showTextColorPicker = false }
                        )
                    }

                    if (showHighlightColorPicker) {
                        AdvancedColorPicker(
                            currentColor = 0,
                            onColorSelected = { colorInt ->
                                formatTriggerId++
                                formatTrigger = com.techilyfly.tfplans.ui.edit.FormatTrigger(formatTriggerId, androidx.compose.ui.text.SpanStyle(background = Color(colorInt)))
                                showHighlightColorPicker = false
                            },
                            onDismiss = { showHighlightColorPicker = false }
                        )
                    }

                    // Editable Category & Reminder Chip Row above title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryColor.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showCategoryDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (note.category.isNotBlank()) Icons.Filled.Tag else Icons.Filled.Add,
                                    contentDescription = "Set Category",
                                    tint = PrimaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (note.category.isNotBlank()) "Category: ${note.category}" else "+ Tag",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PrimaryColor
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (hasReminder) PrimaryColor.copy(alpha = 0.18f) else PrimaryColor.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showReminderDialog = true }
                                .testTag("edit_reminder_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val reminderLabel = remember(noteReminderTime) {
                                    if (noteReminderTime != null && noteReminderTime > 0) {
                                        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                        sdf.format(Date(noteReminderTime))
                                    } else {
                                        "+ Set Reminder"
                                    }
                                }
                                Icon(
                                    if (hasReminder) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                                    contentDescription = "Set Reminder",
                                    tint = PrimaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = reminderLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (hasReminder) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = PrimaryColor
                                )
                            }
                        }
                    }

                    val titleSp = FontSizeManager.getTitleSp(fontSize)
                    val bodySp = FontSizeManager.getBodySp(fontSize)

                    // Title TextField
                    TextField(
                        value = note.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        placeholder = {
                            Text(
                                "Note Title",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = titleSp),
                                color = SecondaryColor.copy(alpha = 0.4f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = dynamicIconColor,
                            unfocusedTextColor = dynamicIconColor,
                            cursorColor = dynamicIconColor
                        ),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = titleSp),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("note_title_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NoteBlockEditor(
                        content = contentState,
                        onContentChange = { newText ->
                            isLocalChange = true
                            contentState = newText
                            viewModel.updateContent(newText)
                        },
                        focusedBlockIndex = focusedBlockIndex,
                        toggleChecklistTrigger = toggleChecklistTrigger,
                        formatTrigger = formatTrigger,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        bodySp = bodySp,
                        textColor = dynamicTextColor,
                        iconColor = dynamicIconColor
                    )
                }
            }
        }
    } else {
        // ================= VIEW MODE SCREEN ("Note Details") =================
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.background(backgroundGradient),
            topBar = {
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
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.size(40.dp).testTag("view_back_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = PrimaryColor
                                    )
                                }

                                Text(
                                    text = "Note Details",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryColor
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // AI Summarize Button
                                if (isSummarizing || isAiLoading) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp).padding(4.dp),
                                        color = PrimaryColor,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = { 
                                            if (isOnline) {
                                                showAiBottomSheet = true 
                                            } else {
                                                showNoInternetDialog = true
                                            }
                                        },
                                        modifier = Modifier.size(36.dp).testTag("view_ai_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = "AI Assistant",
                                            tint = PrimaryColor
                                        )
                                    }
                                }

                                // Pin/Unpin Button
                                IconButton(
                                    onClick = {
                                        viewModel.togglePin()
                                        Toast.makeText(context, if (note.isPinned) "Note Unpinned" else "Note Pinned", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp).testTag("view_pin_btn")
                                ) {
                                    Icon(
                                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = "Pin Note",
                                        tint = if (note.isPinned) PrimaryColor else SecondaryColor
                                    )
                                }

                                // Archive Button
                                IconButton(
                                    onClick = {
                                        viewModel.toggleArchive()
                                        Toast.makeText(context, if (note.isArchived) "Note Unarchived" else "Note Archived", Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier.size(36.dp).testTag("view_archive_btn")
                                ) {
                                    Icon(
                                        imageVector = if (note.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                        contentDescription = "Archive Note",
                                        tint = if (note.isArchived) PrimaryColor else SecondaryColor
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = {
                                        viewModel.deleteNote()
                                        Toast.makeText(context, "Note Deleted", Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier.size(36.dp).testTag("view_delete_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete Note",
                                        tint = ErrorColor
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                com.techilyfly.tfplans.ui.components.AppBottomBar(
                    currentTab = "",
                    onTabSelected = onNavigateToTab
                )
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // New Note Add Button
                    FloatingActionButton(
                        onClick = { onNavigateToAddNote?.invoke() },
                        modifier = Modifier.testTag("view_add_note_fab"),
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
                                contentDescription = "Add New Note",
                                tint = BackgroundColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Edit Note Button
                    FloatingActionButton(
                        onClick = { isEditingMode = true },
                        modifier = Modifier.testTag("view_edit_fab"),
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
                                Icons.Filled.Edit,
                                contentDescription = "Edit Note",
                                tint = BackgroundColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxSize()
                        .verticalScroll(mainScrollState)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Category Tag & Reminder display row
                    if (note.category.isNotBlank() || hasReminder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (note.category.isNotBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.12f),
                                    border = BorderStroke(0.5.dp, PrimaryColor.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Tag,
                                            contentDescription = "Category",
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = note.category,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryColor
                                        )
                                    }
                                }
                            }

                            if (hasReminder) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryColor.copy(alpha = 0.18f),
                                    border = BorderStroke(0.5.dp, PrimaryColor.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.NotificationsActive,
                                            contentDescription = "Reminder Active",
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                        Text(
                                            text = sdf.format(Date(noteReminderTime!!)),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Title
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = dynamicIconColor
                        )
                    }

                    val parsedBlocks = remember(contentState) { parseBlocks(contentState) }
                    val bodySp = FontSizeManager.getBodySp(fontSize)

                    // Body blocks viewer
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        parsedBlocks.forEachIndexed { idx, block ->
                            when (block) {
                                is TextBlock -> {
                                    if (block.textFieldValue.text.isNotBlank()) {
                                        Text(
                                            text = block.textFieldValue.annotatedString,
                                            style = TextStyle(
                                                color = dynamicTextColor,
                                                fontSize = bodySp,
                                                lineHeight = (bodySp.value + 8f).sp
                                            ),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        )
                                    }
                                }
                                is ChecklistBlock -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (block.isChecked) dynamicIconColor else Color.Transparent)
                                                .border(
                                                    width = 2.dp,
                                                    color = if (block.isChecked) dynamicIconColor else dynamicTextColor.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    val newBlocks = parsedBlocks.toMutableList()
                                                    newBlocks[idx] = block.copy(isChecked = !block.isChecked)
                                                    val serialized = serializeBlocks(newBlocks)
                                                    contentState = serialized
                                                    viewModel.updateContent(serialized)
                                                    viewModel.saveNote()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (block.isChecked) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = BackgroundColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = block.textFieldValue.annotatedString,
                                            style = TextStyle(
                                                color = if (block.isChecked) dynamicTextColor.copy(alpha = 0.6f) else dynamicTextColor,
                                                fontSize = bodySp,
                                                textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 10.dp)
                                        )
                                    }
                                }
                                is ImageBlock -> {
                                    val parsedUri = Uri.parse(block.uri)
                                    val imageModel: Any = if (parsedUri.scheme == "file" && parsedUri.path != null) {
                                        java.io.File(parsedUri.path!!)
                                    } else {
                                        parsedUri
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = "Attached Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                is AudioBlock -> {
                                    AudioPlayerCard(
                                        audioPath = block.uri,
                                        onDelete = {
                                            val newBlocks = parsedBlocks.toMutableList()
                                            newBlocks.removeAt(idx)
                                            val serialized = serializeBlocks(newBlocks)
                                            contentState = serialized
                                            viewModel.updateContent(serialized)
                                            viewModel.saveNote()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Updated / Timestamp line
                    Text(
                        text = updatedTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = dynamicTextColor.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
        }
    }

    if (browserUrl != null) {
        com.techilyfly.tfplans.ui.settings.InAppBrowserScreen(
            initialUrl = browserUrl!!,
            onDismiss = { browserUrl = null }
        )
    }
}

// Category Selection Dialog
@Composable
private fun CategorySelectionDialog(
    currentCategory: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var customCategory by remember { mutableStateOf(currentCategory) }
    val defaultCategories = listOf("Work", "Personal", "Study", "Ideas", "Important", "Shopping", "Finance", "Quotes")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select or Type Category Tag",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PrimaryColor
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    label = { Text("Category / Tag Name") },
                    placeholder = { Text("e.g. Work, Personal...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryColor,
                        focusedLabelColor = PrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("custom_category_input")
                )

                Text(
                    text = "Quick Suggestions:",
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryColor
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(defaultCategories) { cat ->
                        val isSelected = customCategory.equals(cat, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { customCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryColor,
                                selectedLabelColor = Color.White,
                                containerColor = PrimaryColor.copy(alpha = 0.08f),
                                labelColor = PrimaryColor
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCategorySelected(customCategory.trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Save Tag")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onCategorySelected("")
                    onDismiss()
                }
            ) {
                Text("Remove Tag", color = ErrorColor)
            }
        }
    )
}

// Reminder Selection Dialog
@Composable
private fun ReminderSelectionDialog(
    currentReminderTime: Long?,
    currentRepeat: String?,
    onReminderSelected: (Long, String?) -> Unit,
    onReminderCleared: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedRepeat by remember { mutableStateOf(currentRepeat ?: "NONE") }
    var showRepeatDropdown by remember { mutableStateOf(false) }

    // Check notification permission (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // Launcher for notification permission request
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission denied. Reminders won't show notifications.", Toast.LENGTH_LONG).show()
        }
    }

    // Check exact alarm permission (Android 12+)
    var hasExactAlarmPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                alarmManager?.canScheduleExactAlarms() ?: true
            } else true
        )
    }

    val currentFormatted = remember(currentReminderTime, currentRepeat) {
        if (currentReminderTime != null && currentReminderTime > 0) {
            val sdf = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            val base = sdf.format(Date(currentReminderTime))
            val repStr = if (currentRepeat.isNullOrEmpty() || currentRepeat == "NONE") "Once" else currentRepeat.lowercase().replaceFirstChar { it.uppercase() }
            "$base ($repStr)"
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = PrimaryColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Set Note Reminder",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryColor
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Explanation/Guide for Permissions
                if (!hasNotificationPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, ErrorColor.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Notification Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = ErrorColor,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "To show reminders when the app is closed, please allow notifications.",
                                color = SecondaryColor,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = MaterialTheme.colorScheme.onError),
                                modifier = Modifier.align(Alignment.End).height(28.dp)
                            ) {
                                Text("Allow", fontSize = 11.sp, color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                }

                if (!hasExactAlarmPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryColor.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Exact Alarm Permission Recommended",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Ensure high-priority reminders trigger at the precise minute without system battery delay.",
                                color = SecondaryColor,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        context.startActivity(intent)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = MaterialTheme.colorScheme.onPrimary),
                                modifier = Modifier.align(Alignment.End).height(28.dp)
                            ) {
                                Text("Configure", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }

                if (currentFormatted != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Alarm, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Active Reminder:", style = MaterialTheme.typography.labelSmall, color = SecondaryColor)
                                Text(currentFormatted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PrimaryColor)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Choose when you would like to be reminded about this note:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryColor
                    )
                }

                HorizontalDivider(color = PrimaryColor.copy(alpha = 0.1f))

                // Repeat Selection Option (Interactive Dropdown Menu)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showRepeatDropdown = true }
                            .border(1.dp, PrimaryColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Repeat Pattern", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryColor)
                                Text(
                                    text = when (selectedRepeat) {
                                        "DAILY" -> "Daily"
                                        "WEEKLY" -> "Weekly"
                                        "MONTHLY" -> "Monthly"
                                        "YEARLY" -> "Yearly"
                                        else -> "Don't repeat"
                                    },
                                    fontSize = 12.sp,
                                    color = SecondaryColor
                                )
                            }
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = SecondaryColor)
                    }

                    DropdownMenu(
                        expanded = showRepeatDropdown,
                        onDismissRequest = { showRepeatDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(SurfaceColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Don't repeat") },
                            onClick = { selectedRepeat = "NONE"; showRepeatDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Daily") },
                            onClick = { selectedRepeat = "DAILY"; showRepeatDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Weekly") },
                            onClick = { selectedRepeat = "WEEKLY"; showRepeatDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Monthly") },
                            onClick = { selectedRepeat = "MONTHLY"; showRepeatDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Yearly") },
                            onClick = { selectedRepeat = "YEARLY"; showRepeatDropdown = false }
                        )
                    }
                }

                // Presets
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 18)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        onReminderSelected(cal.timeInMillis, if (selectedRepeat == "NONE") null else selectedRepeat)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Today at 6:00 PM", color = PrimaryColor)
                }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        onReminderSelected(cal.timeInMillis, if (selectedRepeat == "NONE") null else selectedRepeat)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tomorrow at 9:00 AM", color = PrimaryColor)
                }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.WEEK_OF_YEAR, 1)
                            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        onReminderSelected(cal.timeInMillis, if (selectedRepeat == "NONE") null else selectedRepeat)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Next Week (Mon 9:00 AM)", color = PrimaryColor)
                }

                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        val timePicker = TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                cal.set(Calendar.SECOND, 0)
                                if (cal.timeInMillis <= System.currentTimeMillis()) {
                                    Toast.makeText(context, "Cannot set reminder in the past", Toast.LENGTH_SHORT).show()
                                } else {
                                    onReminderSelected(cal.timeInMillis, if (selectedRepeat == "NONE") null else selectedRepeat)
                                }
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false
                        )
                        val datePicker = DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                cal.set(Calendar.YEAR, year)
                                cal.set(Calendar.MONTH, month)
                                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                timePicker.show()
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                        datePicker.show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Custom Date & Time...", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        confirmButton = {
            if (currentReminderTime != null && currentReminderTime > 0) {
                TextButton(
                    onClick = { onReminderCleared() }
                ) {
                    Text("Clear Reminder", color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondaryColor)
            }
        },
        containerColor = SurfaceColor,
        shape = RoundedCornerShape(20.dp)
    )
}

// Helper function to toggle a checklist line
private fun toggleChecklistItem(content: String, targetLine: String): String {
    val lines = content.split("\n")
    val updatedLines = lines.map { line ->
        if (line.trim() == targetLine.trim()) {
            if (line.contains("[x]")) {
                line.replace("[x]", "[ ]")
            } else if (line.contains("[ ]")) {
                line.replace("[ ]", "[x]")
            } else {
                line
            }
        } else {
            line
        }
    }
    return updatedLines.joinToString("\n")
}

@Composable
fun AudioRecorderDialog(
    onDismiss: () -> Unit,
    onAudioRecorded: (String) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recordedPath by remember { mutableStateOf<String?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val filePath = remember {
        "${context.cacheDir.absolutePath}/audio_${System.currentTimeMillis()}.3gp"
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.release()
                mediaPlayer?.release()
            } catch (e: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Recording", fontWeight = FontWeight.Bold, color = PrimaryColor) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = if (isRecording) ErrorColor else PrimaryColor,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = when {
                        isRecording -> "Recording audio..."
                        recordedPath != null -> "Recording saved. Ready to attach."
                        else -> "Tap start to record audio note."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryColor
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isRecording && recordedPath == null) {
                        Button(
                            onClick = {
                                try {
                                    val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        MediaRecorder(context)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        MediaRecorder()
                                    }
                                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                                    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                                    recorder.setOutputFile(filePath)
                                    recorder.prepare()
                                    recorder.start()
                                    mediaRecorder = recorder
                                    isRecording = true
                                    Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Text("Start")
                        }
                    }

                    if (isRecording) {
                        Button(
                            onClick = {
                                try {
                                    mediaRecorder?.stop()
                                    mediaRecorder?.release()
                                    mediaRecorder = null
                                    isRecording = false
                                    recordedPath = filePath
                                    Toast.makeText(context, "Recording saved", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error stopping recording", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = MaterialTheme.colorScheme.onError)
                        ) {
                            Text("Stop")
                        }
                    }

                    if (recordedPath != null) {
                        Button(
                            onClick = {
                                try {
                                    if (isPlaying) {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = null
                                        isPlaying = false
                                    } else {
                                        val player = MediaPlayer().apply {
                                            setDataSource(recordedPath)
                                            prepare()
                                            start()
                                            setOnCompletionListener {
                                                isPlaying = false
                                                release()
                                                mediaPlayer = null
                                            }
                                        }
                                        mediaPlayer = player
                                        isPlaying = true
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryColor, contentColor = MaterialTheme.colorScheme.onSecondary)
                        ) {
                            Text(if (isPlaying) "Stop" else "Play")
                        }

                        Button(
                            onClick = {
                                recordedPath = null
                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray, contentColor = Color.White)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (recordedPath != null) {
                Button(
                    onClick = {
                        onAudioRecorded(recordedPath!!)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Attach")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AudioPlayerCard(audioPath: String, onDelete: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {}
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryColor.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = {
                        try {
                            if (isPlaying) {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                                isPlaying = false
                            } else {
                                val player = MediaPlayer().apply {
                                    setDataSource(audioPath)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        release()
                                        mediaPlayer = null
                                    }
                                }
                                mediaPlayer = player
                                isPlaying = true
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(40.dp).background(PrimaryColor, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }
                Column {
                    Text("Audio Note Recording", fontWeight = FontWeight.Bold, color = PrimaryColor, fontSize = 14.sp)
                    Text(if (isPlaying) "Playing..." else "Tap to play recording", fontSize = 12.sp, color = SecondaryColor)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete Audio", tint = ErrorColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantBottomSheet(
    onActionSelected: (AiAction) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "✨ AI Writing Assistant",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val actions = listOf(
                Pair(AiAction.SUMMARIZE, "Summarize Note"),
                Pair(AiAction.FIX_GRAMMAR, "Fix Grammar & Spelling"),
                Pair(AiAction.REWRITE, "Rewrite Professionally"),
                Pair(AiAction.EXPAND, "Expand Ideas"),
                Pair(AiAction.CONTINUE_WRITING, "Continue Writing"),
                Pair(AiAction.GENERATE_TITLE, "Generate Title")
            )

            actions.forEach { (action, label) ->
                Button(
                    onClick = {
                        onActionSelected(action)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryColor)
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
fun AiPreviewDialog(
    previewText: String,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PrimaryColor, modifier = Modifier.padding(end = 8.dp))
                Text("AI Suggestion", color = PrimaryColor, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(previewText, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Accept & Apply", color = PrimaryColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = ErrorColor)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
// Helper function to copy URI to internal storage to retain access across app restarts
private fun copyUriToInternalStorage(context: Context, uri: Uri): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val imagesDir = java.io.File(context.filesDir, "note_images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val file = java.io.File(imagesDir, fileName)
        val outputStream = java.io.FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
