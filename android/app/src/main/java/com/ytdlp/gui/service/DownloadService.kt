package com.ytdlp.gui.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.ytdlp.gui.MainActivity
import com.ytdlp.gui.R
import kotlinx.coroutines.*
import java.io.File

class DownloadService : Service() {
    
    companion object {
        const val CHANNEL_ID = "ytdlp_downloads"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_DOWNLOAD = "com.ytdlp.gui.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.ytdlp.gui.CANCEL_DOWNLOAD"
        
        const val EXTRA_URL = "url"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COOKIES_PATH = "cookies_path"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null
    private var isCancelled = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val format = intent.getStringExtra(EXTRA_FORMAT) ?: "bestvideo+bestaudio/best"
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Vídeo"
                val cookiesPath = intent.getStringExtra(EXTRA_COOKIES_PATH)
                
                startForeground(NOTIFICATION_ID, createNotification(title, 0))
                startDownload(url, format, title, cookiesPath)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                isCancelled = true
                currentJob?.cancel()
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startDownload(url: String, format: String, title: String, cookiesPath: String?) {
        currentJob = serviceScope.launch {
            try {
                val downloadDir = File(getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val outputTemplate = "${downloadDir.absolutePath}/%(title)s.%(ext)s"
                
                val request = YoutubeDLRequest(url).apply {
                    addOption("-f", format)
                    addOption("-o", outputTemplate)
                    addOption("--no-mtime")
                    
                    if (!cookiesPath.isNullOrEmpty() && File(cookiesPath).exists()) {
                        addOption("--cookies", cookiesPath)
                    }
                }
                
                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                    if (isCancelled) return@execute
                    
                    val progressInt = progress.toInt().coerceIn(0, 100)
                    updateNotification(title, progressInt)
                }
                
                if (!isCancelled) {
                    showCompletedNotification(title)
                }
                
            } catch (e: Exception) {
                if (!isCancelled) {
                    showErrorNotification(title, e.message ?: "Erro desconhecido")
                }
            } finally {
                stopSelf()
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(title: String, progress: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_downloading, title))
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
    
    private fun updateNotification(title: String, progress: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(title, progress))
    }
    
    private fun showCompletedNotification(title: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_completed, title))
            .setContentText("Download concluído")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        
        manager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    private fun showErrorNotification(title: String, error: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_failed, title))
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        
        manager.notify(NOTIFICATION_ID + 2, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
