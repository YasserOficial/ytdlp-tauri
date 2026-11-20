package com.ytdlp.gui.data

data class VideoInfo(
    val id: String = "",
    val title: String = "",
    val uploader: String = "",
    val thumbnail: String = "",
    val duration: String = "",
    val url: String = "",
    val formats: List<FormatInfo> = emptyList()
)

data class FormatInfo(
    val formatId: String = "",
    val ext: String = "",
    val resolution: String = "",
    val filesize: Long = 0,
    val vcodec: String = "",
    val acodec: String = "",
    val fps: Int = 0,
    val tbr: Double = 0.0
)

data class PlaylistInfo(
    val id: String = "",
    val title: String = "",
    val uploader: String = "",
    val entries: List<VideoInfo> = emptyList(),
    val entryCount: Int = 0
)

enum class ContentType {
    VIDEO,
    PLAYLIST,
    CHANNEL,
    UNKNOWN
}

enum class DownloadFormat {
    VIDEO_BEST,
    VIDEO_1080P,
    VIDEO_720P,
    VIDEO_480P,
    AUDIO_ONLY
}

enum class AudioQuality(val bitrate: String, val label: String, val description: String) {
    BEST("0", "Melhor qualidade", "Maior bitrate disponível"),
    HIGH("320", "320 kbps", "Alta qualidade"),
    GOOD("256", "256 kbps", "Boa qualidade"),
    MEDIUM("192", "192 kbps", "Qualidade média"),
    STANDARD("128", "128 kbps", "Qualidade padrão")
}

data class DownloadProgress(
    val videoId: String = "",
    val title: String = "",
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val status: DownloadStatus = DownloadStatus.PENDING,
    val error: String? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    CONVERTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class WizardStep {
    SETUP,
    URL_INPUT,
    AUTH,
    LOADING,
    CONTENT_TYPE,
    SELECTION,
    FORMAT,
    LOCATION,  // Nova etapa para escolher onde salvar
    DOWNLOAD
}

/**
 * Plataformas suportadas com configurações específicas de formato
 */
enum class Platform(val displayName: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TWITTER("Twitter/X"),
    TIKTOK("TikTok"),
    VIMEO("Vimeo"),
    FACEBOOK("Facebook"),
    GENERIC("Outro site");
    
    companion object {
        fun fromUrl(url: String): Platform {
            val lowercaseUrl = url.lowercase()
            return when {
                lowercaseUrl.contains("youtube.com") || lowercaseUrl.contains("youtu.be") -> YOUTUBE
                lowercaseUrl.contains("instagram.com") -> INSTAGRAM
                lowercaseUrl.contains("twitter.com") || lowercaseUrl.contains("x.com") -> TWITTER
                lowercaseUrl.contains("tiktok.com") -> TIKTOK
                lowercaseUrl.contains("vimeo.com") -> VIMEO
                lowercaseUrl.contains("facebook.com") || lowercaseUrl.contains("fb.watch") -> FACEBOOK
                else -> GENERIC
            }
        }
    }
}

/**
 * Retorna a string de formato yt-dlp apropriada para cada plataforma e qualidade
 */
fun getFormatString(platform: Platform, format: DownloadFormat): String {
    return when (platform) {
        Platform.YOUTUBE -> {
            // YouTube suporta seleção detalhada de formato
            when (format) {
                DownloadFormat.VIDEO_BEST -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                DownloadFormat.VIDEO_1080P -> "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]"
                DownloadFormat.VIDEO_720P -> "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]"
                DownloadFormat.VIDEO_480P -> "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480]"
                DownloadFormat.AUDIO_ONLY -> "bestaudio[ext=m4a]/bestaudio"
            }
        }
        Platform.INSTAGRAM -> {
            // Instagram geralmente tem formato único - usar formato simples
            when (format) {
                DownloadFormat.AUDIO_ONLY -> "bestaudio/best"
                else -> "best"  // Instagram não suporta seleção de qualidade
            }
        }
        Platform.TWITTER -> {
            // Twitter/X tem formatos limitados
            when (format) {
                DownloadFormat.VIDEO_BEST -> "best"
                DownloadFormat.VIDEO_1080P -> "best[height<=1080]/best"
                DownloadFormat.VIDEO_720P -> "best[height<=720]/best"
                DownloadFormat.VIDEO_480P -> "best[height<=480]/best"
                DownloadFormat.AUDIO_ONLY -> "bestaudio/best"
            }
        }
        Platform.TIKTOK -> {
            // TikTok geralmente tem apenas um formato de vídeo
            when (format) {
                DownloadFormat.AUDIO_ONLY -> "bestaudio/best"
                else -> "best"
            }
        }
        Platform.VIMEO -> {
            // Vimeo suporta múltiplas qualidades
            when (format) {
                DownloadFormat.VIDEO_BEST -> "bestvideo+bestaudio/best"
                DownloadFormat.VIDEO_1080P -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                DownloadFormat.VIDEO_720P -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                DownloadFormat.VIDEO_480P -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                DownloadFormat.AUDIO_ONLY -> "bestaudio"
            }
        }
        Platform.FACEBOOK -> {
            // Facebook tem formatos limitados
            when (format) {
                DownloadFormat.VIDEO_BEST -> "best"
                DownloadFormat.VIDEO_1080P -> "best[height<=1080]/best"
                DownloadFormat.VIDEO_720P -> "best[height<=720]/best"
                DownloadFormat.VIDEO_480P -> "best[height<=480]/best"
                DownloadFormat.AUDIO_ONLY -> "bestaudio/best"
            }
        }
        Platform.GENERIC -> {
            // Formato genérico que funciona na maioria dos sites
            when (format) {
                DownloadFormat.VIDEO_BEST -> "bestvideo+bestaudio/best"
                DownloadFormat.VIDEO_1080P -> "best[height<=1080]/bestvideo[height<=1080]+bestaudio/best"
                DownloadFormat.VIDEO_720P -> "best[height<=720]/bestvideo[height<=720]+bestaudio/best"
                DownloadFormat.VIDEO_480P -> "best[height<=480]/bestvideo[height<=480]+bestaudio/best"
                DownloadFormat.AUDIO_ONLY -> "bestaudio/best"
            }
        }
    }
}
