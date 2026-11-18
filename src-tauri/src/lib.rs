use serde::{Deserialize, Serialize};
use std::process::{Command, Stdio};
use std::io::{BufRead, BufReader};
use std::env;
use std::fs;
use std::io::Write;
use std::path::PathBuf;
use tauri::{Emitter, AppHandle, async_runtime};

#[cfg(target_os = "windows")]
use std::os::windows::process::CommandExt;

// Flag para esconder janela do console no Windows
#[cfg(target_os = "windows")]
const CREATE_NO_WINDOW: u32 = 0x08000000;

// Configurar comando para esconder janela no Windows
#[cfg(target_os = "windows")]
fn configure_command(cmd: &mut Command) -> &mut Command {
    cmd.creation_flags(CREATE_NO_WINDOW)
}

#[cfg(not(target_os = "windows"))]
fn configure_command(cmd: &mut Command) -> &mut Command {
    cmd
}

// Detectar a fonte da URL (YouTube, Instagram, Twitter, Vimeo, ou outros)
#[derive(Debug, Clone, PartialEq)]
enum VideoSource {
    YouTube,
    Instagram,
    Twitter,
    Vimeo,
    Other,
}

fn detect_source(url: &str) -> VideoSource {
    let url_lower = url.to_lowercase();
    
    if url_lower.contains("youtube.com") || url_lower.contains("youtu.be") {
        VideoSource::YouTube
    } else if url_lower.contains("instagram.com") || url_lower.contains("instagr.am") {
        VideoSource::Instagram
    } else if url_lower.contains("twitter.com") || url_lower.contains("x.com") || url_lower.contains("t.co") {
        VideoSource::Twitter
    } else if url_lower.contains("vimeo.com") {
        VideoSource::Vimeo
    } else {
        VideoSource::Other
    }
}

// Obter string de formato apropriada para cada fonte e qualidade
fn get_format_string(source: &VideoSource, quality: &str) -> String {
    match source {
        VideoSource::YouTube => {
            // YouTube suporta formatos separados de vídeo/áudio
            match quality {
                "best" => "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best".to_string(),
                "1080p" => "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]".to_string(),
                "720p" => "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]".to_string(),
                "480p" => "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480]".to_string(),
                "360p" => "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=360]+bestaudio/best[height<=360]".to_string(),
                _ => "best".to_string(),
            }
        },
        VideoSource::Instagram => {
            // Instagram geralmente tem formatos combinados, usar fallbacks mais flexíveis
            match quality {
                "best" => "best[ext=mp4]/best".to_string(),
                "1080p" => "best[height<=1080][ext=mp4]/best[height<=1080]/best".to_string(),
                "720p" => "best[height<=720][ext=mp4]/best[height<=720]/best".to_string(),
                "480p" => "best[height<=480][ext=mp4]/best[height<=480]/best".to_string(),
                "360p" => "best[height<=360][ext=mp4]/best[height<=360]/best".to_string(),
                _ => "best".to_string(),
            }
        },
        VideoSource::Twitter => {
            // Twitter/X também usa formatos combinados
            match quality {
                "best" => "best[ext=mp4]/best".to_string(),
                "1080p" => "best[height<=1080][ext=mp4]/best[height<=1080]/best".to_string(),
                "720p" => "best[height<=720][ext=mp4]/best[height<=720]/best".to_string(),
                "480p" => "best[height<=480][ext=mp4]/best[height<=480]/best".to_string(),
                "360p" => "best[height<=360][ext=mp4]/best[height<=360]/best".to_string(),
                _ => "best".to_string(),
            }
        },
        VideoSource::Vimeo => {
            // Vimeo suporta formatos separados como YouTube
            match quality {
                "best" => "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best".to_string(),
                "1080p" => "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]".to_string(),
                "720p" => "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]".to_string(),
                "480p" => "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480]".to_string(),
                "360p" => "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=360]+bestaudio/best[height<=360]".to_string(),
                _ => "best".to_string(),
            }
        },
        VideoSource::Other => {
            // Formato genérico e flexível para outras fontes
            match quality {
                "best" => "best[ext=mp4]/best".to_string(),
                "1080p" => "best[height<=1080]/best".to_string(),
                "720p" => "best[height<=720]/best".to_string(),
                "480p" => "best[height<=480]/best".to_string(),
                "360p" => "best[height<=360]/best".to_string(),
                _ => "best".to_string(),
            }
        },
    }
}

// Pasta onde ficam as ferramentas do app
fn get_app_tools_dir() -> PathBuf {
    let app_data = if cfg!(target_os = "windows") {
        env::var("LOCALAPPDATA").unwrap_or_else(|_| env::var("USERPROFILE").unwrap_or_default())
    } else {
        env::var("HOME").unwrap_or_default()
    };
    
    let tools_dir = PathBuf::from(app_data).join("ytdlp-tauri").join("tools");
    
    // Criar pasta se não existir
    if !tools_dir.exists() {
        let _ = fs::create_dir_all(&tools_dir);
    }
    
    tools_dir
}

fn get_ytdlp_exe_path() -> PathBuf {
    let tools_dir = get_app_tools_dir();
    if cfg!(target_os = "windows") {
        tools_dir.join("yt-dlp.exe")
    } else {
        tools_dir.join("yt-dlp")
    }
}

#[derive(Serialize, Deserialize)]
pub struct ToolsStatus {
    ytdlp_installed: bool,
    ytdlp_version: Option<String>,
    ytdlp_latest_version: Option<String>,
    ytdlp_update_available: bool,
    ffmpeg_installed: bool,
    ffmpeg_version: Option<String>,
}

