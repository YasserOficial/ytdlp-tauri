package com.ytdlp.gui

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YtDlpApplication : Application() {
    
    companion object {
        private val _isYtDlpReady = MutableStateFlow(false)
        val isYtDlpReady: StateFlow<Boolean> = _isYtDlpReady.asStateFlow()
        
        private val _initError = MutableStateFlow<String?>(null)
        val initError: StateFlow<String?> = _initError.asStateFlow()
        
        private val _isInitializing = MutableStateFlow(true)
        val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()
        
        private val _updateStatus = MutableStateFlow<String?>(null)
        val updateStatus: StateFlow<String?> = _updateStatus.asStateFlow()
        
        private val _isUpdating = MutableStateFlow(false)
        val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar yt-dlp e FFmpeg em background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _isInitializing.value = true
                _initError.value = null
                
                // Inicializar YoutubeDL
                YoutubeDL.getInstance().init(this@YtDlpApplication)
                
                // Inicializar FFmpeg
                FFmpeg.getInstance().init(this@YtDlpApplication)
                
                _isYtDlpReady.value = true
                
                // Atualizar yt-dlp automaticamente após inicialização
                autoUpdateYtDlp()
                
            } catch (e: YoutubeDLException) {
                _initError.value = e.message ?: "Falha ao inicializar"
                e.printStackTrace()
            } catch (e: Exception) {
                _initError.value = e.message ?: "Erro desconhecido"
                e.printStackTrace()
            } finally {
                _isInitializing.value = false
            }
        }
    }
    
    private suspend fun autoUpdateYtDlp() {
        try {
            _isUpdating.value = true
            _updateStatus.value = "Verificando atualizações..."
            
            // NOTA: Usar canal STABLE em vez de NIGHTLY
            // Versões recentes (2025.11+) exigem JavaScript runtime que não funciona no Android
            // A versão embutida na biblioteca é mais compatível
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                this@YtDlpApplication,
                YoutubeDL.UpdateChannel.STABLE
            )
            
            _updateStatus.value = when (status) {
                YoutubeDL.UpdateStatus.DONE -> "✅ yt-dlp atualizado!"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "✅ yt-dlp pronto"
                else -> null
            }
        } catch (e: Exception) {
            // Não mostrar erro se falhar a atualização automática
            // O usuário ainda pode usar a versão embutida
            _updateStatus.value = "✅ yt-dlp pronto"
            e.printStackTrace()
        } finally {
            _isUpdating.value = false
        }
    }
    
    suspend fun updateYtDlp(): Result<String> {
        return try {
            _isUpdating.value = true
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                this,
                YoutubeDL.UpdateChannel.NIGHTLY
            )
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> Result.success("yt-dlp atualizado com sucesso!")
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> Result.success("yt-dlp já está atualizado")
                else -> Result.failure(Exception("Status desconhecido: $status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isUpdating.value = false
        }
    }
}
