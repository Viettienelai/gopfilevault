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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = colorTabBarBg) {
                    val pagerState = rememberPagerState(pageCount = { 2 })
                    val coroutineScope = rememberCoroutineScope()
                    val tabs = listOf("DATA", "AI")

                    Column(modifier = Modifier.fillMaxSize().background(colorTabBarBg)) {
                        TabRow(
                            selectedTabIndex = pagerState.currentPage,
                            containerColor = colorTabBarBg,
                            contentColor = colorTabSelected,
                            divider = {},
                            // TÙY CHỈNH THANH INDICATOR: Rút ngắn 1/2 và bo tròn 2 góc trên
                            indicator = { tabPositions ->
                                if (pagerState.currentPage < tabPositions.size) {
                                    val currentTabPosition = tabPositions[pagerState.currentPage]
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier
                                            .tabIndicatorOffset(currentTabPosition)
                                            .padding(horizontal = currentTabPosition.width / 4) // Bóp mỗi bên 1/4 -> Còn 1/2
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)), // Bo tròn 2 góc trên
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
                                            fontWeight = FontWeight.Bold,
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