#[tauri::command]
async fn check_tools() -> Result<ToolsStatus, String> {
    let ytdlp_path = get_ytdlp_exe_path();
    let tools_dir = get_app_tools_dir();
    let ffmpeg_exe = tools_dir.join("ffmpeg.exe");

    let mut status = ToolsStatus {
        ytdlp_installed: false,
        ytdlp_version: None,
        ytdlp_latest_version: None,
        ytdlp_update_available: false,
        ffmpeg_installed: false,
        ffmpeg_version: None,
    };

    // Verificar yt-dlp instalado
    if ytdlp_path.exists() {
        let mut cmd = Command::new(&ytdlp_path);
        cmd.arg("--version");
        configure_command(&mut cmd);
        if let Ok(output) = cmd.output() {
            if output.status.success() {
                status.ytdlp_installed = true;
                status.ytdlp_version = Some(String::from_utf8_lossy(&output.stdout).trim().to_string());
            }
        }
    }

    // Verificar versão mais recente do yt-dlp (via GitHub API)
    if let Ok(latest) = get_ytdlp_latest_version().await {
        status.ytdlp_latest_version = Some(latest.clone());
        
        // Comparar versões se instalado
        if let Some(ref current) = status.ytdlp_version {
            // Remove prefixos como "yt-dlp " se existir
            let current_clean = current.trim();
            let latest_clean = latest.trim();
            
            if current_clean != latest_clean {
                status.ytdlp_update_available = true;
            }
        }
    }

    // Verificar FFmpeg
    if ffmpeg_exe.exists() {
        let mut cmd = Command::new(&ffmpeg_exe);
        cmd.arg("-version");
        configure_command(&mut cmd);
        if let Ok(output) = cmd.output() {
            if output.status.success() {
                let version_str = String::from_utf8_lossy(&output.stdout);
                let version = version_str.lines().next().unwrap_or("").to_string();
                status.ffmpeg_installed = true;
                status.ffmpeg_version = Some(version);
            }
        }
    }

    Ok(status)
}

// Função auxiliar para obter versão mais recente do yt-dlp
async fn get_ytdlp_latest_version() -> Result<String, String> {
    let client = reqwest::Client::new();
    let response = client
        .get("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
        .header("User-Agent", "ytdlp-tauri")
        .send()
        .await
        .map_err(|e| format!("Erro ao verificar atualização: {}", e))?;

    let json: serde_json::Value = response
        .json()
        .await
        .map_err(|e| format!("Erro ao parsear resposta: {}", e))?;

    json["tag_name"]
        .as_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Versão não encontrada".to_string())
}

#[tauri::command]
async fn update_ytdlp() -> Result<String, String> {
    // Simplesmente re-baixa a versão mais recente
    download_ytdlp().await?;
    
    // Retorna a nova versão
    let ytdlp_path = get_ytdlp_exe_path();
    let mut cmd = Command::new(&ytdlp_path);
    cmd.arg("--version");
    configure_command(&mut cmd);
    
    if let Ok(output) = cmd.output() {
        if output.status.success() {
            return Ok(String::from_utf8_lossy(&output.stdout).trim().to_string());
        }
    }
    
    Ok("Atualizado".to_string())
}

#[tauri::command]
async fn download_ytdlp() -> Result<(), String> {
    let ytdlp_path = get_ytdlp_exe_path();
    
    // URL do yt-dlp para Windows
    let url = if cfg!(target_os = "windows") {
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
    } else if cfg!(target_os = "macos") {
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos"
    } else {
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
    };

    // Baixar arquivo
    let response = reqwest::get(url)
        .await
        .map_err(|e| format!("Erro ao baixar yt-dlp: {}", e))?;

    let bytes = response.bytes()
        .await
        .map_err(|e| format!("Erro ao ler dados: {}", e))?;

    // Salvar arquivo
    let mut file = fs::File::create(&ytdlp_path)
        .map_err(|e| format!("Erro ao criar arquivo: {}", e))?;
    
    file.write_all(&bytes)
        .map_err(|e| format!("Erro ao salvar arquivo: {}", e))?;

    // No Linux/Mac, dar permissão de execução
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mut perms = fs::metadata(&ytdlp_path)
            .map_err(|e| format!("Erro ao ler permissões: {}", e))?
            .permissions();
        perms.set_mode(0o755);
        fs::set_permissions(&ytdlp_path, perms)
            .map_err(|e| format!("Erro ao definir permissões: {}", e))?;
    }

    Ok(())
}

