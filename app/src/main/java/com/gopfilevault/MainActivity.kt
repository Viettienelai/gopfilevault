package com.gopfilevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch

// ---------------------------------------------------------
// BỘ FONT GOOGLE SANS TEXT STATIC (ĐÃ TÍCH HỢP IN NGHIÊNG)
// ---------------------------------------------------------
val GoogleSansTextFamily = FontFamily(
    // 1. Regular
    Font(R.font.google_sans_text_regular, FontWeight.Normal, FontStyle.Normal),
    // 2. Regular Italic
    Font(R.font.google_sans_text_italic, FontWeight.Normal, FontStyle.Italic),

    // 3. Medium
    Font(R.font.google_sans_text_medium, FontWeight.Medium, FontStyle.Normal),
    // 4. Medium Italic
    Font(R.font.google_sans_text_medium_italic, FontWeight.Medium, FontStyle.Italic),

    // 5. Bold
    Font(R.font.google_sans_text_bold, FontWeight.Bold, FontStyle.Normal),
    // 6. Bold Italic
    Font(R.font.google_sans_text_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

val defaultTypography = Typography()

// Áp dụng FontFamily này lên toàn hệ thống để dứt điểm vụ font
val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = GoogleSansTextFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = GoogleSansTextFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = GoogleSansTextFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = GoogleSansTextFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = GoogleSansTextFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = GoogleSansTextFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = GoogleSansTextFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = GoogleSansTextFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = GoogleSansTextFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = GoogleSansTextFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = GoogleSansTextFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = GoogleSansTextFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = GoogleSansTextFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = GoogleSansTextFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = GoogleSansTextFamily)
)
// ---------------------------------------------------------

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ẩn thanh trạng thái (Status bar) để app tràn viền
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val colorTabSelected = Color(0xFF0060A7)
            val colorTabUnselected = Color(0xFF404753)
            val colorTabBarBg = Color(0xFFEFF4FF)
            val colorContentBg = Color(0xFFF8F9FF)

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = colorTabSelected,
                    background = colorTabBarBg,
                    surface = colorContentBg
                ),
                typography = AppTypography
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = colorTabBarBg) {
                    val pagerState = rememberPagerState(pageCount = { 2 })
                    val coroutineScope = rememberCoroutineScope()
                    val tabs = listOf("Data", "AI")

                    Column(modifier = Modifier.fillMaxSize().background(colorTabBarBg)) {
                        TabRow(
                            selectedTabIndex = pagerState.currentPage,
                            containerColor = colorTabBarBg,
                            contentColor = colorTabSelected,
                            divider = {},
                            indicator = { tabPositions ->
                                if (pagerState.currentPage < tabPositions.size) {
                                    val currentTabPosition = tabPositions[pagerState.currentPage]
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier
                                            .tabIndicatorOffset(currentTabPosition)
                                            .padding(horizontal = currentTabPosition.width / 4)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                                        color = colorTabSelected,
                                        height = 4.dp
                                    )
                                }
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                val isSelected = pagerState.currentPage == index
                                Tab(
                                    text = {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSelected) colorTabSelected else colorTabUnselected
                                        )
                                    },
                                    selected = isSelected,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                                .background(colorContentBg)
                        ) {
                            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                when (page) {
                                    0 -> GopFileScreen(this@MainActivity)
                                    1 -> ChatScreen(this@MainActivity)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}