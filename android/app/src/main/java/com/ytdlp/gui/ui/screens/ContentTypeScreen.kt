package com.ytdlp.gui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytdlp.gui.data.ContentType
import com.ytdlp.gui.ui.components.GlassButton
import com.ytdlp.gui.ui.components.GlassCard
import com.ytdlp.gui.ui.components.GlassOutlinedButton
import com.ytdlp.gui.viewmodel.MainViewModel

@Composable
fun ContentTypeScreen(viewModel: MainViewModel) {
    val contentType by viewModel.contentType.collectAsState()
    val videoInfo by viewModel.videoInfo.collectAsState()
    val playlistInfo by viewModel.playlistInfo.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Ícone do tipo de conteúdo
        Text(
            text = when (contentType) {
                ContentType.VIDEO -> "🎬"
                ContentType.PLAYLIST -> "📋"
                ContentType.CHANNEL -> "📺"
                else -> "❓"
            },
            fontSize = 64.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (contentType) {
                ContentType.VIDEO -> "Vídeo Encontrado"
                ContentType.PLAYLIST -> "Playlist Encontrada"
                ContentType.CHANNEL -> "Canal Encontrado"
                else -> "Conteúdo Detectado"
            },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Card com informações
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            when (contentType) {
                ContentType.VIDEO -> {
                    videoInfo?.let { video ->
                        Text(
                            text = video.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        if (video.uploader.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = video.uploader,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        if (video.duration.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = video.duration,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
                
                ContentType.PLAYLIST, ContentType.CHANNEL -> {
                    playlistInfo?.let { playlist ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${playlist.entryCount} vídeos",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        if (playlist.title.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = playlist.title,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                else -> {
                    Text(
                        text = "Tipo de conteúdo não identificado",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
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
                onClick = { viewModel.nextStep() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Continuar")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