#[tauri::command]
async fn download_ffmpeg() -> Result<(), String> {
    let tools_dir = get_app_tools_dir();
    let ffmpeg_exe = tools_dir.join("ffmpeg.exe");
    let ffprobe_exe = tools_dir.join("ffprobe.exe");
    let zip_path = tools_dir.join("ffmpeg.zip");

    // URL do FFmpeg BtbN (versão mais leve, ~30MB)
    // Só baixamos ffmpeg.exe e ffprobe.exe que são necessários para yt-dlp
    let url = if cfg!(target_os = "windows") {
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip"
    } else {
        return Err("Download automático de FFmpeg só suportado no Windows por enquanto".to_string());
    };

    // Cliente com timeout maior para downloads grandes
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(300)) // 5 minutos
        .build()
        .map_err(|e| format!("Erro ao criar cliente HTTP: {}", e))?;

    // Baixar arquivo
    let response = client.get(url)
        .send()
        .await
        .map_err(|e| format!("Erro ao baixar FFmpeg: {}", e))?;

    if !response.status().is_success() {
        return Err(format!("Erro HTTP: {}", response.status()));
    }

    let bytes = response.bytes()
        .await
        .map_err(|e| format!("Erro ao ler dados: {}", e))?;

    // Salvar ZIP
    let mut file = fs::File::create(&zip_path)
        .map_err(|e| format!("Erro ao criar arquivo: {}", e))?;
    
    file.write_all(&bytes)
        .map_err(|e| format!("Erro ao salvar arquivo: {}", e))?;

    drop(file); // Fechar arquivo antes de abrir para leitura

    // Extrair apenas ffmpeg.exe e ffprobe.exe
    let file = fs::File::open(&zip_path)
        .map_err(|e| format!("Erro ao abrir ZIP: {}", e))?;
    
    let mut archive = zip::ZipArchive::new(file)
        .map_err(|e| format!("Erro ao ler ZIP: {}", e))?;

    let mut found_ffmpeg = false;
    let mut found_ffprobe = false;

    for i in 0..archive.len() {
        let mut file = archive.by_index(i)
            .map_err(|e| format!("Erro ao extrair: {}", e))?;
        
        let name = file.name().to_string();
        
        // Extrair apenas os executáveis que precisamos
        if name.ends_with("bin/ffmpeg.exe") {
            let mut outfile = fs::File::create(&ffmpeg_exe)
                .map_err(|e| format!("Erro ao criar ffmpeg.exe: {}", e))?;
            std::io::copy(&mut file, &mut outfile)
                .map_err(|e| format!("Erro ao copiar ffmpeg.exe: {}", e))?;
            found_ffmpeg = true;
        } else if name.ends_with("bin/ffprobe.exe") {
            let mut outfile = fs::File::create(&ffprobe_exe)
                .map_err(|e| format!("Erro ao criar ffprobe.exe: {}", e))?;
            std::io::copy(&mut file, &mut outfile)
                .map_err(|e| format!("Erro ao copiar ffprobe.exe: {}", e))?;
            found_ffprobe = true;
        }
        
        if found_ffmpeg && found_ffprobe {
            break;
        }
    }

    // Limpar ZIP
    let _ = fs::remove_file(&zip_path);

    if !found_ffmpeg || !found_ffprobe {
        return Err("Não foi possível encontrar ffmpeg.exe e ffprobe.exe no ZIP".to_string());
    }

    Ok(())
}

