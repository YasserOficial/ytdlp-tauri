package com.ytdlp.gui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytdlp.gui.ui.components.GlassButton
import com.ytdlp.gui.ui.components.GlassCard
import com.ytdlp.gui.ui.components.GlassOutlinedButton
import com.ytdlp.gui.ui.components.VideoItem
import com.ytdlp.gui.viewmodel.MainViewModel

@Composable
fun SelectionScreen(viewModel: MainViewModel) {
    val playlistInfo by viewModel.playlistInfo.collectAsState()
    val selectedVideos by viewModel.selectedVideos.collectAsState()
    
    val videos = playlistInfo?.entries ?: emptyList()
    val allSelected = videos.isNotEmpty() && selectedVideos.size == videos.size
    
    // Estado da lista para performance
    val listState = rememberLazyListState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "📋 Selecione os vídeos",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedVideos.size} de ${videos.size} selecionados",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )
            
            // Indicador se a lista foi limitada
            if (videos.size >= 50) {
                Text(
                    text = "📌 Limite: ${videos.size}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botões de seleção rápida
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { 
                    if (allSelected) viewModel.deselectAllVideos() 
                    else viewModel.selectAllVideos() 
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (allSelected) Icons.Default.CheckBoxOutlineBlank else Icons.Default.CheckBox,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (allSelected) "Desmarcar" else "Marcar todos")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Lista de vídeos com key para performance
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = videos,
                key = { it.id }  // Usar ID como key para performance
            ) { video ->
                VideoItem(
                    video = video,
                    isSelected = selectedVideos.contains(video.id),
                    onClick = { viewModel.toggleVideoSelection(video.id) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
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
                enabled = selectedVideos.isNotEmpty(),
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
    }
}
