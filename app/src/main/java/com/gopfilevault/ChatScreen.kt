package com.gopfilevault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable // <--- Import quan trọng để lưu trạng thái qua các Tab
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    context: Context,
    inputText: String,
    onInputChanged: (String) -> Unit,
    triggerSend: Long,
    triggerAttach: Long,
    onOverlayChange: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val sharedPref = remember { context.getSharedPreferences("HimmelOS_Prefs", Context.MODE_PRIVATE) }
    val apiKey = sharedPref.getString("API_KEY", "") ?: ""
    val destUriString = sharedPref.getString("DEST_URI", null)

    var allSessions by remember { mutableStateOf(ChatManager.loadSessions(context)) }
    var currentSessionId by remember { mutableStateOf(allSessions.first().id) }
    val currentSession = allSessions.find { it.id == currentSessionId } ?: allSessions.first()

    var isThinking by remember { mutableStateOf(false) }

    var pendingAttachments by remember { mutableStateOf(listOf<Attachment>()) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showTopMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newTitleName by remember { mutableStateOf("") }

    var currentTokenCount by remember { mutableStateOf(0) }
    var isCountingTokens by remember { mutableStateOf(false) }

    // THÊM 2 BIẾN NÀY ĐỂ GHI NHỚ LẦN BẤM CUỐI CÙNG (Sống sót qua quá trình đổi Tab)
    var lastHandledAttach by rememberSaveable { mutableStateOf(0L) }
    var lastHandledSend by rememberSaveable { mutableStateOf(0L) }

    val isDrawerOpening = drawerState.targetValue == DrawerValue.Open
    LaunchedEffect(isDrawerOpening, showAttachmentSheet, showRenameDialog) {
        onOverlayChange(isDrawerOpening || showAttachmentSheet || showRenameDialog)
        if (isDrawerOpening) {
            keyboardController?.hide()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val doc = DocumentFile.fromSingleUri(context, it)
                val name = doc?.name ?: "Unknown File"
                val type = context.contentResolver.getType(it) ?: ""
                val isImg = type.startsWith("image/")
                pendingAttachments = pendingAttachments + Attachment(it.toString(), name, isImg)
                coroutineScope.launch { showAttachmentSheet = false }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(currentSession.messages.size) {
        if (currentSession.messages.isNotEmpty() && !isThinking) {
            val targetIndex = if (currentSession.messages.size >= 2) currentSession.messages.size - 2 else currentSession.messages.lastIndex
            listState.scrollToItem(targetIndex)
        }
    }

    // FIX LỖI TỰ MỞ SHEET KHI CHUYỂN TAB
    LaunchedEffect(triggerAttach) {
        if (triggerAttach > 0L && triggerAttach != lastHandledAttach) {
            lastHandledAttach = triggerAttach // Cập nhật lại mốc thời gian đã xử lý
            keyboardController?.hide()
            showAttachmentSheet = true
        }
    }

    // FIX LỖI (DỰ PHÒNG) CHO NÚT SEND KHI CHUYỂN TAB
    LaunchedEffect(triggerSend) {
        if (triggerSend > 0L && triggerSend != lastHandledSend && inputText.isNotBlank() && !isThinking) {
            lastHandledSend = triggerSend // Cập nhật lại mốc thời gian đã xử lý
            keyboardController?.hide()

            val question = inputText
            val usedTokens = currentTokenCount
            onInputChanged("")

            val attachmentsToSend = if (pendingAttachments.isNotEmpty()) pendingAttachments.toList()
            else currentSession.messages.firstOrNull { it.attachments.isNotEmpty() }?.attachments ?: emptyList()
            pendingAttachments = emptyList()

            var newTitle = currentSession.title
            if (currentSession.messages.isEmpty()) newTitle = if (question.length > 25) question.take(25) + "..." else question
            val newHistory = currentSession.messages + ChatMessage(question, isUser = true, attachments = attachmentsToSend, tokenCount = usedTokens)

            val updatedSessionsBeforeAPI = allSessions.map { if (it.id == currentSessionId) it.copy(title = newTitle, messages = newHistory) else it }
            allSessions = updatedSessionsBeforeAPI
            ChatManager.saveSessions(context, updatedSessionsBeforeAPI)
            isThinking = true

            coroutineScope.launch { delay(350); listState.animateScrollToItem(newHistory.size) }

            coroutineScope.launch {
                val contextWindow = newHistory.takeLast(5).joinToString("\n") { if (it.isUser) "User: ${it.text}" else "Himmel: ${it.text}" }
                val finalPromptToGemini = """
                    [NGỮ CẢNH - LỊCH SỬ TRÒ CHUYỆN ĐỂ THAM KHẢO]
                    $contextWindow
                    
                    [YÊU CẦU MỚI TỪ NGƯỜI DÙNG - HÃY DỰA VÀO DỮ LIỆU VAULT ĐỂ TRẢ LỜI]
                    Câu hỏi: $question
                """.trimIndent()

                val answer = GeminiService.askVault(context, Uri.parse(destUriString!!), apiKey, finalPromptToGemini, attachmentsToSend)

                val finalHistory = newHistory + ChatMessage(answer, isUser = false)
                val finalSessions = allSessions.map { if (it.id == currentSessionId) it.copy(messages = finalHistory) else it }
                allSessions = finalSessions
                ChatManager.saveSessions(context, finalSessions)
                isThinking = false

                coroutineScope.launch { delay(300); if (finalHistory.size >= 2) listState.animateScrollToItem(finalHistory.size - 2, 0) }
            }
        }
    }

    LaunchedEffect(inputText, pendingAttachments) {
        if (apiKey.isNotBlank() && destUriString != null) {
            isCountingTokens = true
            delay(800)
            val attachmentsToCount = if (pendingAttachments.isNotEmpty()) pendingAttachments.toList() else currentSession.messages.firstOrNull { it.attachments.isNotEmpty() }?.attachments ?: emptyList()
            val mockQuestion = if (inputText.isNotBlank()) inputText else "..."
            val mockHistory = currentSession.messages + ChatMessage(mockQuestion, isUser = true, attachments = attachmentsToCount)
            val contextWindow = mockHistory.takeLast(5).joinToString("\n") { if (it.isUser) "User: ${it.text}" else "Himmel: ${it.text}" }
            val promptToCount = "[NGỮ CẢNH]\n$contextWindow\n[YÊU CẦU]\nCâu hỏi: $mockQuestion"
            val count = GeminiService.countRequestTokens(context, Uri.parse(destUriString), apiKey, promptToCount, attachmentsToCount)
            currentTokenCount = count
            isCountingTokens = false
        }
    }

    if (apiKey.isBlank() || destUriString == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
            Text("Please setup API Key & Target File in Data tab!", color = Color(0xFFDB4437), modifier = Modifier.padding(32.dp))
        }
        return
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename chat", fontWeight = FontWeight.Medium, color = Color(0xFF0060A7)) },
            text = { OutlinedTextField(value = newTitleName, onValueChange = { newTitleName = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            containerColor = Color(0xFFF8F9FF),
            confirmButton = { TextButton(onClick = {
                val updatedSessions = allSessions.map { if (it.id == currentSessionId) it.copy(title = newTitleName) else it }
                allSessions = updatedSessions
                ChatManager.saveSessions(context, updatedSessions)
                showRenameDialog = false
            }) { Text("Save", color = Color(0xFF0060A7), fontWeight = FontWeight.Medium) } },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = Color(0xFF404753)) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        ModalNavigationDrawer(
            drawerState = drawerState, gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(drawerShape = RectangleShape, drawerContainerColor = Color(0xFFF8F9FF), modifier = Modifier.width(260.dp)) {
                    Icon(Icons.Default.History, "History", tint = Color(0xFF1B1C1D), modifier = Modifier.padding(16.dp).size(28.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(allSessions) { _, session ->
                            NavigationDrawerItem(
                                label = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 15.sp) },
                                selected = session.id == currentSessionId,
                                onClick = { currentSessionId = session.id; coroutineScope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(40.dp),
                                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFD3E3FD), unselectedContainerColor = Color.Transparent, selectedTextColor = Color(0xFF0842A0), unselectedTextColor = Color(0xFF444746))
                            )
                        }
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Text(currentSession.title, color = Color(0xFF1B1C1D), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu", tint = Color(0xFF404753)) } },
                        actions = {
                            IconButton(onClick = {
                                val newSession = ChatSession()
                                val updatedSessions = listOf(newSession) + allSessions
                                allSessions = updatedSessions
                                currentSessionId = newSession.id
                                ChatManager.saveSessions(context, updatedSessions)
                            }) { Icon(Icons.Default.AddComment, "New Chat", tint = Color(0xFF404753)) }
                            IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color(0xFF404753)) }
                            DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }, containerColor = Color(0xFFE9EEFA)) {
                                DropdownMenuItem(text = { Text("Rename", color = Color(0xFF404753)) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFF404753)) }, onClick = { newTitleName = currentSession.title; showRenameDialog = true; showTopMenu = false })
                                DropdownMenuItem(text = { Text("Delete chat", color = Color(0xFF404753)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFF404753)) }, onClick = {
                                    showTopMenu = false
                                    val updatedSessions = allSessions.filter { it.id != currentSessionId }.toMutableList()
                                    if (updatedSessions.isEmpty()) updatedSessions.add(ChatSession())
                                    currentSessionId = updatedSessions.first().id
                                    allSessions = updatedSessions
                                    ChatManager.saveSessions(context, updatedSessions)
                                })
                            }
                        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        itemsIndexed(items = currentSession.messages) { index, message ->
                            ChatBubble(message = message, onDelete = {
                                val newMsgs = currentSession.messages.toMutableList()
                                if (index < newMsgs.size) {
                                    newMsgs.removeAt(index)
                                    if (index < newMsgs.size && !newMsgs[index].isUser) newMsgs.removeAt(index)
                                    val updatedSessions = allSessions.map { if (it.id == currentSessionId) it.copy(messages = newMsgs) else it }
                                    allSessions = updatedSessions
                                    ChatManager.saveSessions(context, updatedSessions)
                                }
                            })
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        if (isThinking) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                                    val transition = rememberInfiniteTransition(label = "")
                                    val rot by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "")
                                    Box(modifier = Modifier.padding(bottom = 8.dp).size(26.dp), contentAlignment = Alignment.Center) {
                                        Icon(painterResource(R.drawable.ic_gemini_spark), null, tint = Color.Unspecified, modifier = Modifier.size(24.dp).rotate(rot))
                                    }
                                    Text("Himmel is thinking...", color = Color(0xFF0060A7), fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 120.dp)
                    ) {
                        if (pendingAttachments.isNotEmpty()) {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(pendingAttachments) { att ->
                                    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFD2E4FF)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (att.isImage) Icons.Default.Image else Icons.Default.InsertDriveFile, null, tint = Color(0xFF4285F4), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(att.name, fontSize = 12.sp, color = Color(0xFF0060A7), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 100.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Close, "Remove", tint = Color(0xFF404753), modifier = Modifier.size(16.dp).clickable { pendingAttachments = pendingAttachments.filter { it != att } })
                                    }
                                }
                            }
                        }
                        Text(text = if (isCountingTokens) "Calculating tokens..." else if (currentTokenCount > 0) "Tokens req: ~${String.format("%,d", currentTokenCount)}" else "", fontSize = 11.sp, color = Color(0xFF404753), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = showAttachmentSheet, enter = fadeIn(tween(300)), exit = fadeOut(tween(300)), modifier = Modifier.fillMaxSize()) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { showAttachmentSheet = false }) }
        AnimatedVisibility(visible = showAttachmentSheet, enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)), exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFFF8F9FF)).clickable(enabled = false) {}.padding(bottom = 32.dp, top = 8.dp)) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(Color(0xFF404753), CircleShape))
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { filePickerLauncher.launch(arrayOf("*/*")) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFE9EEFA), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF4285F4)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Select from device", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF404753))
                }
                HorizontalDivider(color = Color(0xFF404753).copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                val historyFiles = ChatManager.getMergedHistory(context)
                if (historyFiles.isNotEmpty()) {
                    historyFiles.forEach { file ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { pendingAttachments = pendingAttachments + file; coroutineScope.launch { showAttachmentSheet = false } }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, tint = Color(0xFF4285F4), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(file.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF404753), modifier = Modifier.weight(1f))
                            Text(if (file.time > 0L) SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(file.time)) else "", fontSize = 12.sp, color = Color(0xFF404753))
                        }
                    }
                }
            }
        }
    }
    if (showAttachmentSheet) BackHandler { showAttachmentSheet = false }
}

