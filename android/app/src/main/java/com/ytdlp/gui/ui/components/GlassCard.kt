package com.ytdlp.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Paleta de cores vibrante e moderna
private val CardGradientStart = Color(0xFF1E1E2E)
private val CardGradientEnd = Color(0xFF2D1B4E)
private val AccentPurple = Color(0xFFB24592)
private val AccentPink = Color(0xFFFF6B9D)
private val AccentBlue = Color(0xFF6B5CE7)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            // Fundo com gradiente diagonal sutil
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        CardGradientStart.copy(alpha = 0.95f),
                        CardGradientEnd.copy(alpha = 0.90f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            // Borda com gradiente colorido sutil
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        AccentPurple.copy(alpha = 0.4f),
                        AccentBlue.copy(alpha = 0.2f),
                        AccentPink.copy(alpha = 0.3f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        // Brilho no canto superior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(AccentPurple, AccentPink),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, 0f)
                    )
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(AccentPurple.copy(alpha = 0.7f), AccentBlue.copy(alpha = 0.5f))
                ),
                shape = RoundedCornerShape(14.dp)
            ),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = null,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        content = content
    )
}

// Card de input elegante
@Composable
fun GlassTextField(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        AccentPurple.copy(alpha = 0.3f),
                        AccentBlue.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        content()
    }
}

// Chip moderno
@Composable
fun GlassChip(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(
                        colors = listOf(
                            AccentPurple.copy(alpha = 0.4f),
                            AccentPink.copy(alpha = 0.2f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) AccentPurple.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        content = content
    )
}
