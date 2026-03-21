package com.gopfilevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch

// --- FONT ---
val GoogleSansTextFamily = FontFamily(
    Font(R.font.google_sans_text_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.google_sans_text_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.google_sans_text_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.google_sans_text_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.google_sans_text_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.google_sans_text_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

val defaultTypography = Typography()
val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = GoogleSansTextFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = GoogleSansTextFamily),
)

// --- MÀU SẮC ---
val PillBgColor = Color(0xFFD2E4FF)
val PillIconColor = Color(0xFF1D4875)
val TiBgColor = Color(0xFFFFFFFF)
val ActiveIconColor = Color(0xFF191C20)
val AppBgColor = Color(0xFFF8F9FF)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = true
        }

        setContent {
            MaterialTheme(typography = AppTypography) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBgColor
                ) {
                    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
                    val coroutineScope = rememberCoroutineScope()

                    var inputText by remember { mutableStateOf("") }
                    var triggerSend by remember { mutableStateOf(0L) }
                    var triggerAttach by remember { mutableStateOf(0L) }

                    var isOverlayVisible by remember { mutableStateOf(false) }

                    val fluidSpring = spring<androidx.compose.ui.unit.Dp>(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessLow
                    )

                    val pillOffsetY by animateDpAsState(
                        targetValue = if (isOverlayVisible) 104.dp else 0.dp,
                        animationSpec = fluidSpring,
                        label = "pillOffset"
                    )

                    val isImeVisible = WindowInsets.isImeVisible
                    val bottomOffset by animateDpAsState(
                        targetValue = if (isImeVisible) 16.dp else 40.dp,
                        animationSpec = fluidSpring,
                        label = "bottomOffset"
                    )

                    Box(modifier = Modifier.fillMaxSize()) {

                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            when (page) {
                                0 -> DummyDriveScreen()
                                1 -> GopFileScreen(this@MainActivity)
                                2 -> ChatScreen(
                                    context = this@MainActivity,
                                    inputText = inputText,
                                    onInputChanged = { inputText = it },
                                    triggerSend = triggerSend,
                                    triggerAttach = triggerAttach,
                                    onOverlayChange = { isVisible ->
                                        isOverlayVisible = isVisible
                                    }
                                )
                            }
                        }

                        FloatingPill(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = pillOffsetY)
                                .imePadding()
                                .padding(bottom = bottomOffset),
                            currentPage = pagerState.currentPage,
                            inputText = inputText,
                            onInputChanged = { inputText = it },
                            onIcon1Click = {
                                if (it == 3) triggerAttach = System.currentTimeMillis()
                                else coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            },
                            onIcon2Click = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            onIcon3Click = {
                                if (it == 3) triggerSend = System.currentTimeMillis()
                                else coroutineScope.launch { pagerState.animateScrollToPage(2) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DummyDriveScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CloudSync, contentDescription = null, tint = PillIconColor, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Google Drive Sync", color = ActiveIconColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Chức năng đang được phát triển...", color = PillIconColor, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun FloatingPill(
    modifier: Modifier = Modifier,
    currentPage: Int,
    inputText: String,
    onInputChanged: (String) -> Unit,
    onIcon1Click: (Int) -> Unit,
    onIcon2Click: () -> Unit,
    onIcon3Click: (Int) -> Unit
) {
    val state = if (currentPage != 2) 1 else if (inputText.isEmpty()) 2 else 3

    val stateTracker = remember { intArrayOf(state, state) }
    if (stateTracker[1] != state) {
        stateTracker[0] = stateTracker[1]
        stateTracker[1] = state
    }
    val prevState = stateTracker[0]

    val dynamicDamping = when {
        state == 1 && prevState == 3 -> 0.8f
        state == 1 && prevState == 2 -> 0.75f
        state == 3 && prevState == 1 -> 0.7f
        else -> 0.6f
    }

    val dpAnimSpec = spring<androidx.compose.ui.unit.Dp>(dampingRatio = dynamicDamping, stiffness = Spring.StiffnessLow)
    val floatAnimSpec = spring<Float>(dampingRatio = dynamicDamping, stiffness = Spring.StiffnessLow)
    val colorAnimSpec = spring<Color>(dampingRatio = dynamicDamping, stiffness = Spring.StiffnessLow)

    // Spec tween 200 cho plus và send icons
    val tween200Spec = tween<Float>(200)

    val pillSquashHeight = remember { Animatable(64f) }
    val pillExtraWidth = remember { Animatable(0f) }
    val tiSquashDelta = remember { Animatable(0f) }
    val tiStretchDelta = remember { Animatable(0f) }
    val sendScale = remember { Animatable(1f) }

    var isInitialStateChange by remember { mutableStateOf(true) }

    val textOffsetX by animateDpAsState(
        targetValue = if (state == 2) 60.dp else 0.dp,
        animationSpec = dpAnimSpec,
        label = "textOffsetX"
    )

    LaunchedEffect(state) {
        if (isInitialStateChange) {
            isInitialStateChange = false
        } else {
            if (state == 3 && prevState != 3) {
                launch {
                    sendScale.snapTo(1.5f)
                    // Đã thay spring thành tween 200
                    sendScale.animateTo(1f, tween(200))
                }
            }

            if ((prevState == 2 && state == 3) || (prevState == 3 && state == 2)) {
                launch {
                    pillSquashHeight.animateTo(60f, tween(100))
                    pillSquashHeight.animateTo(64f, spring(0.35f, Spring.StiffnessLow))
                }
                launch {
                    pillExtraWidth.animateTo(8f, tween(100))
                    pillExtraWidth.animateTo(0f, spring(0.35f, Spring.StiffnessLow))
                }
            }
        }
    }

    var isInitialPageSwitch by remember { mutableStateOf(true) }

    LaunchedEffect(currentPage) {
        if (isInitialPageSwitch) {
            isInitialPageSwitch = false
        } else {
            launch {
                pillSquashHeight.animateTo(60f, tween(100))
                pillSquashHeight.animateTo(64f, spring(0.35f, Spring.StiffnessLow))
            }

            if (currentPage != 2) {
                launch {
                    pillExtraWidth.animateTo(8f, tween(100))
                    pillExtraWidth.animateTo(0f, spring(0.35f, Spring.StiffnessLow))
                }
                launch {
                    tiSquashDelta.animateTo(-4f, tween(100))
                    tiSquashDelta.animateTo(0f, spring(0.35f, Spring.StiffnessLow))
                }
                launch {
                    tiStretchDelta.animateTo(10f, tween(100))
                    tiStretchDelta.animateTo(0f, spring(0.35f, Spring.StiffnessLow))
                }
            }
        }
    }

    var textLineCount by remember { mutableStateOf(1) }
    val extraHeightTarget = if (state > 1) {
        ((textLineCount.coerceIn(1, 8) - 1) * 20).dp
    } else {
        0.dp
    }

    val animatedExtraHeight by animateDpAsState(
        targetValue = extraHeightTarget,
        animationSpec = dpAnimSpec,
        label = "extraHeight"
    )

    val icon1Color by animateColorAsState(if (currentPage == 0) ActiveIconColor else PillIconColor, animationSpec = colorAnimSpec, label = "")
    val icon2Color by animateColorAsState(if (currentPage == 1) ActiveIconColor else PillIconColor, animationSpec = colorAnimSpec, label = "")
    val icon3Color by animateColorAsState(if (currentPage == 2) ActiveIconColor else PillIconColor, animationSpec = colorAnimSpec, label = "")

    val baseWidth by animateDpAsState(if (state == 1) 168.dp else 324.dp, dpAnimSpec, label = "")
    val pillWidth = baseWidth + pillExtraWidth.value.dp

    val targetTiWidth = when (state) {
        1 -> 40.dp
        2 -> 200.dp
        3 -> 308.dp
        else -> 40.dp
    }

    val targetRightEdge = when (state) {
        1 -> if (currentPage == 0) (-32).dp else if (currentPage == 1) 20.dp else 72.dp
        2 -> 154.dp
        3 -> 154.dp
        else -> 20.dp
    }

    val baseTiWidth by animateDpAsState(targetTiWidth, dpAnimSpec, label = "")
    val animatedRightEdge by animateDpAsState(targetRightEdge, dpAnimSpec, label = "")

    val currentTiWidth = baseTiWidth + tiStretchDelta.value.dp
    val calculatedCenter = animatedRightEdge - (currentTiWidth / 2)
    val finalTiCenterOffset = if (currentTiWidth > 308.dp) 0.dp else calculatedCenter

    val baseTiHeight by animateDpAsState(if (state == 1) 40.dp else 48.dp, dpAnimSpec, label = "")
    val tiHeight = baseTiHeight + tiSquashDelta.value.dp + animatedExtraHeight

    val bottomAlignYOffset = (tiHeight - 48.dp) / 2

    val icon1Offset by animateDpAsState(if (state == 1) (-52).dp else (-130).dp, dpAnimSpec, label = "")
    val icon2Offset by animateDpAsState(if (state == 1) 0.dp else (-78).dp, dpAnimSpec, label = "")
    val icon3Offset by animateDpAsState(if (state == 1) 52.dp else 130.dp, dpAnimSpec, label = "")

    val icon1Alpha by animateFloatAsState(if (state == 3) 0f else 1f, floatAnimSpec, label = "")
    val icon1Scale by animateFloatAsState(if (state == 3) 0.4f else 1f, floatAnimSpec, label = "")

    val icon2Alpha by animateFloatAsState(if (state == 3) 0f else 1f, floatAnimSpec, label = "")
    val icon2Scale by animateFloatAsState(if (state == 3) 0.4f else 1f, floatAnimSpec, label = "")

    val icon3Alpha by animateFloatAsState(if (state == 1) 1f else 0f, floatAnimSpec, label = "")
    val icon3Scale by animateFloatAsState(if (state == 1) 1f else 0.4f, floatAnimSpec, label = "")

    // Đã thay thế floatAnimSpec thành tween200Spec
    val plusAlpha by animateFloatAsState(if (state >= 2) 1f else 0f, tween200Spec, label = "")
    val plusScale by animateFloatAsState(if (state >= 2) 1f else 0.4f, tween200Spec, label = "")

    val sendAlpha by animateFloatAsState(if (state == 3) 1f else 0f, tween200Spec, label = "")
    val sendBtnScale by animateFloatAsState(if (state == 3) 1f else 0.4f, tween200Spec, label = "")

    val density = LocalDensity.current

    Box(
        modifier = modifier
            .offset(y = ((pillSquashHeight.value - 64f) / 2).dp)
            .dropShadow(shape = RoundedCornerShape(32.dp)) {
                alpha = 0.15f
                offset = Offset(0f, with(density) { 1.dp.toPx() })
                radius = with(density) { 3.dp.toPx() }
                spread = 0f
            }
            .dropShadow(shape = RoundedCornerShape(32.dp)) {
                alpha = 0.075f
                offset = Offset(0f, with(density) { 4.dp.toPx() })
                radius = with(density) { 8.dp.toPx() }
                spread = with(density) { 3.dp.toPx() }
            }
            .width(pillWidth)
            .height(pillSquashHeight.value.dp + animatedExtraHeight)
            .background(PillBgColor, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
    ) {

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = finalTiCenterOffset)
                    .width(currentTiWidth)
                    .height(tiHeight)
                    .background(TiBgColor, RoundedCornerShape(24.dp))
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = finalTiCenterOffset)
                    .width(currentTiWidth)
                    .height(tiHeight)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedVisibility(
                    visible = state > 1,
                    // Đã thay spring/tween(300) thành tween(200) cho Text Input
                    enter = fadeIn(tween(200)) + scaleIn(
                        initialScale = 0.7f,
                        animationSpec = tween(200)
                    ),
                    exit = fadeOut(tween(200)) + scaleOut(
                        targetScale = 0.7f,
                        animationSpec = tween(200)
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputChanged,
                            onTextLayout = { textLayoutResult ->
                                textLineCount = textLayoutResult.lineCount
                            },
                            modifier = Modifier
                                .offset(x = textOffsetX)
                                .requiredWidth(212.dp)
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .padding(top = 14.dp, bottom = 14.dp),

                            textStyle = TextStyle(
                                color = ActiveIconColor,
                                fontSize = 15.sp,
                                fontFamily = GoogleSansTextFamily,
                                fontWeight = FontWeight.Normal
                            ),
                            maxLines = 8,
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = "Ask Himmel...",
                                            color = PillIconColor.copy(alpha = 0.6f),
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Visible,
                                            fontWeight = FontWeight.Normal,
                                            modifier = Modifier.offset(y = (-1).dp)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = icon1Offset)
                    .size(40.dp)
                    .scale(icon1Scale)
                    .alpha(icon1Alpha)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = state != 3
                    ) { onIcon1Click(state) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CloudSync, null, tint = icon1Color, modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = icon2Offset)
                    .size(40.dp)
                    .scale(icon2Scale)
                    .alpha(icon2Alpha)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = state != 3
                    ) { onIcon2Click() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CallMerge, null, tint = icon2Color, modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = icon3Offset)
                    .size(40.dp)
                    .scale(icon3Scale)
                    .alpha(icon3Alpha)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = state == 1
                    ) { onIcon3Click(state) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = icon3Color, modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = finalTiCenterOffset - (currentTiWidth / 2) + 24.dp, y = bottomAlignYOffset)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = state >= 2
                    ) { onIcon1Click(3) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add, null,
                    tint = ActiveIconColor.copy(alpha = plusAlpha),
                    modifier = Modifier.size(20.dp).scale(plusScale)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = finalTiCenterOffset + (currentTiWidth / 2) - 24.dp, y = bottomAlignYOffset)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = state == 3
                    ) { onIcon3Click(3) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send, null,
                    tint = ActiveIconColor.copy(alpha = sendAlpha),
                    modifier = Modifier.size(20.dp).scale(sendBtnScale * sendScale.value)
                )
            }
        }
    }
}