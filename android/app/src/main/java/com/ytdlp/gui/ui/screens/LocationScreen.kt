package com.ytdlp.gui.ui.screens

import android.content.Intent
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytdlp.gui.ui.components.GlassButton
import com.ytdlp.gui.ui.components.GlassOutlinedButton
import com.ytdlp.gui.viewmodel.MainViewModel
import com.ytdlp.gui.viewmodel.MainViewModel.DestinationType
import java.io.File

@Composable
fun LocationScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentDir by viewModel.downloadDir.collectAsState()
    var selectedOption by remember { mutableStateOf(0) }
    
    // Opções de pastas predefinidas com tipo de destino
    val downloadOptions = remember {
        listOf(
            DownloadLocation(
                name = "Downloads do App",
                path = File(context.getExternalFilesDir(null), "downloads"),
                icon = Icons.Default.Folder,
                description = "Pasta privada do aplicativo",
                type = DestinationType.APP_FOLDER
            ),
            DownloadLocation(
                name = "Downloads",
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                icon = Icons.Default.Download,
                description = "Pasta de downloads do sistema",
                type = DestinationType.PUBLIC_DOWNLOADS
            ),
            DownloadLocation(
                name = "Músicas",
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                icon = Icons.Default.MusicNote,
                description = "Pasta de músicas do sistema",
                type = DestinationType.PUBLIC_MUSIC
            ),
            DownloadLocation(
                name = "Vídeos",
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                icon = Icons.Default.VideoLibrary,
                description = "Pasta de vídeos do sistema",
                type = DestinationType.PUBLIC_MOVIES
            )
        )
    }
    
    // Atualizar seleção baseada no diretório atual
    LaunchedEffect(currentDir) {
        currentDir?.let { dir ->
            val index = downloadOptions.indexOfFirst { it.path.absolutePath == dir.absolutePath }
            if (index >= 0) selectedOption = index
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "📁 Onde salvar?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Escolha onde os arquivos serão salvos",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Opções de localização
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            downloadOptions.forEachIndexed { index, option ->
                LocationOption(
                    location = option,
                    isSelected = selectedOption == index,
                    onClick = {
                        selectedOption = index
                        viewModel.setDownloadDir(context, option.path, option.type)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Mostrar caminho atual
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "📍 Caminho atual:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentDir?.absolutePath ?: "Não definido",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
                onClick = { viewModel.startDownload(context) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Baixar")
            }
        }
    }
}

data class DownloadLocation(
    val name: String,
    val path: File,
    val icon: ImageVector,
    val description: String,
    val type: DestinationType
)

@Composable
private fun LocationOption(
    location: DownloadLocation,
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
                imageVector = location.icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = location.description,
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
