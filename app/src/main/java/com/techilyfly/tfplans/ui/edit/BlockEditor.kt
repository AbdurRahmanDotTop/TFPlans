package com.techilyfly.tfplans.ui.edit

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.techilyfly.tfplans.ui.theme.BackgroundColor
import com.techilyfly.tfplans.ui.theme.PrimaryColor
import com.techilyfly.tfplans.ui.theme.SecondaryColor
import com.techilyfly.tfplans.ui.theme.ErrorColor

import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.toArgb

sealed class NoteBlock {
    abstract val id: Int
}
data class TextBlock(override val id: Int, var textFieldValue: TextFieldValue) : NoteBlock()
data class ChecklistBlock(override val id: Int, var textFieldValue: TextFieldValue, var isChecked: Boolean) : NoteBlock()
data class ImageBlock(override val id: Int, val uri: String) : NoteBlock()
data class AudioBlock(override val id: Int, val uri: String) : NoteBlock()

fun parseBlocks(content: String): List<NoteBlock> {
    val blocks = mutableListOf<NoteBlock>()
    var id = 0

    if (content.trimStart().startsWith("[")) {
        try {
            val jsonArray = JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                when (obj.getString("type")) {
                    "text", "checklist" -> {
                        val text = obj.getString("text")
                        val builder = AnnotatedString.Builder(text)
                        
                        if (obj.has("spans")) {
                            val spansArray = obj.getJSONArray("spans")
                            for (j in 0 until spansArray.length()) {
                                val spanObj = spansArray.getJSONObject(j)
                                val start = spanObj.getInt("start")
                                val end = spanObj.getInt("end")
                                
                                var color = Color.Unspecified
                                if (spanObj.has("c")) color = Color(spanObj.getInt("c"))
                                
                                var bg = Color.Unspecified
                                if (spanObj.has("bg")) bg = Color(spanObj.getInt("bg"))
                                
                                var fw: androidx.compose.ui.text.font.FontWeight? = null
                                if (spanObj.has("fw")) fw = androidx.compose.ui.text.font.FontWeight(spanObj.getInt("fw"))
                                
                                var fs: androidx.compose.ui.text.font.FontStyle? = null
                                if (spanObj.has("fs")) {
                                    fs = if (spanObj.getInt("fs") == 1) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                                }
                                
                                var td: TextDecoration? = null
                                if (spanObj.has("td_u") || spanObj.has("td_s")) {
                                    val decos = mutableListOf<TextDecoration>()
                                    if (spanObj.optBoolean("td_u", false)) decos.add(TextDecoration.Underline)
                                    if (spanObj.optBoolean("td_s", false)) decos.add(TextDecoration.LineThrough)
                                    if (decos.isNotEmpty()) td = TextDecoration.combine(decos)
                                }
                                
                                if (color != Color.Unspecified || bg != Color.Unspecified || fw != null || fs != null || td != null) {
                                    builder.addStyle(SpanStyle(color = color, background = bg, fontWeight = fw, fontStyle = fs, textDecoration = td), start.coerceAtMost(text.length), end.coerceAtMost(text.length))
                                }
                            }
                        }
                        
                        val textFieldValue = TextFieldValue(builder.toAnnotatedString())
                        
                        if (obj.getString("type") == "checklist") {
                            blocks.add(ChecklistBlock(id++, textFieldValue, obj.optBoolean("checked", false)))
                        } else {
                            blocks.add(TextBlock(id++, textFieldValue))
                        }
                    }
                    "image" -> {
                        blocks.add(ImageBlock(id++, obj.getString("uri")))
                    }
                    "audio" -> {
                        blocks.add(AudioBlock(id++, obj.getString("uri")))
                    }
                }
            }
            if (blocks.isEmpty()) blocks.add(TextBlock(id++, TextFieldValue("")))
            return blocks
        } catch (e: Exception) {
            // Fallback to legacy parsing if JSON parsing fails
        }
    }

    // Legacy Parsing
    val lines = content.split("\n")
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]")) {
            val isChecked = line.contains("[x]")
            val text = line.substringAfter("] ")
            blocks.add(ChecklistBlock(id++, TextFieldValue(text), isChecked))
        } else if (trimmed.startsWith("[Image: ") && trimmed.endsWith("]")) {
            val uri = trimmed.substringAfter("[Image: ").substringBeforeLast("]")
            blocks.add(ImageBlock(id++, uri))
        } else if (trimmed.startsWith("[Audio: ") && trimmed.endsWith("]")) {
            val uri = trimmed.substringAfter("[Audio: ").substringBeforeLast("]")
            blocks.add(AudioBlock(id++, uri))
        } else if (trimmed == "[Image Attachment]" || trimmed == "[Audio Note Recording]") {
            // Ignore legacy placeholders
        } else {
            blocks.add(TextBlock(id++, TextFieldValue(line)))
        }
    }
    
    if (blocks.isEmpty()) {
        blocks.add(TextBlock(id++, TextFieldValue("")))
    }
    return blocks
}