@Composable
fun ChatBubble(message: ChatMessage, onDelete: () -> Unit = {}) {
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
        if (message.isUser) {
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier.background(Color(0xFFD2E4FF), RoundedCornerShape(topStart = 24.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)).pointerInput(Unit) { detectTapGestures(onLongPress = { showMenu = true }) }.widthIn(max = 300.dp).animateContentSize()
                ) {
                    Text(message.text, color = Color(0xFF1B1C1D), fontSize = 15.sp, maxLines = if (isExpanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, onTextLayout = { if (it.hasVisualOverflow) isOverflowing = true }, modifier = Modifier.padding(start = 18.dp, end = if (isOverflowing) 44.dp else 16.dp, top = 12.dp, bottom = 12.dp))
                    if (isOverflowing) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).clip(CircleShape).clickable { isExpanded = !isExpanded }.size(30.dp), contentAlignment = Alignment.Center) {
                            Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Expand", tint = Color(0xFF0060A7), modifier = Modifier.size(20.dp))
                        }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = Color(0xFFF8F9FF)) {
                        DropdownMenuItem(text = { Text("Copy", fontSize = 14.sp, color = Color(0xFF404753)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF404753), modifier = Modifier.size(20.dp)) }, onClick = { clipboardManager.setText(buildAnnotatedString { append(message.text) }); showMenu = false })
                        DropdownMenuItem(text = { Text("Delete", color = Color(0xFF404753), fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFF404753), modifier = Modifier.size(20.dp)) }, onClick = { onDelete(); showMenu = false })
                    }
                }
                if (message.tokenCount > 0) Text("~${String.format("%,d", message.tokenCount)} tokens", fontSize = 11.sp, color = Color(0xFF404753), modifier = Modifier.padding(end = 12.dp, top = 4.dp))
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 0.dp)) {
                Box(modifier = Modifier.padding(bottom = 4.dp).size(26.dp), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_gemini_spark), null, tint = Color.Unspecified, modifier = Modifier.size(24.dp)) }
                MarkdownText(markdown = message.text, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 24.sp, color = Color(0xFF1B1C1D)))
                IconButton(onClick = { clipboardManager.setText(buildAnnotatedString { append(message.text) }) }, modifier = Modifier.padding(top = 8.dp).size(30.dp)) { Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF404753), modifier = Modifier.size(16.dp)) }
            }
        }
    }
}