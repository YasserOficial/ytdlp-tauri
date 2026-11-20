package com.ytdlp.gui.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ytdlp.gui.data.WizardStep
import com.ytdlp.gui.ui.components.GlassButton
import com.ytdlp.gui.ui.components.GlassCard
import com.ytdlp.gui.ui.components.GlassOutlinedButton
import com.ytdlp.gui.viewmodel.MainViewModel

// Configurações de login para cada site
data class SiteAuthConfig(
    val name: String,
    val icon: String,
    val loginUrl: String,
    val cookieDomains: List<String>,
    val loginIndicators: List<String>
)

private val siteConfigs = mapOf(
    "youtube" to SiteAuthConfig(
        name = "YouTube",
        icon = "🎬",
        loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube",
        cookieDomains = listOf("youtube.com", "google.com"),
        loginIndicators = listOf("SID=", "LOGIN_INFO=", "SSID=")
    ),
    "instagram" to SiteAuthConfig(
        name = "Instagram",
        icon = "📸",
        loginUrl = "https://www.instagram.com/accounts/login/",
        cookieDomains = listOf("instagram.com"),
        loginIndicators = listOf("sessionid=", "ds_user_id=")
    ),
    "twitter" to SiteAuthConfig(
        name = "Twitter/X",
        icon = "🐦",
        loginUrl = "https://twitter.com/i/flow/login",
        cookieDomains = listOf("twitter.com", "x.com"),
        loginIndicators = listOf("auth_token=", "ct0=")
    ),
    "facebook" to SiteAuthConfig(
        name = "Facebook",
        icon = "📘",
        loginUrl = "https://www.facebook.com/login/",
        cookieDomains = listOf("facebook.com"),
        loginIndicators = listOf("c_user=", "xs=")
    ),
    "tiktok" to SiteAuthConfig(
        name = "TikTok",
        icon = "🎵",
        loginUrl = "https://www.tiktok.com/login",
        cookieDomains = listOf("tiktok.com"),
        loginIndicators = listOf("sessionid=", "sid_tt=")
    ),
    "vimeo" to SiteAuthConfig(
        name = "Vimeo",
        icon = "🎥",
        loginUrl = "https://vimeo.com/log_in",
        cookieDomains = listOf("vimeo.com"),
        loginIndicators = listOf("vuid=", "player=")
    ),
    "twitch" to SiteAuthConfig(
        name = "Twitch",
        icon = "🎮",
        loginUrl = "https://www.twitch.tv/login",
        cookieDomains = listOf("twitch.tv"),
        loginIndicators = listOf("auth-token=", "login=")
    ),
    "reddit" to SiteAuthConfig(
        name = "Reddit",
        icon = "🔴",
        loginUrl = "https://www.reddit.com/login/",
        cookieDomains = listOf("reddit.com"),
        loginIndicators = listOf("reddit_session=", "token_v2=")
    )
)

private fun detectSite(url: String): SiteAuthConfig {
    val host = try {
        Uri.parse(url).host?.lowercase() ?: ""
    } catch (e: Exception) {
        ""
    }
    
    return when {
        host.contains("youtube") || host.contains("youtu.be") || host.contains("google") -> siteConfigs["youtube"]!!
        host.contains("instagram") -> siteConfigs["instagram"]!!
        host.contains("twitter") || host.contains("x.com") -> siteConfigs["twitter"]!!
        host.contains("facebook") || host.contains("fb.com") -> siteConfigs["facebook"]!!
        host.contains("tiktok") -> siteConfigs["tiktok"]!!
        host.contains("vimeo") -> siteConfigs["vimeo"]!!
        host.contains("twitch") -> siteConfigs["twitch"]!!
        host.contains("reddit") -> siteConfigs["reddit"]!!
        else -> siteConfigs["youtube"]!! // Default
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var showWebView by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var cookies by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val error by viewModel.error.collectAsState()
    val needsAuth by viewModel.needsAuth.collectAsState()
    val currentUrl by viewModel.url.collectAsState()
    
    // Detectar o site baseado na URL
    val siteConfig = remember(currentUrl) { detectSite(currentUrl) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (!showWebView) {
            // Tela de introdução
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = siteConfig.icon,
                    fontSize = 64.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Login no ${siteConfig.name}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Mostrar mensagem de erro se houver
                if (error != null) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Para acessar este conteúdo, você precisa fazer login no ${siteConfig.name}.",
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "🔒 Seus dados ficam apenas no seu dispositivo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                GlassButton(
                    onClick = { showWebView = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${siteConfig.icon} Fazer login no ${siteConfig.name}")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                GlassOutlinedButton(
                    onClick = { 
                        viewModel.clearError()
                        viewModel.goToStep(WizardStep.URL_INPUT) 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar e voltar")
                }
            }
        } else {
            // WebView para login
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showWebView = false }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                    
                    Text(
                        text = "${siteConfig.icon} ${siteConfig.name}",
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (isLoggedIn) {
                        IconButton(
                            onClick = {
                                viewModel.saveCookies(cookies)
                                viewModel.goToStep(WizardStep.URL_INPUT)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Concluir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
                
                // WebView
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            
                            // Limpar cookies antigos
                            CookieManager.getInstance().removeAllCookies(null)
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    
                                    // Verificar cookies de todos os domínios do site
                                    val allCookies = StringBuilder()
                                    var foundLogin = false
                                    
                                    siteConfig.cookieDomains.forEach { domain ->
                                        val domainCookies = CookieManager.getInstance()
                                            .getCookie("https://www.$domain")
                                        
                                        if (domainCookies != null) {
                                            // Verificar se tem indicadores de login
                                            if (siteConfig.loginIndicators.any { domainCookies.contains(it) }) {
                                                foundLogin = true
                                            }
                                            allCookies.append(domainCookies).append("; ")
                                        }
                                    }
                                    
                                    if (foundLogin) {
                                        isLoggedIn = true
                                        cookies = convertToNetscapeCookies(allCookies.toString(), siteConfig.cookieDomains)
                                    }
                                }
                            }
                            
                            loadUrl(siteConfig.loginUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                
                // Status - Sempre visível quando logado
                if (isLoggedIn) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✅ Login no ${siteConfig.name} detectado!",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = {
                                    viewModel.saveCookies(cookies)
                                    viewModel.clearError()
                                    // Se tinha uma URL pendente, tentar novamente
                                    if (currentUrl.isNotBlank()) {
                                        viewModel.fetchInfo(context)
                                    } else {
                                        viewModel.goToStep(WizardStep.URL_INPUT)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Continuar", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun convertToNetscapeCookies(cookies: String, domains: List<String>): String {
    val sb = StringBuilder()
    sb.appendLine("# Netscape HTTP Cookie File")
    
    val primaryDomain = domains.firstOrNull() ?: "youtube.com"
    
    cookies.split(";").forEach { cookie ->
        val parts = cookie.trim().split("=", limit = 2)
        if (parts.size == 2) {
            val name = parts[0].trim()
            val value = parts[1].trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                // Adicionar para todos os domínios
                domains.forEach { domain ->
                    sb.appendLine(".$domain\tTRUE\t/\tTRUE\t0\t$name\t$value")
                }
            }
        }
    }
    
    return sb.toString()
}
