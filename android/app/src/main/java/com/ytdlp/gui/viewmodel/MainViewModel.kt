package com.ytdlp.gui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.ytdlp.gui.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MainViewModel : ViewModel() {
    
    // Estado atual do wizard
    private val _currentStep = MutableStateFlow(WizardStep.SETUP)
    val currentStep: StateFlow<WizardStep> = _currentStep.asStateFlow()
    
    // URL de entrada
    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()
    
    // Status de carregamento
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Erro
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Tipo de conteúdo detectado
    private val _contentType = MutableStateFlow(ContentType.UNKNOWN)
    val contentType: StateFlow<ContentType> = _contentType.asStateFlow()
    
    // Informações do vídeo/playlist
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()
    
    private val _playlistInfo = MutableStateFlow<PlaylistInfo?>(null)
    val playlistInfo: StateFlow<PlaylistInfo?> = _playlistInfo.asStateFlow()
    
    // Vídeos selecionados
    private val _selectedVideos = MutableStateFlow<Set<String>>(emptySet())
    val selectedVideos: StateFlow<Set<String>> = _selectedVideos.asStateFlow()
    
    // Limite de vídeos para playlists/rádios (0 = sem limite)
    private val _playlistLimit = MutableStateFlow(50)
    val playlistLimit: StateFlow<Int> = _playlistLimit.asStateFlow()
    
    // Formato selecionado
    private val _selectedFormat = MutableStateFlow(DownloadFormat.VIDEO_BEST)
    val selectedFormat: StateFlow<DownloadFormat> = _selectedFormat.asStateFlow()
    
    // Qualidade de áudio selecionada
    private val _audioQuality = MutableStateFlow(AudioQuality.HIGH)
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()
    
    // Plataforma detectada pela URL
    private val _detectedPlatform = MutableStateFlow(Platform.GENERIC)
    val detectedPlatform: StateFlow<Platform> = _detectedPlatform.asStateFlow()
    
    // Progresso de downloads
    private val _downloadProgress = MutableStateFlow<List<DownloadProgress>>(emptyList())
    val downloadProgress: StateFlow<List<DownloadProgress>> = _downloadProgress.asStateFlow()
    
    // Autenticação
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private val _needsAuth = MutableStateFlow(false)
    val needsAuth: StateFlow<Boolean> = _needsAuth.asStateFlow()
    
    // Diretório de download (destino final)
    private val _downloadDir = MutableStateFlow<File?>(null)
    val downloadDir: StateFlow<File?> = _downloadDir.asStateFlow()
    
    // Diretório temporário para downloads (sempre a pasta privada do app)
    private var tempDownloadDir: File? = null
    
    // Tipo de pasta de destino
    private var destinationType: DestinationType = DestinationType.APP_FOLDER
    
    private var cookiesFile: File? = null

    enum class DestinationType {
        APP_FOLDER,      // Pasta privada do app (acesso direto)
        PUBLIC_DOWNLOADS, // Downloads público
        PUBLIC_MUSIC,     // Música pública
        PUBLIC_MOVIES     // Vídeos público
    }

    fun initialize(context: Context) {
        val defaultDir = File(context.getExternalFilesDir(null), "downloads")
        defaultDir.mkdirs()
        _downloadDir.value = defaultDir
        tempDownloadDir = defaultDir
        destinationType = DestinationType.APP_FOLDER
        
        cookiesFile = File(context.filesDir, "cookies.txt")
        _isAuthenticated.value = cookiesFile?.exists() == true && cookiesFile?.length() ?: 0 > 0
    }

    fun setDownloadDir(context: Context, dir: File, type: DestinationType) {
        _downloadDir.value = dir
        destinationType = type
        
        // Sempre usar pasta privada do app para download temporário
        tempDownloadDir = File(context.getExternalFilesDir(null), "downloads")
        tempDownloadDir?.mkdirs()
        dir.mkdirs()
    }
    
    fun setUrl(newUrl: String) {
        _url.value = newUrl
        _error.value = null
        // Detectar plataforma pela URL
        _detectedPlatform.value = Platform.fromUrl(newUrl)
    }
    
    fun goToStep(step: WizardStep) {
        _currentStep.value = step
    }
    
    fun nextStep() {
        _currentStep.value = when (_currentStep.value) {
            WizardStep.SETUP -> WizardStep.URL_INPUT
            WizardStep.URL_INPUT -> WizardStep.LOADING
            WizardStep.AUTH -> WizardStep.URL_INPUT
            WizardStep.LOADING -> {
                when (_contentType.value) {
                    ContentType.VIDEO -> WizardStep.FORMAT
                    ContentType.PLAYLIST, ContentType.CHANNEL -> WizardStep.SELECTION
                    else -> WizardStep.URL_INPUT
                }
            }
            WizardStep.CONTENT_TYPE -> WizardStep.SELECTION
            WizardStep.SELECTION -> WizardStep.FORMAT
            WizardStep.FORMAT -> WizardStep.LOCATION
            WizardStep.LOCATION -> WizardStep.DOWNLOAD
            WizardStep.DOWNLOAD -> WizardStep.URL_INPUT
        }
    }
    
    fun previousStep() {
        _currentStep.value = when (_currentStep.value) {
            WizardStep.SETUP -> WizardStep.SETUP
            WizardStep.URL_INPUT -> WizardStep.SETUP
            WizardStep.AUTH -> WizardStep.URL_INPUT
            WizardStep.LOADING -> WizardStep.URL_INPUT
            WizardStep.CONTENT_TYPE -> WizardStep.URL_INPUT
            WizardStep.SELECTION -> WizardStep.URL_INPUT
            WizardStep.FORMAT -> {
                if (_contentType.value == ContentType.VIDEO) WizardStep.URL_INPUT
                else WizardStep.SELECTION
            }
            WizardStep.LOCATION -> WizardStep.FORMAT
            WizardStep.DOWNLOAD -> WizardStep.LOCATION
        }
    }
    
    fun fetchInfo(context: Context) {
        if (_url.value.isBlank()) {
            _error.value = "Por favor, insira uma URL válida"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _currentStep.value = WizardStep.LOADING
            
            try {
                val info = withContext(Dispatchers.IO) {
                    val limit = _playlistLimit.value
                    val request = YoutubeDLRequest(_url.value).apply {
                        addOption("--dump-json")
                        addOption("--flat-playlist")
                        addOption("--no-download")
                        
                        // Limitar número de vídeos em playlists/rádios
                        if (limit > 0) {
                            addOption("--playlist-end", limit.toString())
                        }
                        
                        // Adicionar cookies se disponível
                        if (cookiesFile?.exists() == true) {
                            addOption("--cookies", cookiesFile!!.absolutePath)
                        }
                        
                        // Opções para melhor compatibilidade
                        addOption("--no-check-certificates")
                        addOption("--ignore-errors")
                    }
                    
                    val response = YoutubeDL.getInstance().execute(request)
                    response.out
                }
                
                parseInfo(info)
                
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Erro desconhecido"
                
                // Verificar se é erro de JavaScript runtime ou impersonation (problema de sites modernos)
                val jsRuntimeError = listOf(
                    "JavaScript runtime",
                    "No supported JavaScript",
                    "JS runtime",
                    "nsig extraction",
                    "signature extraction",
                    "Signature extraction failed",
                    "impersonation",
                    "impersonate target",
                    "no impersonate"
                ).any { errorMsg.contains(it, ignoreCase = true) }
                
                // Verificar se precisa de autenticação - lista expandida de mensagens
                val authRequired = listOf(
                    "Sign in", "sign in", "signin",
                    "Private video", "private video",
                    "members-only", "member only",
                    "login required",
                    "age-restricted", "age restricted",
                    "confirm your age", "verify your age",
                    "This video is private",
                    "who has access",
                    "Join this channel",
                    "This video requires payment",
                    "rental", "purchase"
                ).any { errorMsg.contains(it, ignoreCase = true) }
                
                when {
                    jsRuntimeError -> {
                        _error.value = "⚠️ Este site requer recursos avançados\n\n" +
                            "O yt-dlp não conseguiu baixar este vídeo. " +
                            "Isso pode acontecer com alguns vídeos protegidos.\n\n" +
                            "💡 Dicas:\n" +
                            "• Tente outro vídeo do mesmo site\n" +
                            "• Faça login se for conteúdo privado\n" +
                            "• Alguns vídeos podem estar protegidos"
                        _currentStep.value = WizardStep.URL_INPUT
                    }
                    authRequired -> {
                        _needsAuth.value = true
                        _error.value = "🔐 Este conteúdo requer autenticação.\nFaça login para continuar."
                        _currentStep.value = WizardStep.AUTH
                    }
                    else -> {
                        _error.value = errorMsg
                        _currentStep.value = WizardStep.URL_INPUT
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun parseInfo(jsonString: String) {
        try {
            val lines = jsonString.trim().split("\n")
            
            if (lines.size > 1) {
                // É uma playlist
                val videos = lines.mapNotNull { line ->
                    try {
                        val json = JSONObject(line)
                        val videoId = json.optString("id", "")
                        val title = json.optString("title", "")
                        
                        // Filtrar vídeos indisponíveis (privados, deletados, etc)
                        if (videoId.isEmpty() || isUnavailableVideo(title)) {
                            return@mapNotNull null
                        }
                        
                        // Usar thumbnail do YouTube sem bordas pretas
                        val thumbnail = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                        
                        VideoInfo(
                            id = videoId,
                            title = title.ifEmpty { "Sem título" },
                            uploader = json.optString("uploader", json.optString("channel", "")),
                            thumbnail = thumbnail,
                            duration = formatDuration(json.optDouble("duration", 0.0)),
                            url = json.optString("url", json.optString("webpage_url", "https://www.youtube.com/watch?v=$videoId"))
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                _playlistInfo.value = PlaylistInfo(
                    entries = videos,
                    entryCount = videos.size
                )
                _contentType.value = ContentType.PLAYLIST
                _selectedVideos.value = videos.map { it.id }.toSet()
                
            } else {
                // É um vídeo único
                val json = JSONObject(jsonString)
                _videoInfo.value = VideoInfo(
                    id = json.optString("id", ""),
                    title = json.optString("title", "Sem título"),
                    uploader = json.optString("uploader", ""),
                    thumbnail = json.optString("thumbnail", ""),
                    duration = formatDuration(json.optDouble("duration", 0.0)),
                    url = json.optString("webpage_url", _url.value)
                )
                _contentType.value = ContentType.VIDEO
                _selectedVideos.value = setOf(_videoInfo.value?.id ?: "")
            }
            
            nextStep()
            
        } catch (e: Exception) {
            _error.value = "Erro ao processar informações: ${e.message}"
            _currentStep.value = WizardStep.URL_INPUT
        }
    }
    
    private fun formatDuration(seconds: Double): String {
        if (seconds <= 0) return ""
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
    
    fun toggleVideoSelection(videoId: String) {
        val current = _selectedVideos.value.toMutableSet()
        if (current.contains(videoId)) {
            current.remove(videoId)
        } else {
            current.add(videoId)
        }
        _selectedVideos.value = current
    }
    
    fun selectAllVideos() {
        // Usar toSet() direto é mais eficiente para listas grandes
        val allIds = _playlistInfo.value?.entries?.mapTo(HashSet()) { it.id } ?: emptySet()
        _selectedVideos.value = allIds
    }
    
    fun deselectAllVideos() {
        _selectedVideos.value = emptySet()
    }
    
    fun setPlaylistLimit(limit: Int) {
        _playlistLimit.value = limit.coerceIn(10, 150)
    }
    
    /**
     * Verifica se um vídeo está indisponível baseado no título
     */
    private fun isUnavailableVideo(title: String): Boolean {
        val unavailablePatterns = listOf(
            "[Private video]",
            "[Deleted video]",
            "[Unavailable video]",
            "Private video",
            "Deleted video",
            "Vídeo privado",
            "Vídeo excluído",
            "Vídeo indisponível"
        )
        return unavailablePatterns.any { title.contains(it, ignoreCase = true) }
    }
    
    fun setFormat(format: DownloadFormat) {
        _selectedFormat.value = format
    }
    
    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
    }
    
    fun startDownload(context: Context) {
        viewModelScope.launch {
            _currentStep.value = WizardStep.DOWNLOAD
            
            val videosToDownload = when (_contentType.value) {
                ContentType.VIDEO -> listOf(_videoInfo.value!!)
                ContentType.PLAYLIST -> _playlistInfo.value?.entries?.filter { 
                    _selectedVideos.value.contains(it.id) 
                } ?: emptyList()
                else -> emptyList()
            }
            
            // Inicializar progresso
            _downloadProgress.value = videosToDownload.map { video ->
                DownloadProgress(
                    videoId = video.id,
                    title = video.title,
                    status = DownloadStatus.PENDING
                )
            }
            
            // Baixar cada vídeo
            videosToDownload.forEachIndexed { index, video ->
                downloadVideo(context, video, index)
            }
        }
    }
    
    private suspend fun downloadVideo(context: Context, video: VideoInfo, index: Int) {
        withContext(Dispatchers.IO) {
            try {
                updateProgress(index) { 
                    it.copy(status = DownloadStatus.DOWNLOADING, progress = 0f)
                }
                
                // Detectar plataforma pela URL do vídeo (pode ser diferente da URL original em playlists)
                val videoPlatform = Platform.fromUrl(video.url)
                
                // Obter formato apropriado para a plataforma
                val formatOption = getFormatString(videoPlatform, _selectedFormat.value)
                
                // Qualidade de áudio (bitrate)
                val audioBitrate = _audioQuality.value.bitrate
                
                // Sempre baixar na pasta temporária do app (acesso garantido)
                val safeTitle = video.title.replace(Regex("[^a-zA-Z0-9\\s\\-_]"), "").take(50)
                val outputTemplate = "${tempDownloadDir?.absolutePath}/${safeTitle}.%(ext)s"
                
                val request = YoutubeDLRequest(video.url).apply {
                    addOption("-f", formatOption)
                    addOption("-o", outputTemplate)
                    addOption("--no-mtime")
                    addOption("--no-check-certificates")
                    
                    // Opções específicas por plataforma
                    when (videoPlatform) {
                        Platform.INSTAGRAM -> {
                            // Instagram precisa de user-agent específico às vezes
                            addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        }
                        Platform.TWITTER -> {
                            // Twitter/X pode precisar de configuração especial
                            addOption("--extractor-args", "twitter:api=syndication")
                        }
                        Platform.TIKTOK -> {
                            // TikTok pode precisar de user-agent
                            addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        }
                        else -> { /* Sem opções extras */ }
                    }
                    
                    if (_selectedFormat.value == DownloadFormat.AUDIO_ONLY) {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        // Aplicar bitrate selecionado (se não for "melhor qualidade")
                        if (audioBitrate != "0") {
                            addOption("--audio-quality", "${audioBitrate}K")
                        }
                        // Embutir thumbnail como capa do MP3
                        addOption("--embed-thumbnail")
                        addOption("--add-metadata")
                    } else {
                        // Para vídeos, também embutir thumbnail
                        addOption("--embed-thumbnail")
                    }
                    
                    if (cookiesFile?.exists() == true) {
                        addOption("--cookies", cookiesFile!!.absolutePath)
                    }
                }
                
                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                    val progressValue = progress.toFloat() / 100f
                    val eta = if (etaInSeconds > 0) formatEta(etaInSeconds) else ""
                    
                    viewModelScope.launch {
                        updateProgress(index) {
                            it.copy(
                                progress = progressValue,
                                eta = eta,
                                status = if (progressValue >= 1f) DownloadStatus.CONVERTING else DownloadStatus.DOWNLOADING
                            )
                        }
                    }
                }
                
                // Se destino é pasta pública, mover arquivo usando MediaStore
                if (destinationType != DestinationType.APP_FOLDER) {
                    updateProgress(index) { 
                        it.copy(status = DownloadStatus.CONVERTING) // Mostrar que está movendo
                    }
                    
                    // Encontrar arquivo baixado
                    val downloadedFile = tempDownloadDir?.listFiles()?.find { 
                        it.name.startsWith(safeTitle) 
                    }
                    
                    if (downloadedFile != null && downloadedFile.exists()) {
                        val success = moveToPublicFolder(context, downloadedFile)
                        if (!success) {
                            updateProgress(index) { 
                                it.copy(
                                    status = DownloadStatus.COMPLETED, 
                                    progress = 1f,
                                    error = "Salvo na pasta do app (sem permissão para pasta pública)"
                                )
                            }
                            return@withContext
                        }
                    }
                }
                
                updateProgress(index) { 
                    it.copy(status = DownloadStatus.COMPLETED, progress = 1f)
                }
                
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Erro desconhecido"
                
                // Simplificar mensagem de erro para o usuário
                val userFriendlyError = when {
                    errorMsg.contains("impersonat", ignoreCase = true) -> 
                        "Vídeo protegido - não foi possível baixar"
                    errorMsg.contains("JavaScript", ignoreCase = true) -> 
                        "Site requer recursos não suportados"
                    errorMsg.contains("403", ignoreCase = true) -> 
                        "Acesso negado - tente fazer login"
                    errorMsg.contains("404", ignoreCase = true) -> 
                        "Vídeo não encontrado"
                    errorMsg.contains("private", ignoreCase = true) -> 
                        "Vídeo privado - faça login"
                    else -> errorMsg.take(100) // Limitar tamanho
                }
                
                updateProgress(index) { 
                    it.copy(status = DownloadStatus.FAILED, error = userFriendlyError)
                }
            }
        }
    }
    
    /**
     * Move arquivo para pasta pública usando MediaStore (Android 10+) ou acesso direto
     */
    private fun moveToPublicFolder(context: Context, sourceFile: File): Boolean {
        return try {
            val mimeType = when {
                sourceFile.extension.equals("mp3", true) -> "audio/mpeg"
                sourceFile.extension.equals("m4a", true) -> "audio/mp4"
                sourceFile.extension.equals("mp4", true) -> "video/mp4"
                sourceFile.extension.equals("webm", true) -> "video/webm"
                sourceFile.extension.equals("mkv", true) -> "video/x-matroska"
                else -> "application/octet-stream"
            }
            
            val isAudio = mimeType.startsWith("audio")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - usar MediaStore
                val contentUri = when (destinationType) {
                    DestinationType.PUBLIC_MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    DestinationType.PUBLIC_MOVIES -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }
                
                val relativePath = when (destinationType) {
                    DestinationType.PUBLIC_MUSIC -> Environment.DIRECTORY_MUSIC
                    DestinationType.PUBLIC_MOVIES -> Environment.DIRECTORY_MOVIES
                    else -> Environment.DIRECTORY_DOWNLOADS
                }
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(contentUri, contentValues)
                
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    
                    // Marcar como completo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    
                    // Deletar arquivo temporário
                    sourceFile.delete()
                    true
                } else {
                    false
                }
            } else {
                // Android 9 e anterior - copiar diretamente
                val destDir = _downloadDir.value
                if (destDir != null && destDir.exists()) {
                    val destFile = File(destDir, sourceFile.name)
                    sourceFile.copyTo(destFile, overwrite = true)
                    sourceFile.delete()
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun formatEta(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }
    
    private fun updateProgress(index: Int, update: (DownloadProgress) -> DownloadProgress) {
        val current = _downloadProgress.value.toMutableList()
        if (index in current.indices) {
            current[index] = update(current[index])
            _downloadProgress.value = current
        }
    }
    
    fun saveCookies(cookies: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cookiesFile?.writeText(cookies)
                _isAuthenticated.value = true
                _needsAuth.value = false
            } catch (e: Exception) {
                _error.value = "Erro ao salvar cookies: ${e.message}"
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            cookiesFile?.delete()
            _isAuthenticated.value = false
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Retenta downloads que falharam
     */
    fun retryFailedDownloads(context: Context) {
        viewModelScope.launch {
            val failedVideos = _downloadProgress.value
                .filter { it.status == DownloadStatus.FAILED }
                .map { it.videoId }
            
            if (failedVideos.isEmpty()) return@launch
            
            // Buscar informações dos vídeos que falharam
            val allVideos = when (_contentType.value) {
                ContentType.VIDEO -> listOf(_videoInfo.value!!)
                ContentType.PLAYLIST -> _playlistInfo.value?.entries ?: emptyList()
                else -> emptyList()
            }
            
            failedVideos.forEach { videoId ->
                val video = allVideos.find { it.id == videoId }
                val index = _downloadProgress.value.indexOfFirst { it.videoId == videoId }
                if (video != null && index >= 0) {
                    // Resetar status para PENDING
                    updateProgress(index) {
                        it.copy(status = DownloadStatus.PENDING, error = null, progress = 0f)
                    }
                    // Tentar baixar novamente
                    downloadVideo(context, video, index)
                }
            }
        }
    }
    
    /**
     * Remove vídeo com falha da lista de downloads
     */
    fun removeFailedDownload(videoId: String) {
        _downloadProgress.value = _downloadProgress.value.filter { it.videoId != videoId }
    }
    
    /**
     * Verifica se todos os downloads terminaram (sucesso ou falha)
     */
    fun allDownloadsFinished(): Boolean {
        return _downloadProgress.value.all { 
            it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED 
        }
    }
    
    /**
     * Conta downloads com sucesso
     */
    fun successfulDownloadsCount(): Int {
        return _downloadProgress.value.count { it.status == DownloadStatus.COMPLETED }
    }
    
    /**
     * Conta downloads com falha
     */
    fun failedDownloadsCount(): Int {
        return _downloadProgress.value.count { it.status == DownloadStatus.FAILED }
    }
    
    fun resetWizard() {
        _url.value = ""
        _videoInfo.value = null
        _playlistInfo.value = null
        _selectedVideos.value = emptySet()
        _selectedFormat.value = DownloadFormat.VIDEO_BEST
        _downloadProgress.value = emptyList()
        _contentType.value = ContentType.UNKNOWN
        _error.value = null
        _currentStep.value = WizardStep.URL_INPUT
    }
}
