package com.ytdlp.gui.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ytdlp.gui.data.DownloadProgress
import com.ytdlp.gui.data.DownloadStatus
import com.ytdlp.gui.ui.components.GlassButton
import com.ytdlp.gui.ui.components.GlassCard
import com.ytdlp.gui.viewmodel.MainViewModel

@Composable
fun DownloadScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadDir by viewModel.downloadDir.collectAsState()
    
    val completedCount = downloadProgress.count { it.status == DownloadStatus.COMPLETED }
    val failedCount = downloadProgress.count { it.status == DownloadStatus.FAILED }
    val totalCount = downloadProgress.size
    val allFinished = downloadProgress.all { 
        it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED 
    } && totalCount > 0
    val hasErrors = failedCount > 0
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when {
                        !allFinished -> "⬇️ Baixando..."
                        hasErrors && completedCount > 0 -> "⚠️ Concluído com erros"
                        hasErrors -> "❌ Falhou"
                        else -> "✅ Concluído!"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Text(
                    text = if (hasErrors) 
                        "$completedCount baixados, $failedCount com erro" 
                    else 
                        "$completedCount de $totalCount concluídos",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            
            if (hasErrors) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Progresso geral
        val overallProgress = if (totalCount > 0) {
            downloadProgress.sumOf { 
                when (it.status) {
                    DownloadStatus.COMPLETED -> 100
                    DownloadStatus.FAILED -> 100 // Considera falha como "processado"
                    DownloadStatus.DOWNLOADING, DownloadStatus.CONVERTING -> (it.progress * 100).toInt()
                    else -> 0
                }
            } / (totalCount * 100f)
        } else 0f
        
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Progresso geral: ${(overallProgress * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = overallProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (hasErrors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Lista de downloads
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(downloadProgress, key = { it.videoId }) { progress ->
                DownloadItem(progress = progress)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botões - mostrar quando todos terminaram (com ou sem erros)
        if (allFinished) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botão de reiniciar downloads com falha
                if (hasErrors) {
                    OutlinedButton(
                        onClick = { viewModel.retryFailedDownloads(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tentar novamente ($failedCount)")
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (completedCount > 0) {
                        OutlinedButton(
                            onClick = {
                                // Abrir pasta de downloads
                                downloadDir?.let { dir ->
                                    var opened = false
                                    
                                    // Tentar abrir com gerenciador de arquivos do sistema
                                    try {
                                        // Primeiro: tentar abrir o gerenciador de arquivos padrão
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            val uri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:${dir.absolutePath.removePrefix("/storage/emulated/0/")}")
                                            setDataAndType(uri, "vnd.android.document/directory")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(intent)
                                            opened = true
                                        }
                                    } catch (e: Exception) { }
                                    
                                    // Segundo: tentar com Files app (Google)
                                    if (!opened) {
                                        try {
                                            val intent = Intent("android.provider.action.BROWSE").apply {
                                                setDataAndType(
                                                    android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:${dir.absolutePath.removePrefix("/storage/emulated/0/")}"),
                                                    "vnd.android.document/directory"
                                                )
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            opened = true
                                        } catch (e: Exception) { }
                                    }
                                    
                                    // Terceiro: abrir gerenciador de downloads do sistema
                                    if (!opened) {
                                        try {
                                            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            opened = true
                                        } catch (e: Exception) { }
                                    }
                                    
                                    // Fallback: mostrar toast com o caminho
                                    if (!opened) {
                                        Toast.makeText(
                                            context,
                                            "📁 Arquivos salvos em:\n${dir.absolutePath}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver arquivos")
                        }
                    }
                    
                    GlassButton(
                        onClick = { viewModel.resetWizard() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Novo download")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(progress: DownloadProgress) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progress,
        label = "download_progress"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.05f)
                    )
                )
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícone de status
                Icon(
                    imageVector = when (progress.status) {
                        DownloadStatus.PENDING -> Icons.Default.Schedule
                        DownloadStatus.DOWNLOADING -> Icons.Default.Download
                        DownloadStatus.CONVERTING -> Icons.Default.Transform
                        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                        DownloadStatus.FAILED -> Icons.Default.Error
                        DownloadStatus.CANCELLED -> Icons.Default.Cancel
                    },
                    contentDescription = null,
                    tint = when (progress.status) {
                        DownloadStatus.COMPLETED -> Color(0xFF22C55E)
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Título
                Text(
                    text = progress.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Porcentagem ou status
                Text(
                    text = when (progress.status) {
                        DownloadStatus.PENDING -> "Aguardando"
                        DownloadStatus.DOWNLOADING -> "${(progress.progress * 100).toInt()}%"
                        DownloadStatus.CONVERTING -> "Convertendo..."
                        DownloadStatus.COMPLETED -> "Concluído"
                        DownloadStatus.FAILED -> "Erro"
                        DownloadStatus.CANCELLED -> "Cancelado"
                    },
                    fontSize = 12.sp,
                    color = when (progress.status) {
                        DownloadStatus.COMPLETED -> Color(0xFF22C55E)
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
            }
            
            // Barra de progresso
            if (progress.status == DownloadStatus.DOWNLOADING || progress.status == DownloadStatus.CONVERTING) {
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                
                // ETA
                if (progress.eta.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tempo restante: ${progress.eta}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Mensagem de erro
            if (progress.status == DownloadStatus.FAILED && progress.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progress.error,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