#[derive(Serialize, Deserialize)]
pub struct ProtectionResult {
    needs_auth: bool,
    error: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct CookiesResult {
    valid: bool,
    error: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct VideoItem {
    id: String,
    title: String,
    url: String,
    duration: Option<u64>,
    channel: Option<String>,
    thumbnail: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct AnalyzeResult {
    #[serde(rename = "type")]
    content_type: String,
    title: Option<String>,
    video: Option<VideoItem>,
    items: Option<Vec<VideoItem>>,
}

#[derive(Serialize, Deserialize)]
pub struct FormatsResult {
    video: Vec<String>,
    audio: Vec<String>,
}

#[derive(Serialize, Deserialize)]
pub struct DownloadPaths {
    default: String,
    recent: Vec<String>,
}

fn get_ytdlp_path() -> String {
    // Primeiro verifica na pasta do app
    let app_ytdlp = get_ytdlp_exe_path();
    if app_ytdlp.exists() {
        return app_ytdlp.to_string_lossy().to_string();
    }
    
    // Fallback: tenta encontrar no PATH ou em local específico
    if cfg!(target_os = "windows") {
        // Verificar se existe no PATH
        let mut cmd = Command::new("where");
        cmd.arg("yt-dlp");
        configure_command(&mut cmd);
        if let Ok(output) = cmd.output() {
            if output.status.success() {
                return "yt-dlp".to_string();
            }
        }
        // Caminho comum no Windows
        let user_path = env::var("USERPROFILE").unwrap_or_default();
        let common_paths = vec![
            format!("{}\\yt-dlp.exe", user_path),
            format!("{}\\Downloads\\yt-dlp.exe", user_path),
            "C:\\yt-dlp\\yt-dlp.exe".to_string(),
        ];
        for path in common_paths {
            if std::path::Path::new(&path).exists() {
                return path;
            }
        }
    }
    "yt-dlp".to_string()
}

fn get_ffmpeg_path() -> String {
    // Retorna o caminho do ffmpeg instalado pelo app
    let tools_dir = get_app_tools_dir();
    let ffmpeg_exe = tools_dir.join("ffmpeg.exe");
    
    if ffmpeg_exe.exists() {
        return ffmpeg_exe.to_string_lossy().to_string();
    }
    
    // Fallback para ffmpeg no PATH
    "ffmpeg".to_string()
}

#[tauri::command]
async fn check_url_protection(url: String) -> Result<ProtectionResult, String> {
    let ytdlp = get_ytdlp_path();
    
    // Usar canal para receber resultado de thread separada
    let (tx, rx) = std::sync::mpsc::channel();
    
    std::thread::spawn(move || {
        let mut cmd = Command::new(&ytdlp);
        // Verificar os primeiros 5 itens da playlist para detectar se algum precisa de auth
        cmd.args(&[
            "--simulate",
            "--no-warnings",
            "--ignore-errors",
            "--socket-timeout", "10",
            "--playlist-items", "1:5",
            "--flat-playlist",
            "-j",
            &url
        ]);
        configure_command(&mut cmd);
        let result = cmd.output();
        let _ = tx.send(result);
    });
    
    // Aguardar resultado sem bloquear a UI usando yield
    let output = loop {
        match rx.try_recv() {
            Ok(result) => break result,
            Err(std::sync::mpsc::TryRecvError::Empty) => {
                // Yield para não bloquear - permite UI processar eventos
                async_runtime::spawn(async {}).await.ok();
                std::thread::sleep(std::time::Duration::from_millis(50));
            }
            Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                return Err("Thread desconectada".to_string());
            }
        }
    }.map_err(|e| format!("Erro ao executar yt-dlp: {}. Verifique se está instalado.", e))?;

    let stderr = String::from_utf8_lossy(&output.stderr);
    let stdout = String::from_utf8_lossy(&output.stdout);
    
    // Verificar se precisa de login (no stderr ou stdout)
    let combined_output = format!("{} {}", stderr, stdout);
    
    if combined_output.contains("Sign in to confirm") || 
       combined_output.contains("Video unavailable") ||
       combined_output.contains("Private video") ||
       combined_output.contains("members-only") ||
       combined_output.contains("requires authentication") ||
       combined_output.contains("login required") ||
       combined_output.contains("This video is available to this channel") {
        return Ok(ProtectionResult {
            needs_auth: true,
            error: Some(stderr.to_string()),
        });
    }

    if output.status.success() {
        Ok(ProtectionResult {
            needs_auth: false,
            error: None,
        })
    } else {
        // Pode ser outro tipo de erro
        Ok(ProtectionResult {
            needs_auth: false,
            error: Some(stderr.to_string()),
        })
    }
}

#[tauri::command]
async fn verify_cookies(url: String, cookies_path: String) -> Result<CookiesResult, String> {
    let ytdlp = get_ytdlp_path();
    
    let (tx, rx) = std::sync::mpsc::channel();
    
    std::thread::spawn(move || {
        let mut cmd = Command::new(&ytdlp);
        cmd.args(&[
            "--simulate",
            "--cookies", &cookies_path,
            "--socket-timeout", "15",
            "--playlist-items", "1",
            "-j",
            &url
        ]);
        configure_command(&mut cmd);
        let result = cmd.output();
        let _ = tx.send(result);
    });
    
    let output = loop {
        match rx.try_recv() {
            Ok(result) => break result,
            Err(std::sync::mpsc::TryRecvError::Empty) => {
                async_runtime::spawn(async {}).await.ok();
                std::thread::sleep(std::time::Duration::from_millis(50));
            }
            Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                return Err("Thread desconectada".to_string());
            }
        }
    }.map_err(|e| format!("Erro ao executar yt-dlp: {}", e))?;

    if output.status.success() {
        Ok(CookiesResult {
            valid: true,
            error: None,
        })
    } else {
        let stderr = String::from_utf8_lossy(&output.stderr);
        Ok(CookiesResult {
            valid: false,
            error: Some(stderr.to_string()),
        })
    }
}

#[derive(Serialize, Deserialize)]
pub struct FirefoxStatus {
    installed: bool,
    path: Option<String>,
}

#[tauri::command]
fn check_firefox_installed() -> Result<FirefoxStatus, String> {
    // Verificar se o Firefox está instalado
    let possible_paths = if cfg!(target_os = "windows") {
        vec![
            "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
            "C:\\Program Files (x86)\\Mozilla Firefox\\firefox.exe",
        ]
    } else if cfg!(target_os = "macos") {
        vec!["/Applications/Firefox.app/Contents/MacOS/firefox"]
    } else {
        vec!["/usr/bin/firefox", "/usr/bin/firefox-esr"]
    };

    for path in possible_paths {
        if std::path::Path::new(path).exists() {
            return Ok(FirefoxStatus {
                installed: true,
                path: Some(path.to_string()),
            });
        }
    }

    // Tentar no PATH
    let firefox_cmd = if cfg!(target_os = "windows") { "firefox.exe" } else { "firefox" };
    let mut cmd = Command::new("where");
    if !cfg!(target_os = "windows") {
        cmd = Command::new("which");
    }
    cmd.arg(firefox_cmd);
    configure_command(&mut cmd);
    
    if let Ok(output) = cmd.output() {
        if output.status.success() {
            let path = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !path.is_empty() {
                return Ok(FirefoxStatus {
                    installed: true,
                    path: Some(path),
                });
            }
        }
    }

    Ok(FirefoxStatus {
        installed: false,
        path: None,
    })
}

#[tauri::command]
fn open_firefox_youtube() -> Result<(), String> {
    // Abrir Firefox com YouTube para o usuário fazer login
    let firefox_result = check_firefox_installed()?;
    
    if let Some(firefox_path) = firefox_result.path {
        let mut cmd = Command::new(&firefox_path);
        cmd.arg("https://www.youtube.com/");
        configure_command(&mut cmd);
        cmd.spawn().map_err(|e| format!("Erro ao abrir Firefox: {}", e))?;
        Ok(())
    } else {
        Err("Firefox não encontrado".to_string())
    }
}

#[tauri::command]
async fn check_firefox_youtube_cookies(url: String) -> Result<CookiesResult, String> {
    // Usar yt-dlp com --cookies-from-browser firefox para verificar se tem cookies válidos
    let ytdlp = get_ytdlp_path();
    
    let (tx, rx) = std::sync::mpsc::channel();
    
    std::thread::spawn(move || {
        let mut cmd = Command::new(&ytdlp);
        cmd.args(&[
            "--simulate",
            "--cookies-from-browser", "firefox",
            "--socket-timeout", "15",
            "--playlist-items", "1",
            "-j",
            &url
        ]);
        configure_command(&mut cmd);
        let result = cmd.output();
        let _ = tx.send(result);
    });
    
    let output = loop {
        match rx.try_recv() {
            Ok(result) => break result,
            Err(std::sync::mpsc::TryRecvError::Empty) => {
                async_runtime::spawn(async {}).await.ok();
                std::thread::sleep(std::time::Duration::from_millis(50));
            }
            Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                return Err("Thread desconectada".to_string());
            }
        }
    }.map_err(|e| format!("Erro ao executar yt-dlp: {}", e))?;

