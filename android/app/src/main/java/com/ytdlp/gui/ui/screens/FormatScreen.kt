package com.ytdlp.gui.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ytdlp.gui.data.AudioQuality
import com.ytdlp.gui.data.ContentType
import com.ytdlp.gui.data.DownloadFormat
import com.ytdlp.gui.data.Platform
import com.ytdlp.gui.ui.components.GlassButton
import com.ytdlp.gui.ui.components.GlassOutlinedButton
import com.ytdlp.gui.viewmodel.MainViewModel

// Emoji e cor para cada plataforma
fun getPlatformEmoji(platform: Platform): String {
    return when (platform) {
        Platform.YOUTUBE -> "📺"
        Platform.INSTAGRAM -> "📷"
        Platform.TWITTER -> "🐦"
        Platform.TIKTOK -> "🎵"
        Platform.VIMEO -> "🎬"
        Platform.FACEBOOK -> "📘"
        Platform.GENERIC -> "🌐"
    }
}

// Enum para qualidade de vídeo com tamanho estimado por minuto (em MB)
enum class VideoQuality(
    val format: DownloadFormat, 
    val label: String, 
    val description: String,
    val mbPerMinute: Float // MB por minuto de vídeo
) {
    BEST(DownloadFormat.VIDEO_BEST, "Melhor qualidade", "Maior resolução disponível", 25f),
    FHD(DownloadFormat.VIDEO_1080P, "Full HD (1080p)", "Boa qualidade", 15f),
    HD(DownloadFormat.VIDEO_720P, "HD (720p)", "Qualidade média", 8f),
    SD(DownloadFormat.VIDEO_480P, "SD (480p)", "Arquivos menores", 4f)
}

// Função para estimar tamanho baseado na duração e qualidade
fun estimateDownloadSize(
    durationMinutes: Float,
    videoCount: Int,
    format: DownloadFormat,
    audioQuality: AudioQuality
): Float {
    val mbPerMinute = when (format) {
        DownloadFormat.AUDIO_ONLY -> {
            when (audioQuality) {
                AudioQuality.BEST -> 2.5f
                AudioQuality.HIGH -> 2.4f // 320kbps
                AudioQuality.GOOD -> 1.9f // 256kbps
                AudioQuality.MEDIUM -> 1.4f // 192kbps
                AudioQuality.STANDARD -> 1.0f // 128kbps
            }
        }
        else -> VideoQuality.values().find { it.format == format }?.mbPerMinute ?: 15f
    }
    
    // Estimar duração média de 4 minutos por vídeo se não tiver informação
    val totalMinutes = if (durationMinutes > 0) durationMinutes * videoCount else videoCount * 4f
    return totalMinutes * mbPerMinute
}

fun formatSizeEstimate(sizeMB: Float): String {
    return if (sizeMB >= 1024) {
        String.format("%.1f GB", sizeMB / 1024)
    } else {
        String.format("%.0f MB", sizeMB)
    }
}

enum class SizeWarningLevel {
    NORMAL,  // < 100MB - verde
    WARNING, // 100-500MB - laranja
    DANGER   // > 500MB - vermelho
}

fun getSizeWarningLevel(sizeMB: Float): SizeWarningLevel {
    return when {
        sizeMB >= 500 -> SizeWarningLevel.DANGER
        sizeMB >= 100 -> SizeWarningLevel.WARNING
        else -> SizeWarningLevel.NORMAL
    }
}

