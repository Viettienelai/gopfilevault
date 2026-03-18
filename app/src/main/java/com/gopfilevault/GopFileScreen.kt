package com.gopfilevault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GopFileScreen(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("HimmelOS_Prefs", Context.MODE_PRIVATE) }

    val clipboardManager = LocalClipboardManager.current

    var apiKey by remember { mutableStateOf(sharedPref.getString("API_KEY", "") ?: "") }
    var isApiKeyLocked by remember { mutableStateOf(apiKey.isNotBlank()) }

    var includeProperties by remember { mutableStateOf(sharedPref.getBoolean("INCLUDE_PROPS", false)) }
    var includeFilePath by remember { mutableStateOf(sharedPref.getBoolean("INCLUDE_PATH", false)) }

    var sourceFolderUris by remember {
        val savedUris = sharedPref.getStringSet("SOURCE_URIS", emptySet()) ?: emptySet()
        val sortedUris = savedUris.map { Uri.parse(it) }.sortedBy {
            DocumentFile.fromTreeUri(context, it)?.name?.lowercase() ?: ""
        }
        mutableStateOf(sortedUris)
    }

    var destinationFileUri by remember {
        val savedUri = sharedPref.getString("DEST_URI", null)
        mutableStateOf(savedUri?.let { Uri.parse(it) })
    }

    var statusMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    var showAdvancedOptions by remember { mutableStateOf(false) }
    val advancedRotationAngle by animateFloatAsState(
        targetValue = if (showAdvancedOptions) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "Advanced Arrow Rotation"
    )

    val defaultButtonColors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD2E4FF), contentColor = Color(0xFF004880))

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !sourceFolderUris.contains(uri)) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val updatedUris = (sourceFolderUris + uri).sortedBy {
                    DocumentFile.fromTreeUri(context, it)?.name?.lowercase() ?: ""
                }
                sourceFolderUris = updatedUris
                sharedPref.edit().putStringSet("SOURCE_URIS", updatedUris.map { it.toString() }.toSet()).apply()
            } catch (e: Exception) { statusMessage = "Permission error: ${e.message}" }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                destinationFileUri = uri
                sharedPref.edit().putString("DEST_URI", uri.toString()).apply()
            } catch (e: Exception) { statusMessage = "Permission error: ${e.message}" }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {

        // --- SOURCE FOLDERS MENU (HAMBURGER STYLE) ---
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val headerShape = if (sourceFolderUris.isEmpty()) {
                RoundedCornerShape(16.dp)
            } else {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE9EEFA), headerShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source folders (${sourceFolderUris.size})",
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF161C24),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Folder", tint = Color(0xFF161C24))
                }
            }

            sourceFolderUris.forEachIndexed { index, uri ->
                val itemShape = if (index == sourceFolderUris.lastIndex) {
                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                } else {
                    RoundedCornerShape(4.dp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE9EEFA), itemShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF4285F4))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = DocumentFile.fromTreeUri(context, uri)?.name ?: "Unknown",
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF404753)
                    )
                    IconButton(
                        onClick = {
                            sourceFolderUris = sourceFolderUris - uri
                            sharedPref.edit().putStringSet("SOURCE_URIS", sourceFolderUris.map { it.toString() }.toSet()).apply()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Folder", tint = Color(0xFFEA4335))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- TARGET FILE DISPLAY ---
        if (destinationFileUri == null) {
            Button(
                onClick = { filePickerLauncher.launch(arrayOf("text/plain")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = defaultButtonColors,
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Select Target .txt", fontWeight = FontWeight.Medium)
            }
        } else {
            val fileName = DocumentFile.fromSingleUri(context, destinationFileUri!!)?.name ?: "Unknown"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                        .background(Color(0xFFD2E4FF))
                        .clickable { filePickerLauncher.launch(arrayOf("text/plain")) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f).offset(y = (-3).dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Target File", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF004880), modifier = Modifier.offset(y = 2.dp))
                        Text(fileName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF004880), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(y = (-1).dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 26.dp, bottomEnd = 26.dp))
                        .background(Color(0xFFE2DFFF))
                        .clickable {
                            val promptText = "dựa vào thông tin sau để suy ngẫm, liên kết, và trả lời câu hỏi sau:\n"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, destinationFileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share to Gemini"))

                            coroutineScope.launch {
                                delay(1000)
                                clipboardManager.setText(buildAnnotatedString { append(promptText) })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF424271), modifier = Modifier.size(20.dp).offset(x = (-2).dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- ADVANCED OPTIONS (EXPANDABLE CARD M3) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFE9EEFA))
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMedium))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedOptions = !showAdvancedOptions }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Advanced Options",
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0060A7),
                    fontSize = 15.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Advanced Options",
                    tint = Color(0xFF0060A7),
                    modifier = Modifier.rotate(advancedRotationAngle)
                )
            }

            if (showAdvancedOptions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; sharedPref.edit().putString("API_KEY", it).apply() },
                        label = { Text("Gemini API Key") },
                        singleLine = true,
                        enabled = !isApiKeyLocked,
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyLocked = !isApiKeyLocked }) {
                                Icon(if (isApiKeyLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Toggle Lock")
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Keep YAML Properties", color = Color(0xFF404753), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Switch(
                            checked = includeProperties,
                            onCheckedChange = { includeProperties = it; sharedPref.edit().putBoolean("INCLUDE_PROPS", it).apply() },
                            modifier = Modifier.scale(0.85f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0060A7),
                                uncheckedThumbColor = Color(0xFF707884),
                                uncheckedTrackColor = Color(0xFFE9EEFA),
                                uncheckedBorderColor = Color(0xFF707884)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Include File Path", color = Color(0xFF404753), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Switch(
                            checked = includeFilePath,
                            onCheckedChange = { includeFilePath = it; sharedPref.edit().putBoolean("INCLUDE_PATH", it).apply() },
                            modifier = Modifier.scale(0.85f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0060A7),
                                uncheckedThumbColor = Color(0xFF707884),
                                uncheckedTrackColor = Color(0xFFE9EEFA),
                                uncheckedBorderColor = Color(0xFF707884)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- NÚT MERGE FILES CÓ ANIMATION MÀU SẮC ---
        val isMergeEnabled = !isProcessing && sourceFolderUris.isNotEmpty() && destinationFileUri != null
        val targetBtnColor = if (isMergeEnabled) Color(0xFF0060A7) else Color(0xFFC2D2E1)
        val animatedBtnColor by animateColorAsState(targetValue = targetBtnColor, animationSpec = tween(300), label = "btnColor")

        Button(
            onClick = {
                if (sourceFolderUris.isNotEmpty() && destinationFileUri != null) {
                    isProcessing = true
                    statusMessage = "SCANNING"
                    coroutineScope.launch {
                        val result = processMultipleFolders(context, sourceFolderUris, destinationFileUri!!, includeProperties, includeFilePath, apiKey) { msg -> statusMessage = msg }
                        statusMessage = result
                        isProcessing = false
                    }
                }
            },
            enabled = isMergeEnabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = animatedBtnColor,
                contentColor = Color.White,
                disabledContainerColor = animatedBtnColor, // Override disabled color để ăn theo animation
                disabledContentColor = Color.White
            )
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (isProcessing) "PROCESSING..." else "MERGE FILES",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- LOG/CONSOLE BOX (CỐ ĐỊNH, KHÔNG CÒN ANIMATION VÀ DÙNG IF ĐỂ RENDER NGAY LẬP TỨC) ---
        if (statusMessage.isNotBlank()) {
            val isScanning = statusMessage == "SCANNING"
            val isCounting = statusMessage == "COUNTING"
            val isProcessingStep = isScanning || isCounting

            val isSuccess = statusMessage.contains("SUCCESS", ignoreCase = true)
            val isError = statusMessage.contains("error", ignoreCase = true)

            val targetBgColor = when {
                isProcessingStep -> Color(0xFFF8F9FA)
                isSuccess -> Color(0xFFF2FBF5)
                isError -> Color(0xFFFDF3F2)
                else -> Color(0xFFF8F9FA)
            }
            val targetBorderColor = when {
                isProcessingStep -> Color(0xFFDADCE0)
                isSuccess -> Color(0xFF81C995).copy(alpha = 0.5f)
                isError -> Color(0xFFF28B82).copy(alpha = 0.5f)
                else -> Color(0xFFDADCE0)
            }
            val targetIconTint = when {
                isProcessingStep -> Color(0xFF5F6368)
                isSuccess -> Color(0xFF1E8E3E)
                isError -> Color(0xFFD93025)
                else -> Color(0xFF5F6368)
            }

            val bgColor by animateColorAsState(targetValue = targetBgColor, label = "bg")
            val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "border")
            val iconTint by animateColorAsState(targetValue = targetIconTint, label = "icon")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = bgColor),
                border = BorderStroke(1.dp, borderColor)
            ) {
                if (isProcessingStep) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Bước 1: Scan & merge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0060A7), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8E3E), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Scan & merge", color = Color(0xFF3C4043), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        // Bước 2: Counting token using API
                        if (isCounting) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0060A7), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Counting token using API", color = Color(0xFF3C4043), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                // UI KHI THÀNH CÔNG / LỖI
                else {
                    val iconStatus = if (isSuccess) Icons.Default.CheckCircle else if (isError) Icons.Default.Error else Icons.Default.Info
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = iconStatus,
                            contentDescription = "Status",
                            tint = iconTint,
                            modifier = Modifier.size(22.dp).offset(y = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = buildAnnotatedString {
                                val parts = statusMessage.split("*")
                                parts.forEachIndexed { index, part ->
                                    if (index % 2 == 1) {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color(0xFF0D47A1))) {
                                            append(part)
                                        }
                                    } else {
                                        withStyle(SpanStyle(color = Color(0xFF3C4043))) {
                                            append(part)
                                        }
                                    }
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

data class MergeResult(val fileCount: Int, val estimatedTokens: Int)

suspend fun processMultipleFolders(context: Context, sourceUris: List<Uri>, destUri: Uri, includeProps: Boolean, includePath: Boolean, apiKey: String, onProgressUpdate: (String) -> Unit): String {
    return withContext(Dispatchers.IO) {
        try {
            onProgressUpdate("SCANNING")

            val resolver = context.contentResolver
            var totalFileCount = 0
            var totalEstimatedTokens = 0

            resolver.openOutputStream(destUri, "wt")?.bufferedWriter()?.use { writer ->
                for (uri in sourceUris) {
                    val rootDir = DocumentFile.fromTreeUri(context, uri)
                    if (rootDir != null) {
                        val rootFolderName = rootDir.name ?: "Vault"
                        val result = traverseAndMerge(rootDir, resolver, writer, includeProps, includePath, rootFolderName)
                        totalFileCount += result.fileCount
                        totalEstimatedTokens += result.estimatedTokens
                    }
                }
            }

            val mergedText = resolver.openInputStream(destUri)?.bufferedReader()?.use { it.readText() } ?: ""

            val historyDir = File(context.filesDir, "merged_history")
            if (!historyDir.exists()) historyDir.mkdirs()

            val timestamp = System.currentTimeMillis()
            val generatedName = sourceUris.mapNotNull { uri ->
                DocumentFile.fromTreeUri(context, uri)?.name?.take(2)
            }.joinToString("+") + ".txt"

            val internalFile = File(historyDir, "${generatedName}_$timestamp.txt")
            internalFile.writeText(mergedText)
            val internalUri = Uri.fromFile(internalFile).toString()

            val removedUris = ChatManager.saveMergedHistory(context, internalUri, generatedName, timestamp)
            removedUris.forEach { uriString ->
                try {
                    val file = File(Uri.parse(uriString).path!!)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {}
            }

            if (apiKey.isNotBlank()) {
                onProgressUpdate("COUNTING")
                try {
                    val generativeModel = com.google.ai.client.generativeai.GenerativeModel(modelName = "gemini-3.1-flash-lite-preview", apiKey = apiKey)
                    val exactTokens = generativeModel.countTokens(mergedText).totalTokens
                    return@withContext "SUCCESS!\nMerged *$totalFileCount* files.\nExact Tokens: *${String.format("%,d", exactTokens)}*"
                } catch (e: Exception) { return@withContext "MERGED SUCCESSFULLY (*$totalFileCount* files).\nAPI count error: ${e.localizedMessage}" }
            } else {
                return@withContext "SUCCESS!\nMerged *$totalFileCount* files.\nEst. Tokens: *~${String.format("%,d", totalEstimatedTokens)}*"
            }
        } catch (e: Exception) { return@withContext "System error: ${e.localizedMessage}" }
    }
}

fun traverseAndMerge(dir: DocumentFile, resolver: android.content.ContentResolver, writer: java.io.BufferedWriter, includeProps: Boolean, includePath: Boolean, currentPath: String): MergeResult {
    var count = 0
    var fileTokens = 0
    fun estimateTokensForString(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.split(Regex("\\s+")).size * 1.3 + text.count { !it.isLetterOrDigit() && !it.isWhitespace() } * 0.8).toInt()
    }
    dir.listFiles().forEach { file ->
        if (file.isDirectory) {
            val folderName = file.name ?: ""
            val nextPath = if (currentPath.isEmpty()) folderName else "$currentPath/$folderName"
            val result = traverseAndMerge(file, resolver, writer, includeProps, includePath, nextPath)
            count += result.fileCount
            fileTokens += result.estimatedTokens
        } else {
            val fileName = file.name ?: ""
            if (fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true)) {
                try {
                    val displayPath = if (includePath) "$currentPath/$fileName" else fileName
                    writer.write("=== BEGIN DOCUMENT: $displayPath ===\n")
                    fileTokens += estimateTokensForString("=== BEGIN DOCUMENT: $displayPath ===\n")
                    resolver.openInputStream(file.uri)?.bufferedReader()?.use { reader ->
                        var content = reader.readText()
                        if (!includeProps && content.startsWith("---")) {
                            val secondDashIndex = content.indexOf("\n---", 3)
                            if (secondDashIndex != -1) {
                                val endOfPropsLine = content.indexOf('\n', secondDashIndex + 1)
                                content = if (endOfPropsLine != -1) content.substring(endOfPropsLine + 1).trimStart() else ""
                            }
                        }
                        writer.write(content)
                        fileTokens += estimateTokensForString(content)
                    }
                    writer.write("\n=== END DOCUMENT ===\n\n")
                    fileTokens += estimateTokensForString("\n=== END DOCUMENT ===\n\n")
                    count++
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
    return MergeResult(count, fileTokens)
}