    if output.status.success() {
        Ok(CookiesResult {
            valid: true,
            error: None,
        })
    } else {
        let stderr = String::from_utf8_lossy(&output.stderr);
        // Verificar se ainda precisa de auth
        if stderr.contains("Sign in") || stderr.contains("Private") || stderr.contains("unavailable") {
            Ok(CookiesResult {
                valid: false,
                error: Some("Faça login no YouTube pelo Firefox e tente novamente".to_string()),
            })
        } else {
            Ok(CookiesResult {
                valid: false,
                error: Some(stderr.to_string()),
            })
        }
    }
}

#[tauri::command]
async fn analyze_url(url: String, radio_limit: u32, cookies_path: Option<String>, use_browser_cookies: Option<bool>) -> Result<AnalyzeResult, String> {
    let ytdlp = get_ytdlp_path();
    
    let mut args = vec![
        "--flat-playlist".to_string(),
        "--no-warnings".to_string(),
        "--socket-timeout".to_string(),
        "30".to_string(),
        "-J".to_string(),
    ];

    // Cookies - usar do Firefox ou arquivo
    if use_browser_cookies.unwrap_or(false) {
        args.push("--cookies-from-browser".to_string());
        args.push("firefox".to_string());
    } else if let Some(ref cookies) = cookies_path {
        args.push("--cookies".to_string());
        args.push(cookies.clone());
    }

    // Limite para rádios
    let url_clone = url.clone();
    if url.contains("list=RD") || url.contains("start_radio=1") {
        args.push("--playlist-end".to_string());
        args.push(radio_limit.to_string());
    }

    args.push(url.clone());

    // Executar em thread separada com canal
    let (tx, rx) = std::sync::mpsc::channel();
    
    std::thread::spawn(move || {
        let mut cmd = Command::new(&ytdlp);
        cmd.args(&args);
        configure_command(&mut cmd);
        let result = cmd.output();
        let _ = tx.send(result);
    });
    
    let output = loop {
        match rx.try_recv() {
            Ok(result) => break result,
            Err(std::sync::mpsc::TryRecvError::Empty) => {
                async_runtime::spawn(async {}).await.ok();
                std::thread::sleep(std::time::Duration::from_millis(50));
            }
            Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                return Err("Thread desconectada".to_string());
            }
        }
    }.map_err(|e| format!("Erro ao executar yt-dlp: {}", e))?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(format!("Erro ao analisar URL: {}", stderr));
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    let json: serde_json::Value = serde_json::from_str(&stdout)
        .map_err(|e| format!("Erro ao parsear JSON: {}", e))?;

    // Determinar o tipo de conteúdo
    let content_type = if json.get("entries").is_some() {
        // Verificar se é rádio
        if url_clone.contains("list=RD") || url_clone.contains("start_radio=1") {
            // Se tem um vídeo específico na URL (&v=)
            if url_clone.contains("&v=") || url_clone.contains("?v=") {
                "video_with_radio"
            } else {
                "radio"
            }
        } else if url_clone.contains("&list=") {
            "video_with_playlist"
        } else {
            "playlist"
        }
    } else {
        "video"
    };

    let mut items: Vec<VideoItem> = Vec::new();
    let mut single_video: Option<VideoItem> = None;
    let mut title: Option<String> = None;

    if let Some(entries) = json.get("entries").and_then(|e| e.as_array()) {
        title = json.get("title").and_then(|t| t.as_str()).map(|s| s.to_string());
        
        for entry in entries {
            if let Some(id) = entry.get("id").and_then(|i| i.as_str()) {
                // Tentar obter a URL real do item, senão construir baseado na fonte
                let item_url = entry.get("url").and_then(|u| u.as_str())
                    .or_else(|| entry.get("webpage_url").and_then(|u| u.as_str()))
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| {
                        // Fallback: construir URL baseado na fonte original
                        if url_clone.contains("youtube.com") || url_clone.contains("youtu.be") {
                            format!("https://www.youtube.com/watch?v={}", id)
                        } else if url_clone.contains("instagram.com") {
                            format!("https://www.instagram.com/p/{}/", id)
                        } else if url_clone.contains("tiktok.com") {
                            format!("https://www.tiktok.com/@user/video/{}", id)
                        } else if url_clone.contains("twitter.com") || url_clone.contains("x.com") {
                            format!("https://twitter.com/i/status/{}", id)
                        } else {
                            // Para outras fontes, usar o ID como URL (yt-dlp vai resolver)
                            id.to_string()
                        }
                    });
                
                // Thumbnail: tentar pegar do item ou gerar baseado na fonte
                let thumbnail = entry.get("thumbnail").and_then(|t| t.as_str()).map(|s| s.to_string())
                    .or_else(|| {
                        if url_clone.contains("youtube.com") || url_clone.contains("youtu.be") {
                            Some(format!("https://i.ytimg.com/vi/{}/default.jpg", id))
                        } else {
                            None
                        }
                    });
                
                items.push(VideoItem {
                    id: id.to_string(),
                    title: entry.get("title").and_then(|t| t.as_str()).unwrap_or("Sem título").to_string(),
                    url: item_url,
                    duration: entry.get("duration").and_then(|d| d.as_u64()),
                    channel: entry.get("channel").and_then(|c| c.as_str()).map(|s| s.to_string())
                        .or_else(|| entry.get("uploader").and_then(|u| u.as_str()).map(|s| s.to_string())),
                    thumbnail,
                });
            }
        }
    } else {
        // Vídeo único
        if let Some(id) = json.get("id").and_then(|i| i.as_str()) {
            single_video = Some(VideoItem {
                id: id.to_string(),
                title: json.get("title").and_then(|t| t.as_str()).unwrap_or("Sem título").to_string(),
                url: url_clone.clone(),
                duration: json.get("duration").and_then(|d| d.as_u64()),
                channel: json.get("channel").and_then(|c| c.as_str()).map(|s| s.to_string()),
                thumbnail: json.get("thumbnail").and_then(|t| t.as_str()).map(|s| s.to_string()),
            });
        }
    }

    Ok(AnalyzeResult {
        content_type: content_type.to_string(),
        title,
        video: single_video,
        items: if items.is_empty() { None } else { Some(items) },
    })
}

