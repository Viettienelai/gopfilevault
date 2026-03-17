package com.gopfilevault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    var statusMessage by remember { mutableStateOf(if (sourceFolderUris.isNotEmpty() && destinationFileUri != null) "Sẵn sàng gộp file! (Cấu hình đã lưu)" else "Đang chờ lệnh...") }
    var isProcessing by remember { mutableStateOf(false) }

    val btnContainerColor = Color(0xFFDAE1FF)
    val btnContentColor = Color(0xFF003FA4)
    val defaultButtonColors = ButtonDefaults.buttonColors(containerColor = btnContainerColor, contentColor = btnContentColor)

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !sourceFolderUris.contains(uri)) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val updatedUris = (sourceFolderUris + uri).sortedBy {
                    DocumentFile.fromTreeUri(context, it)?.name?.lowercase() ?: ""
                }
                sourceFolderUris = updatedUris
                sharedPref.edit().putStringSet("SOURCE_URIS", updatedUris.map { it.toString() }.toSet()).apply()
                statusMessage = "Đã lưu thư mục mới."
            } catch (e: Exception) { statusMessage = "Lỗi cấp quyền: ${e.message}" }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                destinationFileUri = uri
                sharedPref.edit().putString("DEST_URI", uri.toString()).apply()
                statusMessage = "Đã khóa mục tiêu vào file đích."
            } catch (e: Exception) { statusMessage = "Lỗi cấp quyền lưu: ${e.message}" }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; sharedPref.edit().putString("API_KEY", it).apply() },
            label = { Text("Gemini API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.fillMaxWidth(), colors = defaultButtonColors) {
            Text("+ Thêm Thư Mục Vault")
        }

        if (sourceFolderUris.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
                Text("Thư mục nguồn (${sourceFolderUris.size}):", fontWeight = FontWeight.SemiBold)
                sourceFolderUris.forEach { uri ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "- ${DocumentFile.fromTreeUri(context, uri)?.name ?: "Unknown"}", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            sourceFolderUris = sourceFolderUris - uri
                            sharedPref.edit().putStringSet("SOURCE_URIS", sourceFolderUris.map { it.toString() }.toSet()).apply()
                        }) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { filePickerLauncher.launch(arrayOf("text/plain")) },
                modifier = Modifier.weight(1f).height(50.dp),
                colors = defaultButtonColors
            ) {
                val btnText = if (destinationFileUri == null) {
                    "Chọn File .txt Đích"
                } else {
                    val fileName = DocumentFile.fromSingleUri(context, destinationFileUri!!)?.name ?: "Unknown"
                    "Lưu vào: $fileName"
                }
                Text(btnText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (destinationFileUri != null) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val promptText = "dựa vào thông tin sau để suy ngẫm, liên kết, và trả lời câu hỏi sau:\n"

                        // 1. Chỉ truyền File Stream vào Intent (Đã xóa putExtra text)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, destinationFileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        // 2. Kích hoạt bảng Share ngay lập tức
                        context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ file lên Gemini"))

                        // 3. Đợi 1000ms cho bảng Share mở xong rồi mới gọi Clipboard để tránh kẹt UI
                        coroutineScope.launch {
                            delay(1000)
                            clipboardManager.setText(AnnotatedString(promptText))
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = btnContainerColor, contentColor = btnContentColor),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Chia sẻ")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().clickable { includeProperties = !includeProperties; sharedPref.edit().putBoolean("INCLUDE_PROPS", includeProperties).apply() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeProperties, onCheckedChange = { includeProperties = it; sharedPref.edit().putBoolean("INCLUDE_PROPS", it).apply() })
            Text("Giữ lại Properties (YAML)")
        }

        Row(modifier = Modifier.fillMaxWidth().clickable { includeFilePath = !includeFilePath; sharedPref.edit().putBoolean("INCLUDE_PATH", includeFilePath).apply() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeFilePath, onCheckedChange = { includeFilePath = it; sharedPref.edit().putBoolean("INCLUDE_PATH", it).apply() })
            Text("Hiển thị cấu trúc thư mục (Path)")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (sourceFolderUris.isNotEmpty() && destinationFileUri != null) {
                    isProcessing = true
                    coroutineScope.launch {
                        val result = processMultipleFolders(context, sourceFolderUris, destinationFileUri!!, includeProperties, includeFilePath, apiKey) { msg -> statusMessage = msg }
                        statusMessage = result
                        isProcessing = false
                    }
                }
            },
            enabled = !isProcessing && sourceFolderUris.isNotEmpty() && destinationFileUri != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = btnContainerColor, contentColor = btnContentColor, disabledContainerColor = Color.LightGray)
        ) { Text(if (isProcessing) "Đang xử lý..." else "BẮT ĐẦU GỘP FILE") }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = statusMessage, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
    }
}

data class MergeResult(val fileCount: Int, val estimatedTokens: Int)

suspend fun processMultipleFolders(context: Context, sourceUris: List<Uri>, destUri: Uri, includeProps: Boolean, includePath: Boolean, apiKey: String, onProgressUpdate: (String) -> Unit): String {
    return withContext(Dispatchers.IO) {
        try {
            onProgressUpdate("BƯỚC 1: Đang quét và gộp file...")
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
                onProgressUpdate("BƯỚC 2: Đang gọi Gemini API đếm token...")
                try {
                    val generativeModel = com.google.ai.client.generativeai.GenerativeModel(modelName = "gemini-3.1-flash-lite-preview", apiKey = apiKey)
                    val exactTokens = generativeModel.countTokens(mergedText).totalTokens
                    return@withContext "THÀNH CÔNG!\nĐã ghi đè vào file đích: $totalFileCount tài liệu.\nToken CHÍNH XÁC: ${String.format("%,d", exactTokens)} Tokens."
                } catch (e: Exception) { return@withContext "GỘP THÀNH CÔNG ($totalFileCount file).\nLỗi API đếm: ${e.localizedMessage}" }
            } else {
                return@withContext "THÀNH CÔNG!\nĐã ghi đè vào file đích: $totalFileCount tài liệu.\nToken ước lượng: ~${String.format("%,d", totalEstimatedTokens)} Tokens."
            }
        } catch (e: Exception) { "Lỗi hệ thống: ${e.localizedMessage}" }
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