@Composable
fun FormatScreen(viewModel: MainViewModel) {
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val contentType by viewModel.contentType.collectAsState()
    val selectedVideos by viewModel.selectedVideos.collectAsState()
    val videoInfo by viewModel.videoInfo.collectAsState()
    val playlistInfo by viewModel.playlistInfo.collectAsState()
    val detectedPlatform by viewModel.detectedPlatform.collectAsState()
    
    val videoCount = when (contentType) {
        ContentType.VIDEO -> 1
        else -> selectedVideos.size
    }
    
    // Calcular duração média em minutos
    val avgDurationMinutes = remember(videoInfo, playlistInfo, selectedVideos) {
        when (contentType) {
            ContentType.VIDEO -> {
                val duration = videoInfo?.duration ?: "0:00"
                parseDurationToMinutes(duration)
            }
            else -> {
                // Média de 4 minutos por vídeo em playlists
                4f
            }
        }
    }
    
    val isAudioOnly = selectedFormat == DownloadFormat.AUDIO_ONLY
    val isVideo = !isAudioOnly
    
    // Calcular tamanho estimado
    val estimatedSizeMB = remember(videoCount, avgDurationMinutes, selectedFormat, audioQuality) {
        estimateDownloadSize(avgDurationMinutes, videoCount, selectedFormat, audioQuality)
    }
    
    val warningLevel = getSizeWarningLevel(estimatedSizeMB)
    
    // Diálogo de confirmação para downloads grandes
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    // Estado para controlar qual seção está expandida (começam fechadas)
    var videoExpanded by remember { mutableStateOf(false) }
    var audioExpanded by remember { mutableStateOf(false) }
    
    // Estado para saber se o usuário já fez uma escolha
    var hasUserSelected by remember { mutableStateOf(false) }
    
    // Encontrar qualidade de vídeo selecionada
    val selectedVideoQuality = VideoQuality.values().find { it.format == selectedFormat } ?: VideoQuality.BEST
    
    // Verificar se tem seleção válida para poder continuar
    val canContinue = hasUserSelected
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "🎛️ Formato e Qualidade",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Informações: plataforma e quantidade
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${getPlatformEmoji(detectedPlatform)} ${detectedPlatform.displayName}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$videoCount vídeo(s)",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        
        // Aviso para plataformas com limitações
        if (detectedPlatform != Platform.YOUTUBE && detectedPlatform != Platform.VIMEO) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (detectedPlatform) {
                    Platform.INSTAGRAM -> "ℹ️ Instagram: qualidade fixa pelo site"
                    Platform.TWITTER -> "ℹ️ Twitter/X: qualidades limitadas"
                    Platform.TIKTOK -> "ℹ️ TikTok: qualidade fixa pelo site"
                    Platform.FACEBOOK -> "ℹ️ Facebook: qualidades limitadas"
                    else -> "ℹ️ Site genérico: formatos podem variar"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "O QUE VOCÊ DESEJA BAIXAR?",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Área rolável
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === VÍDEO COMPLETO ===
            MainOptionCard(
                title = "Vídeo Completo",
                subtitle = if (hasUserSelected && isVideo) selectedVideoQuality.label else "Baixar vídeo com áudio (MP4)",
                icon = Icons.Default.VideoLibrary,
                isSelected = hasUserSelected && isVideo,
                isExpanded = videoExpanded,
                onClick = {
                    viewModel.setFormat(DownloadFormat.VIDEO_BEST)
                    hasUserSelected = true
                    videoExpanded = !videoExpanded
                    if (videoExpanded) audioExpanded = false
                }
            )
            
            // Opções de qualidade de vídeo
            AnimatedVisibility(
                visible = videoExpanded && isVideo,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SELECIONE A QUALIDADE:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    VideoQuality.values().forEach { quality ->
                        QualityOption(
                            label = quality.label,
                            description = quality.description,
                            isSelected = selectedFormat == quality.format,
                            onClick = { viewModel.setFormat(quality.format) }
                        )
                    }
                }
            }
            
            // === APENAS ÁUDIO ===
            MainOptionCard(
                title = "Apenas Áudio",
                subtitle = if (hasUserSelected && isAudioOnly) audioQuality.label else "Extrair somente a música (MP3)",
                icon = Icons.Default.MusicNote,
                isSelected = hasUserSelected && isAudioOnly,
                isExpanded = audioExpanded,
                onClick = {
                    viewModel.setFormat(DownloadFormat.AUDIO_ONLY)
                    hasUserSelected = true
                    audioExpanded = !audioExpanded
                    if (audioExpanded) videoExpanded = false
                }
            )
            
            // Opções de qualidade de áudio
            AnimatedVisibility(
                visible = audioExpanded && isAudioOnly,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SELECIONE A QUALIDADE:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    AudioQuality.values().forEach { quality ->
                        QualityOption(
                            label = quality.label,
                            description = quality.description,
                            isSelected = audioQuality == quality,
                            onClick = { viewModel.setAudioQuality(quality) }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Aviso de tamanho estimado - só mostra quando o usuário selecionou algo
        if (hasUserSelected) {
            val sizeColor = when (warningLevel) {
                SizeWarningLevel.NORMAL -> Color(0xFF4CAF50) // Verde
                SizeWarningLevel.WARNING -> Color(0xFFFF9800) // Laranja
                SizeWarningLevel.DANGER -> Color(0xFFF44336) // Vermelho
            }
            
            val sizeIcon = when (warningLevel) {
                SizeWarningLevel.NORMAL -> Icons.Default.CheckCircle
                SizeWarningLevel.WARNING -> Icons.Default.Warning
                SizeWarningLevel.DANGER -> Icons.Default.Error
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        sizeColor.copy(alpha = 0.15f)
                    )
                    .border(
                        width = 1.dp,
                        color = sizeColor.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = sizeIcon,
                        contentDescription = null,
                        tint = sizeColor,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tamanho estimado: ${formatSizeEstimate(estimatedSizeMB)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = sizeColor
                        )
                        Text(
                            text = when (warningLevel) {
                                SizeWarningLevel.NORMAL -> "Tamanho razoável para download"
                                SizeWarningLevel.WARNING -> "⚠️ Download de tamanho médio"
                                SizeWarningLevel.DANGER -> "⚠️ Download muito grande!"
                            },
                            fontSize = 12.sp,
                            color = sizeColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // Aviso para selecionar uma opção
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "Selecione uma opção acima para continuar",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Botões de navegação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassOutlinedButton(
                onClick = { viewModel.previousStep() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Voltar")
            }
            
            GlassButton(
                onClick = { 
                    if (canContinue) {
                        if (warningLevel != SizeWarningLevel.NORMAL) {
                            showConfirmDialog = true
                        } else {
                            viewModel.nextStep()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = canContinue
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (canContinue) "Continuar" else "Selecione")
            }
        }
    }
    
    // Diálogo de confirmação para downloads grandes
    if (showConfirmDialog) {
        Dialog(onDismissRequest = { showConfirmDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E1E2E),
                                Color(0xFF2D1B4E)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFB24592).copy(alpha = 0.5f),
                                Color(0xFF6B5CE7).copy(alpha = 0.5f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val dialogColor = if (warningLevel == SizeWarningLevel.DANGER) 
                        Color(0xFFF44336) else Color(0xFFFF9800)
                    
                    Icon(
                        imageVector = if (warningLevel == SizeWarningLevel.DANGER) 
                            Icons.Default.Error else Icons.Default.Warning,
                        contentDescription = null,
                        tint = dialogColor,
                        modifier = Modifier.size(56.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (warningLevel == SizeWarningLevel.DANGER)
                            "Download Muito Grande!"
                        else
                            "Atenção ao Tamanho",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "O download estimado é de aproximadamente ${formatSizeEstimate(estimatedSizeMB)}." +
                            if (warningLevel == SizeWarningLevel.DANGER)
                                "\n\nIsso pode consumir muitos dados móveis e espaço de armazenamento."
                            else
                                "\n\nVerifique se você tem espaço suficiente.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassOutlinedButton(
                            onClick = { showConfirmDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar")
                        }
                        
                        GlassButton(
                            onClick = { 
                                showConfirmDialog = false
                                viewModel.nextStep()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Baixar mesmo assim")
                        }
                    }
                }
            }
        }
    }
}

// Função para converter duração "mm:ss" ou "hh:mm:ss" para minutos
private fun parseDurationToMinutes(duration: String): Float {
    try {
        val parts = duration.split(":")
        return when (parts.size) {
            2 -> {
                val minutes = parts[0].toFloatOrNull() ?: 0f
                val seconds = parts[1].toFloatOrNull() ?: 0f
                minutes + (seconds / 60f)
            }
            3 -> {
                val hours = parts[0].toFloatOrNull() ?: 0f
                val minutes = parts[1].toFloatOrNull() ?: 0f
                val seconds = parts[2].toFloatOrNull() ?: 0f
                (hours * 60f) + minutes + (seconds / 60f)
            }
            else -> 4f // Default 4 minutos
        }
    } catch (e: Exception) {
        return 4f // Default 4 minutos
    }
}

@Composable
private fun MainOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.25f),
                            primaryColor.copy(alpha = 0.15f)
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
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) primaryColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) primaryColor.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) primaryColor else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // Indicador de expansão
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isSelected) primaryColor else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QualityOption(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else 
                    Color.White.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else 
                    Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun FormatOption(
    format: DownloadFormat,
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                }
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selecionado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