#[tauri::command]
fn get_formats(url: String, cookies_path: Option<String>, use_browser_cookies: Option<bool>) -> Result<FormatsResult, String> {
    // Tentar obter formatos reais do yt-dlp
    let ytdlp_path = get_ytdlp_exe_path();
    
    if ytdlp_path.exists() {
        let mut cmd = Command::new(&ytdlp_path);
        cmd.args(["--list-formats", "--no-warnings", "-J"]);
        
        // Cookies - usar do Firefox ou arquivo
        if use_browser_cookies.unwrap_or(false) {
            cmd.args(["--cookies-from-browser", "firefox"]);
        } else if let Some(ref cookies) = cookies_path {
            cmd.args(["--cookies", cookies]);
        }
        
        cmd.arg(&url);
        configure_command(&mut cmd);
        
        if let Ok(output) = cmd.output() {
            if output.status.success() {
                let stdout = String::from_utf8_lossy(&output.stdout);
                
                // Parsear JSON e extrair formatos
                if let Ok(json) = serde_json::from_str::<serde_json::Value>(&stdout) {
                    let mut video_formats: Vec<String> = vec!["best".to_string()];
                    let mut audio_formats: Vec<String> = vec!["best".to_string()];
                    let mut seen_resolutions: std::collections::HashSet<String> = std::collections::HashSet::new();
                    
                    if let Some(formats) = json.get("formats").and_then(|f| f.as_array()) {
                        for format in formats {
                            // Formatos de vídeo
                            if let Some(height) = format.get("height").and_then(|h| h.as_i64()) {
                                let resolution = format!("{}p", height);
                                if !seen_resolutions.contains(&resolution) && height >= 360 {
                                    seen_resolutions.insert(resolution.clone());
                                    video_formats.push(resolution);
                                }
                            }
                            
                            // Formatos de áudio
                            if format.get("vcodec").and_then(|v| v.as_str()) == Some("none") {
                                if let Some(abr) = format.get("abr").and_then(|a| a.as_f64()) {
                                    let bitrate = format!("{}", abr as i32);
                                    if !audio_formats.contains(&bitrate) && abr >= 64.0 {
                                        audio_formats.push(bitrate);
                                    }
                                }
                            }
                        }
                    }
                    
                    // Ordenar por qualidade (maior primeiro)
                    video_formats.sort_by(|a, b| {
                        let a_num: i32 = a.trim_end_matches('p').parse().unwrap_or(0);
                        let b_num: i32 = b.trim_end_matches('p').parse().unwrap_or(0);
                        b_num.cmp(&a_num)
                    });
                    
                    audio_formats.sort_by(|a, b| {
                        let a_num: i32 = a.parse().unwrap_or(0);
                        let b_num: i32 = b.parse().unwrap_or(0);
                        b_num.cmp(&a_num)
                    });
                    
                    // Se encontrou formatos, retornar
                    if video_formats.len() > 1 || audio_formats.len() > 1 {
                        return Ok(FormatsResult {
                            video: video_formats,
                            audio: audio_formats,
                        });
                    }
                }
            }
        }
    }
    
    // Fallback: formatos padrão (sempre funcionam com yt-dlp)
    Ok(FormatsResult {
        video: vec!["best".to_string(), "1080p".to_string(), "720p".to_string(), "480p".to_string(), "360p".to_string()],
        audio: vec!["best".to_string(), "320".to_string(), "256".to_string(), "192".to_string(), "128".to_string()],
    })
}

#[tauri::command]
fn get_download_paths() -> Result<DownloadPaths, String> {
    let downloads = if cfg!(target_os = "windows") {
        let user_profile = env::var("USERPROFILE").unwrap_or_default();
        format!("{}\\Downloads", user_profile)
    } else {
        let home = env::var("HOME").unwrap_or_default();
        format!("{}/Downloads", home)
    };

    Ok(DownloadPaths {
        default: downloads,
        recent: vec![],
    })
}

#[derive(Serialize, Deserialize)]
pub struct DownloadOptions {
    url: String,
    destination: String,
    mode: String, // "video" ou "audio"
    quality: String,
    cookies_path: Option<String>,
    use_browser_cookies: Option<bool>, // Usar cookies do Firefox
    selected_items: Option<Vec<String>>, // URLs dos vídeos selecionados
}