private fun extractSpansToJson(annotatedString: AnnotatedString): JSONArray {
    val spansArray = JSONArray()
    annotatedString.spanStyles.forEach { span ->
        val spanObj = JSONObject()
        spanObj.put("start", span.start)
        spanObj.put("end", span.end)
        
        if (span.item.color != Color.Unspecified) {
            spanObj.put("c", span.item.color.toArgb())
        }
        if (span.item.background != Color.Unspecified) {
            spanObj.put("bg", span.item.background.toArgb())
        }
        
        if (span.item.fontWeight != null) {
            spanObj.put("fw", span.item.fontWeight!!.weight)
        }
        if (span.item.fontStyle != null) {
            spanObj.put("fs", if (span.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic) 1 else 0)
        }
        if (span.item.textDecoration != null) {
            val deco = span.item.textDecoration!!
            if (deco.contains(TextDecoration.Underline)) spanObj.put("td_u", true)
            if (deco.contains(TextDecoration.LineThrough)) spanObj.put("td_s", true)
        }
        
        if (spanObj.has("c") || spanObj.has("bg") || spanObj.has("fw") || spanObj.has("fs") || spanObj.has("td_u") || spanObj.has("td_s")) {
            spansArray.put(spanObj)
        }
    }
    return spansArray
}

