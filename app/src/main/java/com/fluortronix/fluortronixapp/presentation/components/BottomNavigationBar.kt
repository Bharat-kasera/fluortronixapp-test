package com.fluortronix.fluortronixapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import com.fluortronix.fluortronixapp.R

data class BottomNavItem(
    val label: String,
    val selectedIcon: Painter,
    val unselectedIcon: Painter,
    val route: String
)

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem("Home", painterResource(R.drawable.home), painterResource(R.drawable.home), "home"),
        BottomNavItem("Rooms", painterResource(R.drawable.roomicon), painterResource(R.drawable.roomicon), "rooms"),
        BottomNavItem("Schedule", painterResource(R.drawable.routine), painterResource(R.drawable.routine), "schedule"),
        BottomNavItem("Profile", painterResource(R.drawable.profile), painterResource(R.drawable.profile), "profile")
    )

    // Get screen width for responsive sizing
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    
    // Calculate responsive dimensions
    val itemSize = if (screenWidthDp < 400) 55.dp else 59.dp //navigation bar size
    val iconSize = if (screenWidthDp < 400) 24.dp else 28.dp
    
    // Calculate responsive horizontal padding to ensure all items fit
    val totalItemsWidth = itemSize * 4 // 4 navigation items
    val totalSpacing = 8.dp * 3 // 3 gaps between 4 items
    val rowPadding = 16.dp // horizontal padding inside Row
    val minRequiredWidth = totalItemsWidth + totalSpacing + rowPadding
    val availableWidth = screenWidthDp.dp
    val maxHorizontalPadding = 62.dp
    val minHorizontalPadding = 16.dp
    val horizontalPadding = ((availableWidth - minRequiredWidth) / 2).coerceIn(minHorizontalPadding, maxHorizontalPadding)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color(0xFF525251),
                    shape = RoundedCornerShape(50.dp)
                )
                .border(
                    width = Dp.Hairline,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = .8f),
                            Color.White.copy(alpha = .2f),
                        ),
                    ),
                    shape = CircleShape
                )
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                Box(
                    modifier = Modifier
                        .size(itemSize)
                        .clip(CircleShape)
                        .background(
                            color = if (isSelected) Color.White else Color(0xFF656565)
                        )
                        .clickable { onNavigate(item.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) Color.Black else Color(0xFFB3B3B3),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}