#[derive(Serialize, Deserialize, Clone)]
pub struct DownloadProgress {
    status: String,
    progress: f32,
    speed: String,
    eta: String,
    filename: String,
    current: u32,
    total: u32,
}

#[tauri::command]
async fn start_download(app: AppHandle, options: DownloadOptions) -> Result<String, String> {
    let ytdlp = get_ytdlp_path();
    let tools_dir = get_app_tools_dir();
    let ffmpeg_location = tools_dir.to_string_lossy().to_string();
    
    // Construir argumentos base
    let mut args: Vec<String> = vec![
        "--ffmpeg-location".to_string(),
        ffmpeg_location,
        "--no-warnings".to_string(),
        "--newline".to_string(),
        "--progress".to_string(),
        "--encoding".to_string(),
        "utf-8".to_string(),
        "--ignore-errors".to_string(),
        "--no-abort-on-error".to_string(),
        "--extractor-retries".to_string(),
        "3".to_string(),
        "--fragment-retries".to_string(),
        "3".to_string(),
        "--retry-sleep".to_string(),
        "5".to_string(),
    ];
    
    // Configurar saída baseado no modo
    if options.mode == "audio" {
        args.extend(vec![
            "-x".to_string(),
            "--audio-format".to_string(),
            "mp3".to_string(),
            "--audio-quality".to_string(),
            if options.quality == "best" { "0".to_string() } else { options.quality.clone() },
        ]);
    } else {
        // Vídeo - detectar fonte e usar formato apropriado
        let source = detect_source(&options.url);
        let format_str = get_format_string(&source, &options.quality);
        
        args.extend(vec![
            "-f".to_string(),
            format_str,
        ]);
        
        // Merge output format - YouTube e Vimeo precisam merge, outros não
        if source == VideoSource::YouTube || source == VideoSource::Vimeo {
            args.extend(vec![
                "--merge-output-format".to_string(),
                "mp4".to_string(),
            ]);
        }
    }
    
    // Cookies - usar do Firefox ou arquivo
    if options.use_browser_cookies.unwrap_or(false) {
        args.push("--cookies-from-browser".to_string());
        args.push("firefox".to_string());
    } else if let Some(ref cookies) = options.cookies_path {
        args.push("--cookies".to_string());
        args.push(cookies.clone());
    }
    
    // Template de saída
    let output_template = if cfg!(target_os = "windows") {
        format!("{}\\%(title)s.%(ext)s", options.destination)
    } else {
        format!("{}/%(title)s.%(ext)s", options.destination)
    };
    args.push("-o".to_string());
    args.push(output_template);
    
    // Coletar URLs para download
    let mut urls_to_download: Vec<String> = Vec::new();
    
    if let Some(ref items) = options.selected_items {
        if !items.is_empty() {
            // Usar as URLs diretamente (já vem completas do frontend)
            for item_url in items {
                urls_to_download.push(item_url.clone());
            }
        } else {
            urls_to_download.push(options.url.clone());
        }
    } else {
        urls_to_download.push(options.url.clone());
    }
    
    let total_items = urls_to_download.len() as u32;
    
    // Se tiver muitas URLs (>50), usar arquivo de batch para evitar erro OS 206
    // (limite de tamanho da linha de comando no Windows)
    let batch_file_path: Option<PathBuf> = if urls_to_download.len() > 50 {
        let tools_dir = get_app_tools_dir();
        let batch_path = tools_dir.join("download_batch.txt");
        
        // Criar arquivo com uma URL por linha
        let mut batch_file = fs::File::create(&batch_path)
            .map_err(|e| format!("Erro ao criar arquivo de batch: {}", e))?;
        
        for url in &urls_to_download {
            writeln!(batch_file, "{}", url)
                .map_err(|e| format!("Erro ao escrever no arquivo de batch: {}", e))?;
        }
        
        // Usar -a para ler URLs do arquivo
        args.push("-a".to_string());
        args.push(batch_path.to_string_lossy().to_string());
        
        Some(batch_path)
    } else {
        // Poucas URLs, adicionar diretamente nos argumentos
        for url in &urls_to_download {
            args.push(url.clone());
        }
        None
    };
    
    // Emitir progresso inicial
    let _ = app.emit("download-progress", DownloadProgress {
        status: "starting".to_string(),
        progress: 0.0,
        speed: "".to_string(),
        eta: "".to_string(),
        filename: "Iniciando download...".to_string(),
        current: 0,
        total: total_items,
    });
    
    // Executar com captura de output em tempo real
    let mut cmd = Command::new(&ytdlp);
    cmd.args(&args)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    configure_command(&mut cmd);
    let mut child = cmd.spawn()
        .map_err(|e| format!("Erro ao executar yt-dlp: {}", e))?;
    
    let stdout = child.stdout.take().ok_or("Falha ao capturar stdout")?;
    let reader = BufReader::new(stdout);
    
    let mut current_item: u32 = 1;
    let mut last_filename = String::new();
    
    for line in reader.lines() {
        if let Ok(line) = line {
            // Debug: mostrar todas as linhas
            // eprintln!("LINE: {}", line);
            
            // Formato padrão: [download]  45.2% of 10.50MiB at 2.50MiB/s ETA 00:05
            if line.contains("[download]") && line.contains("%") {
                // Extrair porcentagem
                if let Some(percent_match) = line.split_whitespace()
                    .find(|s| s.ends_with('%')) {
                    let percent_str = percent_match.trim_end_matches('%');
                    let progress: f32 = percent_str.parse().unwrap_or(0.0);
                    
                    // Extrair velocidade (formato: X.XXMiB/s ou similar)
                    let speed = line.split_whitespace()
                        .find(|s| s.contains("/s"))
                        .unwrap_or("")
                        .to_string();
                    
                    // Extrair ETA (formato: ETA XX:XX ou ETA XX:XX:XX)
                    let eta = if let Some(eta_idx) = line.find("ETA") {
                        line[eta_idx + 3..].trim().split_whitespace().next().unwrap_or("").to_string()
                    } else {
                        "".to_string()
                    };
                    
                    let _ = app.emit("download-progress", DownloadProgress {
                        status: "downloading".to_string(),
                        progress,
                        speed,
                        eta,
                        filename: last_filename.clone(),
                        current: current_item,
                        total: total_items,
                    });
                }
            } else if line.contains("[download] Destination:") {
                // Novo arquivo começando
                if let Some(dest) = line.split("Destination:").nth(1) {
                    let full_path = dest.trim().to_string();
                    if !full_path.is_empty() {
                        // Se tiver nome diferente, é um novo item
                        let new_name = std::path::Path::new(&full_path)
                            .file_name()
                            .map(|f| f.to_string_lossy().to_string())
                            .unwrap_or(full_path.clone());
                        
                        if !last_filename.is_empty() && new_name != last_filename {
                            current_item += 1;
                        }
                        last_filename = new_name;
                        
                        let _ = app.emit("download-progress", DownloadProgress {
                            status: "downloading".to_string(),
                            progress: 0.0,
                            speed: "".to_string(),
                            eta: "".to_string(),
                            filename: last_filename.clone(),
                            current: current_item,
                            total: total_items,
                        });
                    }
                }
            } else if line.contains("[download] Downloading item") || line.contains("[download] Downloading video") {
                // Detectar início de novo item em playlist
                if let Some(num) = line.split_whitespace()
                    .filter_map(|s| s.parse::<u32>().ok())
                    .next() {
                    current_item = num;
                }
            } else if line.contains("[Merger]") || line.contains("Merging formats") {
                let _ = app.emit("download-progress", DownloadProgress {
                    status: "merging".to_string(),
                    progress: 99.0,
                    speed: "".to_string(),
                    eta: "".to_string(),
                    filename: format!("Mesclando: {}", last_filename),
                    current: current_item,
                    total: total_items,
                });
            } else if line.contains("[ExtractAudio]") {
                let _ = app.emit("download-progress", DownloadProgress {
                    status: "converting".to_string(),
                    progress: 99.0,
                    speed: "".to_string(),
                    eta: "".to_string(),
                    filename: format!("Convertendo: {}", last_filename),
                    current: current_item,
                    total: total_items,
                });
            } else if line.contains("ERROR:") || line.contains("[error]") {
                // Erro em um vídeo específico - continua para o próximo
                current_item += 1;
                let _ = app.emit("download-progress", DownloadProgress {
                    status: "skipping".to_string(),
                    progress: 0.0,
                    speed: "".to_string(),
                    eta: "".to_string(),
                    filename: format!("Pulando item com erro... ({})", current_item),
                    current: current_item,
                    total: total_items,
                });
            } else if line.contains("has already been downloaded") {
                // Vídeo já baixado - pula para o próximo
                current_item += 1;
            }
        }
    }
    
    // Aguardar conclusão
    let status = child.wait().map_err(|e| format!("Erro ao aguardar processo: {}", e))?;
    
    if status.success() {
        let _ = app.emit("download-progress", DownloadProgress {
            status: "completed".to_string(),
            progress: 100.0,
            speed: "".to_string(),
            eta: "".to_string(),
            filename: "Download concluído!".to_string(),
            current: total_items,
            total: total_items,
        });
        
        // Limpar arquivo de batch se foi usado
        if let Some(ref batch_path) = batch_file_path {
            let _ = fs::remove_file(batch_path);
        }
        
        Ok("Download concluído com sucesso!".to_string())
    } else {
        let _ = app.emit("download-progress", DownloadProgress {
            status: "error".to_string(),
            progress: 0.0,
            speed: "".to_string(),
            eta: "".to_string(),
            filename: "Erro no download".to_string(),
            current: current_item,
            total: total_items,
        });
        
        // Limpar arquivo de batch mesmo em caso de erro
        if let Some(ref batch_path) = batch_file_path {
            let _ = fs::remove_file(batch_path);
        }
        
        Err("Erro durante o download".to_string())
    }
}

#[tauri::command]
fn open_folder(path: String) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        let mut cmd = Command::new("explorer");
        cmd.arg(&path);
        configure_command(&mut cmd);
        cmd.spawn()
            .map_err(|e| format!("Erro ao abrir pasta: {}", e))?;
    }
    
    #[cfg(target_os = "macos")]
    {
        Command::new("open")
            .arg(&path)
            .spawn()
            .map_err(|e| format!("Erro ao abrir pasta: {}", e))?;
    }
    
    #[cfg(target_os = "linux")]
    {
        Command::new("xdg-open")
            .arg(&path)
            .spawn()
            .map_err(|e| format!("Erro ao abrir pasta: {}", e))?;
    }
    
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![
            check_tools,
            download_ytdlp,
            download_ffmpeg,
            update_ytdlp,
            check_url_protection,
            verify_cookies,
            check_firefox_installed,
            open_firefox_youtube,
            check_firefox_youtube_cookies,
            analyze_url,
            get_formats,
            get_download_paths,
            start_download,
            open_folder
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
