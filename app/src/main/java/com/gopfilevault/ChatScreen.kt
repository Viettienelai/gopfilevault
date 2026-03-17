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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val BgWhite = Color(0xFFFFFFFF)
val ChatUserBg = Color(0xFFE9EEF6)
val BtnBg = Color(0xFFDAE1FF)
val BtnText = Color(0xFF003FA4)
val InlineCodeBgColor = Color(0xFFFFF1F2)
val InlineCodeTextColor = Color(0xFFE11D48)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("HimmelOS_Prefs", Context.MODE_PRIVATE) }
    val apiKey = sharedPref.getString("API_KEY", "") ?: ""
    val destUriString = sharedPref.getString("DEST_URI", null)

    var allSessions by remember { mutableStateOf(ChatManager.loadSessions(context)) }
    var currentSessionId by remember { mutableStateOf(allSessions.first().id) }
    val currentSession = allSessions.find { it.id == currentSessionId } ?: allSessions.first()

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    var pendingAttachments by remember { mutableStateOf(listOf<Attachment>()) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showTopMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newTitleName by remember { mutableStateOf("") }

    var currentTokenCount by remember { mutableStateOf(0) }
    var isCountingTokens by remember { mutableStateOf(false) }

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

    LaunchedEffect(inputText, pendingAttachments) {
        if (apiKey.isNotBlank() && destUriString != null) {
            isCountingTokens = true
            delay(800)

            val attachmentsToCount = if (pendingAttachments.isNotEmpty()) pendingAttachments.toList()
            else currentSession.messages.firstOrNull { it.attachments.isNotEmpty() }?.attachments ?: emptyList()

            val mockQuestion = if (inputText.isNotBlank()) inputText else "..."
            val mockHistory = currentSession.messages + ChatMessage(mockQuestion, isUser = true, attachments = attachmentsToCount)

            val contextWindow = mockHistory.takeLast(5).joinToString("\n") {
                if (it.isUser) "User: ${it.text}" else "Himmel: ${it.text}"
            }
            val promptToCount = """
                [NGỮ CẢNH - LỊCH SỬ TRÒ CHUYỆN ĐỂ THAM KHẢO]
                $contextWindow
                
                [YÊU CẦU MỚI TỪ NGƯỜI DÙNG - HÃY DỰA VÀO DỮ LIỆU VAULT ĐỂ TRẢ LỜI]
                Câu hỏi: $mockQuestion
            """.trimIndent()

            val count = GeminiService.countRequestTokens(context, Uri.parse(destUriString), apiKey, promptToCount, attachmentsToCount)
            currentTokenCount = count
            isCountingTokens = false
        }
    }

    if (apiKey.isBlank() || destUriString == null) {
        Box(modifier = Modifier.fillMaxSize().background(BgWhite), contentAlignment = Alignment.Center) {
            Text("Vui lòng thiết lập API Key và File Đích ở tab DATA trước!", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(32.dp))
        }
        return
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Đổi tên đoạn chat", fontWeight = FontWeight.Bold, color = BtnText) },
            text = {
                OutlinedTextField(
                    value = newTitleName,
                    onValueChange = { newTitleName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            containerColor = BgWhite,
            confirmButton = {
                TextButton(onClick = {
                    val updatedSessions = allSessions.map { if (it.id == currentSessionId) it.copy(title = newTitleName) else it }
                    allSessions = updatedSessions
                    ChatManager.saveSessions(context, updatedSessions)
                    showRenameDialog = false
                }) { Text("Lưu", color = BtnText, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Hủy", color = Color.Gray) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        ModalNavigationDrawer(
            drawerState = drawerState, gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(drawerShape = RectangleShape, drawerContainerColor = BgWhite, modifier = Modifier.width(300.dp)) {
                    Text("Lịch sử trò chuyện", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BtnText, modifier = Modifier.padding(16.dp))
                    HorizontalDivider(color = Color.LightGray)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(allSessions) { _, session ->
                            NavigationDrawerItem(
                                label = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                selected = session.id == currentSessionId,
                                onClick = { currentSessionId = session.id; coroutineScope.launch { drawerState.close() } },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = BtnBg, unselectedContainerColor = Color.Transparent, selectedTextColor = BtnText, unselectedTextColor = Color.Black)
                            )
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentSession.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu", tint = BtnText) } },
                        actions = {
                            IconButton(onClick = {
                                val newSession = ChatSession()
                                val updatedSessions = listOf(newSession) + allSessions
                                allSessions = updatedSessions
                                currentSessionId = newSession.id
                                ChatManager.saveSessions(context, updatedSessions)
                            }) { Icon(Icons.Default.AddComment, "New Chat", tint = BtnText) }

                            IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = BtnText) }

                            DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }, containerColor = BgWhite) {
                                DropdownMenuItem(
                                    text = { Text("Đổi tên") }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.Gray) },
                                    onClick = { newTitleName = currentSession.title; showRenameDialog = true; showTopMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Xóa đoạn chat", color = Color.Red) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                    onClick = {
                                        showTopMenu = false
                                        val updatedSessions = allSessions.filter { it.id != currentSessionId }.toMutableList()
                                        if (updatedSessions.isEmpty()) updatedSessions.add(ChatSession())
                                        currentSessionId = updatedSessions.first().id
                                        allSessions = updatedSessions
                                        ChatManager.saveSessions(context, updatedSessions)
                                    }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgWhite)
                    )
                }
            ) { paddingValues ->
                Column(modifier = Modifier
                    .fillMaxSize()
                    .background(BgWhite)
                    .padding(paddingValues)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            itemsIndexed(
                                items = currentSession.messages,
                                key = { index, message -> "${message.hashCode()}_$index" },
                                contentType = { _, message -> if (message.isUser) "USER" else "AI" }
                            ) { index, message ->
                                ChatBubble(
                                    message = message,
                                    onDelete = {
                                        val newMsgs = currentSession.messages.toMutableList()
                                        if (index < newMsgs.size) {
                                            newMsgs.removeAt(index)
                                            if (index < newMsgs.size && !newMsgs[index].isUser) newMsgs.removeAt(index)

                                            val updatedSessions = allSessions.map {
                                                if (it.id == currentSessionId) it.copy(messages = newMsgs) else it
                                            }
                                            allSessions = updatedSessions
                                            ChatManager.saveSessions(context, updatedSessions)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            if (isThinking) {
                                item(key = "thinking_indicator") {
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                                        val transition = rememberInfiniteTransition(label = "")
                                        val rot by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "")
                                        Box(modifier = Modifier.padding(bottom = 8.dp).size(26.dp), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.ic_gemini_spark), null, tint = Color.Unspecified, modifier = Modifier.size(24.dp).rotate(rot))
                                        }
                                        Text("Himmel đang trích xuất...", color = BtnText, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, BgWhite))))
                    }

                    Column(modifier = Modifier
                        .fillMaxWidth()
                        // ĐIỀU CHỈNH: Ép buộc sử dụng tween(300) cho cả khi co và giãn
                        .animateContentSize(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)
                    ) {

                        if (pendingAttachments.isNotEmpty()) {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(pendingAttachments) { att ->
                                    Row(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ChatUserBg).padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(if (att.isImage) Icons.Default.Image else Icons.Default.InsertDriveFile, null, tint = BtnText, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(att.name, fontSize = 12.sp, color = BtnText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 100.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Close, "Xóa", tint = Color.Gray, modifier = Modifier.size(16.dp).clickable { pendingAttachments = pendingAttachments.filter { it != att } })
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (isCountingTokens) "Đang tính token..." else if (currentTokenCount > 0) "Token yêu cầu: ~${String.format("%,d", currentTokenCount)}" else "",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )

                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                // ĐIỀU CHỈNH: Gắn animationSpec chuẩn cho ô Text
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)),
                                placeholder = { Text("Hỏi về kiến thức trong Vault...", fontSize = 15.sp) },
                                enabled = !isThinking,
                                maxLines = 5,
                                shape = RoundedCornerShape(30.dp),
                                leadingIcon = { Spacer(modifier = Modifier.size(48.dp)) },
                                trailingIcon = { Spacer(modifier = Modifier.size(48.dp)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = BgWhite, unfocusedContainerColor = BgWhite, disabledContainerColor = BgWhite,
                                    focusedBorderColor = BtnBg, unfocusedBorderColor = Color.LightGray, disabledBorderColor = Color.LightGray
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 6.dp, bottom = 6.dp)
                                    .size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = { showAttachmentSheet = true; focusManager.clearFocus() }) { Icon(Icons.Default.AddCircle, "Thêm file", tint = BtnBg, modifier = Modifier.size(32.dp)) }
                            }

                            val isSendEnabled = !isThinking && inputText.isNotBlank()
                            Box(
                                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp).size(38.dp).background(if (isSendEnabled) BtnBg else Color.Transparent, CircleShape)
                                    .clickable(
                                        enabled = isSendEnabled,
                                        onClick = {
                                            focusManager.clearFocus()
                                            val question = inputText
                                            val usedTokens = currentTokenCount
                                            inputText = ""

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
                                    ),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Send, "Gửi", tint = if (isSendEnabled) BtnText else Color.LightGray, modifier = Modifier.size(18.dp).offset(x = 1.dp)) }
                        }
                    }
                }
            }
        }

        // CUSTOM BOTTOM SHEET
        AnimatedVisibility(
            visible = showAttachmentSheet,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showAttachmentSheet = false }
            )
        }

        AnimatedVisibility(
            visible = showAttachmentSheet,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(BgWhite)
                    .clickable(enabled = false) {}
                    .padding(bottom = 32.dp, top = 8.dp)
            ) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(Color.LightGray, CircleShape))
                Spacer(modifier = Modifier.height(16.dp))

                Text("Đính kèm file", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { filePickerLauncher.launch(arrayOf("*/*")) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).background(BtnBg, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.FolderOpen, null, tint = BtnText) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Nhập file từ điện thoại", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                HorizontalDivider(color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
                Text("Lịch sử 5 file gộp gần nhất", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                val historyFiles = ChatManager.getMergedHistory(context)
                if (historyFiles.isEmpty()) {
                    Text("Chưa có file lịch sử nào.", modifier = Modifier.padding(start = 16.dp), color = Color.Gray)
                } else {
                    historyFiles.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                pendingAttachments = pendingAttachments + file
                                coroutineScope.launch { showAttachmentSheet = false }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))

                            Text(file.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))

                            val timeStr = if (file.time > 0L) SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(file.time)) else ""
                            Text(timeStr, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        BackHandler { showAttachmentSheet = false }
    }
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
                    modifier = Modifier.background(ChatUserBg, RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp))
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { showMenu = true }) }
                        .widthIn(max = 300.dp).animateContentSize()
                ) {
                    Text(
                        text = message.text, color = Color.Black, fontSize = 15.sp, maxLines = if (isExpanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (it.hasVisualOverflow) isOverflowing = true },
                        modifier = Modifier.padding(start = 18.dp, end = if (isOverflowing) 44.dp else 16.dp, top = 12.dp, bottom = 12.dp)
                    )

                    if (isOverflowing) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).shadow(3.dp, CircleShape)
                                .background(Color.White, CircleShape).clickable { isExpanded = !isExpanded }.size(30.dp),
                            contentAlignment = Alignment.Center
                        ) { Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Mở rộng", tint = BtnText, modifier = Modifier.size(20.dp)) }
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = BgWhite) {
                        DropdownMenuItem(text = { Text("Copy", fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(20.dp)) }, onClick = { clipboardManager.setText(AnnotatedString(message.text)); showMenu = false })
                        DropdownMenuItem(text = { Text("Xóa", color = Color.Red, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }, onClick = { onDelete(); showMenu = false })
                    }
                }

                if (message.tokenCount > 0) {
                    Text(
                        text = "~${String.format("%,d", message.tokenCount)} tokens",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 12.dp, top = 4.dp)
                    )
                }
            }

        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Box(modifier = Modifier.padding(bottom = 8.dp).size(26.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_gemini_spark), null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
                }
                Markdown(
                    content = message.text, modifier = Modifier.fillMaxWidth(), padding = markdownPadding(block = 5.dp, list = 4.dp),
                    colors = markdownColor(inlineCodeBackground = InlineCodeBgColor, inlineCodeText = InlineCodeTextColor),
                    typography = markdownTypography(
                        text = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 24.sp),
                        paragraph = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 24.sp),
                        h1 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
                        h2 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
                        h3 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp)
                    )
                )
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(message.text)) }, modifier = Modifier.padding(top = 8.dp).size(30.dp)) {
                    Icon(Icons.Default.ContentCopy, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}