fun serializeBlocks(blocks: List<NoteBlock>): String {
    val jsonArray = JSONArray()
    blocks.forEach { block ->
        val obj = JSONObject()
        when (block) {
            is TextBlock -> {
                obj.put("type", "text")
                obj.put("text", block.textFieldValue.text)
                obj.put("spans", extractSpansToJson(block.textFieldValue.annotatedString))
            }
            is ChecklistBlock -> {
                obj.put("type", "checklist")
                obj.put("text", block.textFieldValue.text)
                obj.put("checked", block.isChecked)
                obj.put("spans", extractSpansToJson(block.textFieldValue.annotatedString))
            }
            is ImageBlock -> {
                obj.put("type", "image")
                obj.put("uri", block.uri)
            }
            is AudioBlock -> {
                obj.put("type", "audio")
                obj.put("uri", block.uri)
            }
        }
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}

fun parseAndMergeBlocks(content: String, existingBlocks: List<NoteBlock>): List<NoteBlock> {
    val newBlocks = parseBlocks(content)
    return newBlocks.mapIndexed { index, newBlock ->
        val existing = existingBlocks.getOrNull(index)
        if (existing != null) {
            when {
                newBlock is TextBlock && existing is TextBlock -> {
                    if (newBlock.textFieldValue.annotatedString == existing.textFieldValue.annotatedString) {
                        existing
                    } else {
                        val newSelection = existing.textFieldValue.selection
                        val maxSel = newBlock.textFieldValue.text.length
                        val selection = TextRange(
                            newSelection.start.coerceIn(0, maxSel),
                            newSelection.end.coerceIn(0, maxSel)
                        )
                        newBlock.copy(textFieldValue = newBlock.textFieldValue.copy(selection = selection))
                    }
                }
                newBlock is ChecklistBlock && existing is ChecklistBlock -> {
                    if (newBlock.textFieldValue.annotatedString == existing.textFieldValue.annotatedString && newBlock.isChecked == existing.isChecked) {
                        existing
                    } else {
                        val newSelection = existing.textFieldValue.selection
                        val maxSel = newBlock.textFieldValue.text.length
                        val selection = TextRange(
                            newSelection.start.coerceIn(0, maxSel),
                            newSelection.end.coerceIn(0, maxSel)
                        )
                        newBlock.copy(textFieldValue = newBlock.textFieldValue.copy(selection = selection))
                    }
                }
                else -> newBlock
            }
        } else {
            newBlock
        }
    }
}

fun getDiffAnnotatedString(oldString: AnnotatedString, newText: String, activeStyle: SpanStyle): AnnotatedString {
    val oldText = oldString.text
    if (oldText == newText) return oldString
    
    var prefixLength = 0
    while (prefixLength < oldText.length && prefixLength < newText.length && oldText[prefixLength] == newText[prefixLength]) {
        prefixLength++
    }
    
    var suffixLength = 0
    while (suffixLength < oldText.length - prefixLength && suffixLength < newText.length - prefixLength && oldText[oldText.length - 1 - suffixLength] == newText[newText.length - 1 - suffixLength]) {
        suffixLength++
    }
    
    val deletedEnd = oldText.length - suffixLength
    val insertedEnd = newText.length - suffixLength
    
    val builder = AnnotatedString.Builder()
    if (prefixLength > 0) {
        builder.append(oldString.subSequence(0, prefixLength))
    }
    if (insertedEnd > prefixLength) {
        val insertedText = newText.substring(prefixLength, insertedEnd)
        builder.append(AnnotatedString(insertedText, spanStyle = activeStyle))
    }
    if (deletedEnd < oldText.length) {
        builder.append(oldString.subSequence(deletedEnd, oldText.length))
    }
    return builder.toAnnotatedString()
}

data class FormatTrigger(val id: Int, val spanStyle: SpanStyle, val isClearFormatting: Boolean = false)

@Composable
fun NoteBlockEditor(
    content: String,
    onContentChange: (String) -> Unit,
    focusedBlockIndex: MutableState<Int?>,
    toggleChecklistTrigger: Int,
    formatTrigger: FormatTrigger? = null,
    modifier: Modifier = Modifier,
    bodySp: TextUnit = 16.sp,
    textColor: Color = SecondaryColor,
    iconColor: Color = PrimaryColor
) {
    var blocks by remember { mutableStateOf(parseBlocks(content)) }
    var activeSpanStyle by remember { mutableStateOf(SpanStyle()) }
    var lastSentContent by remember { mutableStateOf(content) }
    val recentLocalSerials = remember { java.util.LinkedHashSet<String>() }
    
    LaunchedEffect(content) {
        if (recentLocalSerials.contains(content)) {
            if (content == lastSentContent) {
                recentLocalSerials.clear()
            }
            return@LaunchedEffect
        }
        val currentLocalSerialized = serializeBlocks(blocks)
        if (content != currentLocalSerialized) {
            blocks = parseAndMergeBlocks(content, blocks)
            lastSentContent = content
        }
    }

    var focusRequestIndex by remember { mutableStateOf<Int?>(null) }
    val focusRequesters = remember { mutableStateListOf<FocusRequester>() }
    while (focusRequesters.size < blocks.size) {
        focusRequesters.add(FocusRequester())
    }
    
    LaunchedEffect(blocks.size, focusRequestIndex) {
        focusRequestIndex?.let { idx ->
            if (idx in focusRequesters.indices) {
                try {
                    focusRequesters[idx].requestFocus()
                    focusRequestIndex = null
                } catch (e: Exception) {}
            }
        }
    }
    
    fun updateBlocks(newBlocks: List<NoteBlock>, indexToFocus: Int? = null) {
        blocks = newBlocks
        if (indexToFocus != null) {
            focusRequestIndex = indexToFocus
        }
        val serialized = serializeBlocks(newBlocks)
        recentLocalSerials.add(serialized)
        if (recentLocalSerials.size > 50) {
            val iterator = recentLocalSerials.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        lastSentContent = serialized
        onContentChange(serialized)
    }

    LaunchedEffect(toggleChecklistTrigger) {
        if (toggleChecklistTrigger > 0) {
            val index = focusedBlockIndex.value ?: blocks.lastIndex
            if (index in blocks.indices) {
                val block = blocks[index]
                val newBlocks = blocks.toMutableList()
                if (block is TextBlock) {
                    newBlocks[index] = ChecklistBlock(block.id, block.textFieldValue, false)
                } else if (block is ChecklistBlock) {
                    newBlocks[index] = TextBlock(block.id, block.textFieldValue)
                }
                updateBlocks(newBlocks)
            }
        }
    }

    LaunchedEffect(formatTrigger) {
        formatTrigger?.let { trigger ->
            if (trigger.isClearFormatting) {
                activeSpanStyle = SpanStyle()
            } else {
                activeSpanStyle = activeSpanStyle.merge(trigger.spanStyle)
            }
            val index = focusedBlockIndex.value
            if (index != null && index in blocks.indices) {
                val block = blocks[index]
                if (block is TextBlock || block is ChecklistBlock) {
                    val tfv = if (block is TextBlock) block.textFieldValue else (block as ChecklistBlock).textFieldValue
                    val selection = tfv.selection
                        if (!selection.collapsed) {
                            val newAnnotatedString = if (trigger.isClearFormatting) {
                                val builder = AnnotatedString.Builder(tfv.text)
                                tfv.annotatedString.spanStyles.forEach { span ->
                                    if (span.end <= selection.start || span.start >= selection.end) {
                                        builder.addStyle(span.item, span.start, span.end)
                                    } else {
                                        if (span.start < selection.start) builder.addStyle(span.item, span.start, selection.start)
                                        if (span.end > selection.end) builder.addStyle(span.item, selection.end, span.end)
                                    }
                                }
                                builder.toAnnotatedString()
                            } else {
                                val newBuilder = AnnotatedString.Builder(tfv.annotatedString)
                                newBuilder.addStyle(trigger.spanStyle, selection.start, selection.end)
                                newBuilder.toAnnotatedString()
                            }
                            val newTfv = tfv.copy(
                                annotatedString = newAnnotatedString,
                                selection = TextRange(selection.end)
                            )
                            val newBlocks = blocks.toMutableList()
                            if (block is TextBlock) {
                                newBlocks[index] = block.copy(textFieldValue = newTfv)
                            } else if (block is ChecklistBlock) {
                                newBlocks[index] = block.copy(textFieldValue = newTfv)
                            }
                            updateBlocks(newBlocks, index)
                        } else {
                            // If selection is collapsed, apply style to the current word
                            val text = tfv.text
                            if (text.isNotEmpty() && selection.start in 0..text.length) {
                                val cursor = selection.start
                                val start = text.take(cursor).indexOfLast { it.isWhitespace() }.let { if (it == -1) 0 else it + 1 }
                                val end = text.drop(cursor).indexOfFirst { it.isWhitespace() }.let { if (it == -1) text.length else cursor + it }
                                
                                if (start < end) {
                                    val newAnnotatedString = if (trigger.isClearFormatting) {
                                        val builder = AnnotatedString.Builder(tfv.text)
                                        tfv.annotatedString.spanStyles.forEach { span ->
                                            if (span.end <= start || span.start >= end) {
                                                builder.addStyle(span.item, span.start, span.end)
                                            } else {
                                                if (span.start < start) builder.addStyle(span.item, span.start, start)
                                                if (span.end > end) builder.addStyle(span.item, end, span.end)
                                            }
                                        }
                                        builder.toAnnotatedString()
                                    } else {
                                        val newBuilder = AnnotatedString.Builder(tfv.annotatedString)
                                        newBuilder.addStyle(trigger.spanStyle, start, end)
                                        newBuilder.toAnnotatedString()
                                    }
                                    val newTfv = tfv.copy(
                                        annotatedString = newAnnotatedString
                                    )
                                    val newBlocks = blocks.toMutableList()
                                    if (block is TextBlock) {
                                        newBlocks[index] = block.copy(textFieldValue = newTfv)
                                    } else if (block is ChecklistBlock) {
                                        newBlocks[index] = block.copy(textFieldValue = newTfv)
                                    }
                                    updateBlocks(newBlocks, index)
                                }
                            }
                        }
                    }
                }
            }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            key(block.id) {
                val focusRequester = if (index < focusRequesters.size) focusRequesters[index] else FocusRequester()
                
                when (block) {
                is TextBlock -> {
                    BasicTextField(
                        value = block.textFieldValue,
                        onValueChange = { newValue ->
                            val newBlocks = blocks.toMutableList()
                            if (newValue.text.contains("\n")) {
                                val diffedAnnotatedString = getDiffAnnotatedString(
                                    oldString = block.textFieldValue.annotatedString,
                                    newText = newValue.text,
                                    activeStyle = activeSpanStyle
                                )
                                var currentString = diffedAnnotatedString
                                val newCreatedBlocks = mutableListOf<TextBlock>()
                                while (currentString.text.contains("\n")) {
                                    val nlIndex = currentString.text.indexOf("\n")
                                    val part1 = currentString.subSequence(0, nlIndex)
                                    val part2 = currentString.subSequence(nlIndex + 1, currentString.length)
                                    newCreatedBlocks.add(TextBlock(-1, TextFieldValue(part1)))
                                    currentString = part2
                                }
                                newCreatedBlocks.add(TextBlock(-1, TextFieldValue(currentString, TextRange(currentString.text.length))))
                                
                                newBlocks[index] = block.copy(textFieldValue = newCreatedBlocks[0].textFieldValue)
                                var currIdx = index + 1
                                for (i in 1 until newCreatedBlocks.size) {
                                    val nextId = (newBlocks.maxOfOrNull { it.id } ?: 0) + 1
                                    newBlocks.add(currIdx, TextBlock(nextId, newCreatedBlocks[i].textFieldValue))
                                    currIdx++
                                }
                                updateBlocks(newBlocks, currIdx - 1)
                            } else {
                                var updatedTfv = newValue
                                if (newValue.text == block.textFieldValue.text) {
                                    val selStart = newValue.selection.start
                                    if (selStart > 0 && selStart <= block.textFieldValue.annotatedString.length) {
                                        val stylesAtCursor = block.textFieldValue.annotatedString.spanStyles.filter { 
                                            selStart > it.start && selStart <= it.end 
                                        }
                                        var mergedStyle = SpanStyle()
                                        stylesAtCursor.forEach { mergedStyle = mergedStyle.merge(it.item) }
                                        activeSpanStyle = mergedStyle
                                    } else if (selStart == 0 && block.textFieldValue.annotatedString.isNotEmpty()) {
                                        val stylesAtCursor = block.textFieldValue.annotatedString.spanStyles.filter { 
                                            it.start == 0 
                                        }
                                        var mergedStyle = SpanStyle()
                                        stylesAtCursor.forEach { mergedStyle = mergedStyle.merge(it.item) }
                                        activeSpanStyle = mergedStyle
                                    }
                                    updatedTfv = block.textFieldValue.copy(selection = newValue.selection, composition = newValue.composition)
                                } else {
                                    val newAnnotatedString = getDiffAnnotatedString(
                                        oldString = block.textFieldValue.annotatedString,
                                        newText = newValue.text,
                                        activeStyle = activeSpanStyle
                                    )
                                    updatedTfv = newValue.copy(annotatedString = newAnnotatedString)
                                }
                                newBlocks[index] = block.copy(textFieldValue = updatedTfv)
                                updateBlocks(newBlocks)
                            }
                        },
                        textStyle = TextStyle(
                            color = textColor,
                            fontSize = bodySp,
                            lineHeight = (bodySp.value + 8f).sp
                        ),
                        cursorBrush = SolidColor(iconColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) focusedBlockIndex.value = index }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Backspace && block.textFieldValue.selection.start == 0 && keyEvent.type == KeyEventType.KeyUp) {
                                    if (index > 0) {
                                        val newBlocks = blocks.toMutableList()
                                        val prevBlock = newBlocks[index - 1]
                                        if (prevBlock is TextBlock) {
                                            val combinedString = prevBlock.textFieldValue.annotatedString + block.textFieldValue.annotatedString
                                            newBlocks[index - 1] = prevBlock.copy(textFieldValue = TextFieldValue(combinedString, TextRange(prevBlock.textFieldValue.text.length)))
                                            newBlocks.removeAt(index)
                                            updateBlocks(newBlocks, index - 1)
                                            return@onKeyEvent true
                                        } else if (prevBlock is ChecklistBlock) {
                                            val combinedString = prevBlock.textFieldValue.annotatedString + block.textFieldValue.annotatedString
                                            newBlocks[index - 1] = prevBlock.copy(textFieldValue = TextFieldValue(combinedString, TextRange(prevBlock.textFieldValue.text.length)))
                                            newBlocks.removeAt(index)
                                            updateBlocks(newBlocks, index - 1)
                                            return@onKeyEvent true
                                        }
                                    }
                                }
                                false
                            },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        decorationBox = { innerTextField ->
                            Box {
                                if (block.textFieldValue.text.isEmpty() && blocks.size == 1) {
                                    Text("Note", color = textColor.copy(alpha = 0.5f), fontSize = bodySp)
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                is ChecklistBlock -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        var dragOffset by remember { mutableFloatStateOf(0f) }
                        val density = LocalDensity.current
                        
                        Icon(
                            Icons.Filled.DragIndicator,
                            contentDescription = "Drag to reorder",
                            tint = textColor.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 4.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = { dragOffset = 0f },
                                        onDragCancel = { dragOffset = 0f }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                        val itemHeightPx = with(density) { 48.dp.toPx() }
                                        
                                        if (dragOffset > itemHeightPx && index < blocks.size - 1) {
                                            val newBlocks = blocks.toMutableList()
                                            val temp = newBlocks[index]
                                            newBlocks[index] = newBlocks[index + 1]
                                            newBlocks[index + 1] = temp
                                            updateBlocks(newBlocks)
                                            dragOffset -= itemHeightPx
                                        } else if (dragOffset < -itemHeightPx && index > 0) {
                                            val newBlocks = blocks.toMutableList()
                                            val temp = newBlocks[index]
                                            newBlocks[index] = newBlocks[index - 1]
                                            newBlocks[index - 1] = temp
                                            updateBlocks(newBlocks)
                                            dragOffset += itemHeightPx
                                        }
                                    }
                                }
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (block.isChecked) iconColor else Color.Transparent)
                                .border(
                                    width = 2.dp,
                                    color = if (block.isChecked) iconColor else textColor.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    val newBlocks = blocks.toMutableList()
                                    newBlocks[index] = block.copy(isChecked = !block.isChecked)
                                    updateBlocks(newBlocks)
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
                        
                        BasicTextField(
                            value = block.textFieldValue,
                            onValueChange = { newValue ->
                                val newBlocks = blocks.toMutableList()
                                if (newValue.text.contains("\n")) {
                                    val nlIndex = newValue.text.indexOf("\n")
                                    val part1 = newValue.annotatedString.subSequence(0, nlIndex)
                                    val part2 = newValue.annotatedString.subSequence(nlIndex + 1, newValue.annotatedString.length)
                                    if (block.textFieldValue.text.isEmpty() && part1.text.isEmpty()) {
                                        newBlocks[index] = TextBlock(block.id, TextFieldValue(""))
                                        updateBlocks(newBlocks, index)
                                    } else {
                                        newBlocks[index] = block.copy(textFieldValue = TextFieldValue(part1))
                                        val nextId = (newBlocks.maxOfOrNull { it.id } ?: 0) + 1
                                        newBlocks.add(index + 1, ChecklistBlock(nextId, TextFieldValue(part2, TextRange(part2.text.length)), false))
                                        updateBlocks(newBlocks, index + 1)
                                    }
                                } else {
                                    var updatedTfv = newValue
                                    if (newValue.text == block.textFieldValue.text) {
                                        val selStart = newValue.selection.start
                                        if (selStart > 0 && selStart <= block.textFieldValue.annotatedString.length) {
                                            val stylesAtCursor = block.textFieldValue.annotatedString.spanStyles.filter { 
                                                selStart > it.start && selStart <= it.end 
                                            }
                                            var mergedStyle = SpanStyle()
                                            stylesAtCursor.forEach { mergedStyle = mergedStyle.merge(it.item) }
                                            activeSpanStyle = mergedStyle
                                        } else if (selStart == 0 && block.textFieldValue.annotatedString.isNotEmpty()) {
                                            val stylesAtCursor = block.textFieldValue.annotatedString.spanStyles.filter { 
                                                it.start == 0 
                                            }
                                            var mergedStyle = SpanStyle()
                                            stylesAtCursor.forEach { mergedStyle = mergedStyle.merge(it.item) }
                                            activeSpanStyle = mergedStyle
                                        }
                                        updatedTfv = block.textFieldValue.copy(selection = newValue.selection, composition = newValue.composition)
                                    } else {
                                        val newAnnotatedString = getDiffAnnotatedString(
                                            oldString = block.textFieldValue.annotatedString,
                                            newText = newValue.text,
                                            activeStyle = activeSpanStyle
                                        )
                                        updatedTfv = newValue.copy(annotatedString = newAnnotatedString)
                                    }
                                    newBlocks[index] = block.copy(textFieldValue = updatedTfv)
                                    updateBlocks(newBlocks)
                                }
                            },
                            textStyle = TextStyle(
                                color = if (block.isChecked) textColor.copy(alpha = 0.6f) else textColor,
                                fontSize = bodySp,
                                textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            cursorBrush = SolidColor(iconColor),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (it.isFocused) focusedBlockIndex.value = index }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.key == Key.Backspace && block.textFieldValue.selection.start == 0 && keyEvent.type == KeyEventType.KeyUp) {
                                        if (index > 0) {
                                            val newBlocks = blocks.toMutableList()
                                            val prevBlock = newBlocks[index - 1]
                                            if (prevBlock is TextBlock) {
                                                val combinedString = prevBlock.textFieldValue.annotatedString + block.textFieldValue.annotatedString
                                                newBlocks[index - 1] = prevBlock.copy(textFieldValue = TextFieldValue(combinedString, TextRange(prevBlock.textFieldValue.text.length)))
                                                newBlocks.removeAt(index)
                                                updateBlocks(newBlocks, index - 1)
                                                return@onKeyEvent true
                                            } else if (prevBlock is ChecklistBlock) {
                                                val combinedString = prevBlock.textFieldValue.annotatedString + block.textFieldValue.annotatedString
                                                newBlocks[index - 1] = prevBlock.copy(textFieldValue = TextFieldValue(combinedString, TextRange(prevBlock.textFieldValue.text.length)))
                                                newBlocks.removeAt(index)
                                                updateBlocks(newBlocks, index - 1)
                                                return@onKeyEvent true
                                            }
                                        }
                                    }
                                    false
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    val newBlocks = blocks.toMutableList()
                                    if (block.textFieldValue.text.isEmpty()) {
                                        newBlocks[index] = TextBlock(block.id, TextFieldValue(""))
                                    } else {
                                        val nextId = (newBlocks.maxOfOrNull { it.id } ?: 0) + 1
                                        newBlocks.add(index + 1, ChecklistBlock(nextId, TextFieldValue(""), false))
                                    }
                                    updateBlocks(newBlocks, index + 1)
                                }
                            )
                        )
                        
                        IconButton(
                            onClick = {
                                val newBlocks = blocks.toMutableList()
                                newBlocks.removeAt(index)
                                updateBlocks(newBlocks)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove",
                                tint = SecondaryColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                is ImageBlock -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        val parsedUri = Uri.parse(block.uri)
                        var isMissing = false
                        val imageModel: Any = if (parsedUri.scheme == "file" && parsedUri.path != null) {
                            val file = java.io.File(parsedUri.path!!)
                            if (!file.exists()) isMissing = true
                            file
                        } else if (block.uri.startsWith("drive://")) {
                            isMissing = true
                            block.uri
                        } else {
                            parsedUri
                        }
                        
                        if (isMissing) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.1f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorColor.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Image, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Image Not Available", color = ErrorColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        val message = if (block.uri.startsWith("drive://")) "Downloading from Google Drive..." else "The local file has been deleted or is unavailable."
                                        Text(message, color = ErrorColor, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        } else {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "Attached Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                val newBlocks = blocks.toMutableList()
                                newBlocks.removeAt(index)
                                updateBlocks(newBlocks)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove Image", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                is AudioBlock -> {
                    val parsedUri = Uri.parse(block.uri)
                    var isMissing = false
                    if (parsedUri.scheme == "file" && parsedUri.path != null) {
                        if (!java.io.File(parsedUri.path!!).exists()) isMissing = true
                    } else if (block.uri.startsWith("drive://")) {
                        isMissing = true
                    }
                    
                    val cardColor = if (isMissing) ErrorColor.copy(alpha = 0.1f) else iconColor.copy(alpha = 0.1f)
                    val iconTint = if (isMissing) ErrorColor else iconColor
                    val textCol = if (isMissing) ErrorColor else textColor
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = if (isMissing) androidx.compose.foundation.BorderStroke(1.dp, ErrorColor.copy(alpha = 0.3f)) else null
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp).fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = iconTint,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = BackgroundColor)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isMissing) "Audio Not Available" else "Audio Recording",
                                    color = textCol,
                                    fontSize = 16.sp,
                                    fontWeight = if (isMissing) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                                if (isMissing) {
                                    val message = if (block.uri.startsWith("drive://")) "Downloading..." else "Deleted"
                                    Text(message, color = ErrorColor, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(
                                onClick = {
                                    val newBlocks = blocks.toMutableList()
                                    newBlocks.removeAt(index)
                                    updateBlocks(newBlocks)
                                }
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove Audio", tint = textCol.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
}
