package com.ytdlp.gui.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yausername.youtubedl_android.YoutubeDL
import com.ytdlp.gui.YtDlpApplication
import com.ytdlp.gui.data.WizardStep
import com.ytdlp.gui.ui.components.GlassButton
import com.ytdlp.gui.ui.components.GlassCard
import com.ytdlp.gui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SetupScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isUpdating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    
    // Usar StateFlow para reagir às mudanças
    val isReady by YtDlpApplication.isYtDlpReady.collectAsState()
    val initError by YtDlpApplication.initError.collectAsState()
    val isInitializing by YtDlpApplication.isInitializing.collectAsState()
    val autoUpdateStatus by YtDlpApplication.updateStatus.collectAsState()
    val isAutoUpdating by YtDlpApplication.isUpdating.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo/Título
        Text(
            text = "🎬",
            fontSize = 80.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "YT-DLP GUI",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = "para Android",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bem-vindo!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Baixe vídeos e músicas do YouTube, Twitter, Instagram e mais de 1000 sites.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Status do yt-dlp
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReady) {
                        if (isAutoUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Atualizando yt-dlp...",
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = autoUpdateStatus ?: "✅ yt-dlp pronto",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (initError != null) {
                        Text(
                            text = "❌ Erro: $initError",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isInitializing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Inicializando yt-dlp...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Mensagem de atualização
                updateMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = if (msg.contains("Erro")) MaterialTheme.colorScheme.error 
                               else MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Botões
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botão de atualizar
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isUpdating = true
                        updateMessage = "🔄 Atualizando yt-dlp..."
                        try {
                            val result = withContext(Dispatchers.IO) {
                                // Usar canal STABLE - versões recentes exigem JS runtime
                                YoutubeDL.getInstance().updateYoutubeDL(
                                    context.applicationContext as Application,
                                    YoutubeDL.UpdateChannel.STABLE
                                )
                            }
                            updateMessage = when (result) {
                                YoutubeDL.UpdateStatus.DONE -> "✅ yt-dlp atualizado com sucesso!"
                                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "✅ yt-dlp já está atualizado"
                                else -> "ℹ️ Status: $result"
                            }
                        } catch (e: Exception) {
                            updateMessage = "❌ Erro: ${e.message}"
                        } finally {
                            isUpdating = false
                        }
                    }
                },
                enabled = isReady && !isUpdating && !isAutoUpdating,
                modifier = Modifier.weight(1f)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Atualizar")
            }
            
            // Botão de continuar
            GlassButton(
                onClick = { viewModel.goToStep(WizardStep.URL_INPUT) },
                enabled = isReady,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Começar")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Features
        Text(
            text = "✨ Suporte a playlists e canais\n🔒 Login para vídeos privados\n📱 Download em segundo plano\n🎵 Extração de áudio MP3",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
