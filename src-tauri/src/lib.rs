use serde::{Deserialize, Serialize};
use std::process::{Command, Stdio};
use std::io::{BufRead, BufReader};
use std::env;
use std::fs;
use std::io::Write;
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::{Duration, Instant};
use tauri::{Emitter, AppHandle, async_runtime};

const BLOCK_COOLDOWN: Duration = Duration::from_secs(60);
static BLOCKED_UNTIL: Mutex<Option<Instant>> = Mutex::new(None);

fn text_is_block(text: &str) -> bool {
    let lower = text.to_lowercase();
    lower.contains("sign in to confirm")
        || lower.contains("not a bot")
        || lower.contains("não é um robô")
        || lower.contains("nao e um robo")
        || lower.contains("too many requests")
        || lower.contains("http error 429")
}

fn remember_block(text: &str) {
    if text_is_block(text) {
        if let Ok(mut until) = BLOCKED_UNTIL.lock() {
            *until = Some(Instant::now() + BLOCK_COOLDOWN);
        }
    }
}

fn cooldown_remaining() -> Duration {
    BLOCKED_UNTIL
        .lock()
        .ok()
        .and_then(|guard| *guard)
        .map(|until| until.saturating_duration_since(Instant::now()))
        .unwrap_or(Duration::ZERO)
}

async fn wait_out_cooldown() {
    let wait = cooldown_remaining();
    if wait.is_zero() {
        return;
    }
    let (tx, rx) = std::sync::mpsc::channel();
    std::thread::spawn(move || {
        std::thread::sleep(wait);
        let _ = tx.send(());
    });
    loop {
        match rx.try_recv() {
            Ok(_) | Err(std::sync::mpsc::TryRecvError::Disconnected) => break,
            Err(std::sync::mpsc::TryRecvError::Empty) => {
                async_runtime::spawn(async {}).await.ok();
                std::thread::sleep(Duration::from_millis(100));
            }
        }
    }
}

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

fn command_exists(name: &str) -> bool {
    let finder = if cfg!(target_os = "windows") { "where" } else { "which" };
    let mut cmd = Command::new(finder);
    cmd.arg(name);
    configure_command(&mut cmd);
    cmd.output().map(|output| output.status.success()).unwrap_or(false)
}

fn js_runtime_args() -> Vec<String> {
    let runtime = if command_exists("node") {
        "node"
    } else if command_exists("deno") {
        "deno"
    } else {
        "node"
    };
    vec!["--js-runtimes".to_string(), runtime.to_string()]
}

fn challenge_notice(text: &str) -> Option<String> {
    let line = text.lines().find(|line| {
        let lower = line.to_lowercase();
        lower.contains("challenge solving failed")
            || lower.contains("no supported javascript runtime")
            || lower.contains("js runtime")
    })?;
    Some(format!(
        "O YouTube não liberou os formatos de vídeo. O desafio JavaScript precisa do Node.js no PATH.\n{}",
        line.trim()
    ))
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
        env::var("XDG_DATA_HOME").unwrap_or_else(|_| {
            let home = env::var("HOME").unwrap_or_default();
            format!("{}/.local/share", home)
        })
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

fn ffmpeg_file_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "ffmpeg.exe"
    } else {
        "ffmpeg"
    }
}

fn ffprobe_file_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "ffprobe.exe"
    } else {
        "ffprobe"
    }
}

fn make_executable(path: &PathBuf) -> Result<(), String> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mut perms = fs::metadata(path)
            .map_err(|e| format!("Erro ao ler permissões: {}", e))?
            .permissions();
        perms.set_mode(0o755);
        fs::set_permissions(path, perms)
            .map_err(|e| format!("Erro ao definir permissões: {}", e))?;
    }
    #[cfg(not(unix))]
    {
        let _ = path;
    }
    Ok(())
}

fn locate_ffmpeg() -> Option<PathBuf> {
    let bundled = get_app_tools_dir().join(ffmpeg_file_name());
    if bundled.exists() {
        return Some(bundled);
    }

    let finder = if cfg!(target_os = "windows") { "where" } else { "which" };
    let mut cmd = Command::new(finder);
    cmd.arg("ffmpeg");
    configure_command(&mut cmd);
    let output = cmd.output().ok()?;
    if !output.status.success() {
        return None;
    }
    let text = String::from_utf8_lossy(&output.stdout);
    let path = PathBuf::from(text.lines().next()?.trim());
    if path.exists() { Some(path) } else { None }
}

fn short_ffmpeg_version(line: &str) -> String {
    let line = line.split("Copyright").next().unwrap_or(line).trim();
    let rest = line.strip_prefix("ffmpeg version ").unwrap_or(line);
    rest.split_whitespace().next().unwrap_or(rest).to_string()
}

fn json_u64(value: Option<&serde_json::Value>) -> Option<u64> {
    let value = value?;
    if let Some(number) = value.as_u64() {
        return Some(number);
    }
    value.as_f64().and_then(|number| {
        if number.is_sign_negative() {
            None
        } else {
            Some(number.round() as u64)
        }
    })
}

fn friendly_download_error(lines: &[String]) -> String {
    let text = lines.join("\n");
    remember_block(&text);
    if let Some(notice) = challenge_notice(&text) {
        return notice;
    }
    if text.contains("Sign in to confirm") || text.to_lowercase().contains("not a bot") {
        return "O YouTube pediu para confirmar que você não é um robô. Entre com o Firefox e tente de novo.".to_string();
    }
    let mut picked: Vec<&str> = lines
        .iter()
        .filter(|line| {
            let lower = line.to_lowercase();
            lower.contains("error")
                || lower.contains("ffmpeg")
                || lower.contains("sign in")
                || lower.contains("challenge")
        })
        .map(|line| line.trim())
        .filter(|line| !line.is_empty())
        .collect();
    if picked.is_empty() {
        picked = lines.iter().rev().take(6).map(|line| line.trim()).filter(|line| !line.is_empty()).collect();
        picked.reverse();
    }
    if picked.len() > 6 {
        picked = picked[picked.len() - 6..].to_vec();
    }
    if picked.is_empty() {
        "O download terminou com erro.".to_string()
    } else {
        format!("O download terminou com erro:\n{}", picked.join("\n"))
    }
}

fn remove_partial_outputs(paths: &[String]) {
    for path in paths {
        let _ = fs::remove_file(path);
        let _ = fs::remove_file(format!("{path}.part"));
        let _ = fs::remove_file(format!("{path}.ytdl"));
    }
}

fn find_file_named(dir: &std::path::Path, name: &str) -> Option<PathBuf> {
    let entries = fs::read_dir(dir).ok()?;
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            if let Some(found) = find_file_named(&path, name) {
                return Some(found);
            }
        } else if path.file_name().and_then(|n| n.to_str()) == Some(name) {
            return Some(path);
        }
    }
    None
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
    let ffmpeg_path = locate_ffmpeg();

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

    // Verificar FFmpeg do app ou do sistema
    if let Some(ffmpeg_exe) = ffmpeg_path {
        let mut cmd = Command::new(&ffmpeg_exe);
        cmd.arg("-version");
        configure_command(&mut cmd);
        if let Ok(output) = cmd.output() {
            if output.status.success() {
                let version_str = String::from_utf8_lossy(&output.stdout);
                let version = version_str.lines().next().unwrap_or("");
                status.ffmpeg_installed = true;
                status.ffmpeg_version = Some(short_ffmpeg_version(version));
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
    } else if cfg!(target_arch = "aarch64") {
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64"
    } else {
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
    };

    // Baixar arquivo
    let response = reqwest::get(url)
        .await
        .map_err(|e| format!("Erro ao baixar yt-dlp: {}", e))?;

    if !response.status().is_success() {
        return Err(format!("Erro HTTP ao baixar yt-dlp: {}", response.status()));
    }

    let bytes = response.bytes()
        .await
        .map_err(|e| format!("Erro ao ler dados: {}", e))?;

    // Salvar arquivo
    let mut file = fs::File::create(&ytdlp_path)
        .map_err(|e| format!("Erro ao criar arquivo: {}", e))?;
    
    file.write_all(&bytes)
        .map_err(|e| format!("Erro ao salvar arquivo: {}", e))?;

    drop(file);
    make_executable(&ytdlp_path)?;

    Ok(())
}

#[tauri::command]
async fn download_ffmpeg() -> Result<(), String> {
    if cfg!(target_os = "linux") {
        return download_ffmpeg_linux().await;
    }
    if !cfg!(target_os = "windows") {
        return Err("Download automático de FFmpeg não suportado neste sistema".to_string());
    }

    let tools_dir = get_app_tools_dir();
    let ffmpeg_exe = tools_dir.join(ffmpeg_file_name());
    let ffprobe_exe = tools_dir.join(ffprobe_file_name());
    let zip_path = tools_dir.join("ffmpeg.zip");

    // URL do FFmpeg BtbN (versão mais leve, ~30MB)
    // Só baixamos ffmpeg.exe e ffprobe.exe que são necessários para yt-dlp
    let url = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";

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

async fn download_ffmpeg_linux() -> Result<(), String> {
    let tools_dir = get_app_tools_dir();
    let archive_path = tools_dir.join("ffmpeg.tar.xz");
    let extract_dir = tools_dir.join("ffmpeg-extract");
    let url = if cfg!(target_arch = "aarch64") {
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarm64-gpl.tar.xz"
    } else {
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"
    };

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(300))
        .build()
        .map_err(|e| format!("Erro ao criar cliente HTTP: {}", e))?;

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

    let mut file = fs::File::create(&archive_path)
        .map_err(|e| format!("Erro ao criar arquivo: {}", e))?;
    file.write_all(&bytes)
        .map_err(|e| format!("Erro ao salvar arquivo: {}", e))?;
    drop(file);

    let _ = fs::remove_dir_all(&extract_dir);
    fs::create_dir_all(&extract_dir)
        .map_err(|e| format!("Erro ao criar pasta temporária: {}", e))?;

    let status = Command::new("tar")
        .arg("-xJf")
        .arg(&archive_path)
        .arg("-C")
        .arg(&extract_dir)
        .status()
        .map_err(|e| format!("Erro ao extrair FFmpeg com tar: {}", e))?;

    if !status.success() {
        return Err("Falha ao extrair o FFmpeg. O pacote xz-utils precisa estar instalado.".to_string());
    }

    let ffmpeg_src = find_file_named(&extract_dir, "ffmpeg")
        .ok_or_else(|| "ffmpeg não encontrado no pacote".to_string())?;
    let ffprobe_src = find_file_named(&extract_dir, "ffprobe")
        .ok_or_else(|| "ffprobe não encontrado no pacote".to_string())?;

    let ffmpeg_dest = tools_dir.join(ffmpeg_file_name());
    let ffprobe_dest = tools_dir.join(ffprobe_file_name());
    fs::copy(&ffmpeg_src, &ffmpeg_dest)
        .map_err(|e| format!("Erro ao copiar ffmpeg: {}", e))?;
    fs::copy(&ffprobe_src, &ffprobe_dest)
        .map_err(|e| format!("Erro ao copiar ffprobe: {}", e))?;
    make_executable(&ffmpeg_dest)?;
    make_executable(&ffprobe_dest)?;

    let _ = fs::remove_file(&archive_path);
    let _ = fs::remove_dir_all(&extract_dir);
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
    filesize: Option<u64>,
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

#[tauri::command]
async fn check_url_protection(url: String) -> Result<ProtectionResult, String> {
    wait_out_cooldown().await;
    let ytdlp = get_ytdlp_path();
    
    // Usar canal para receber resultado de thread separada
    let (tx, rx) = std::sync::mpsc::channel();
    
    std::thread::spawn(move || {
        let mut cmd = Command::new(&ytdlp);
        // Verificar os primeiros 5 itens da playlist para detectar se algum precisa de auth
        cmd.args(&[
            "--simulate",
            "--ignore-errors",
            "--socket-timeout", "10",
            "--playlist-items", "1:5",
            "--flat-playlist",
            "-j",
            &url
        ]);
        cmd.args(js_runtime_args());
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
    remember_block(&combined_output);
    
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
    wait_out_cooldown().await;
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
        cmd.args(js_runtime_args());
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
        vec![
            "/usr/bin/firefox",
            "/usr/bin/firefox-esr",
            "/snap/bin/firefox",
        ]
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
    wait_out_cooldown().await;
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
        cmd.args(js_runtime_args());
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
        remember_block(&stderr);
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
    wait_out_cooldown().await;
    let ytdlp = get_ytdlp_path();
    
    let mut args = vec![
        "--flat-playlist".to_string(),
        "--socket-timeout".to_string(),
        "30".to_string(),
        "-J".to_string(),
    ];
    args.extend(js_runtime_args());

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
        remember_block(&stderr);
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
                    duration: json_u64(entry.get("duration")),
                    channel: entry.get("channel").and_then(|c| c.as_str()).map(|s| s.to_string())
                        .or_else(|| entry.get("uploader").and_then(|u| u.as_str()).map(|s| s.to_string())),
                    thumbnail,
                    filesize: json_u64(entry.get("filesize")).or_else(|| json_u64(entry.get("filesize_approx"))),
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
                duration: json_u64(json.get("duration")),
                channel: json.get("channel").and_then(|c| c.as_str()).map(|s| s.to_string()),
                thumbnail: json.get("thumbnail").and_then(|t| t.as_str()).map(|s| s.to_string()),
                filesize: json_u64(json.get("filesize")).or_else(|| json_u64(json.get("filesize_approx"))),
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
    let pause = cooldown_remaining();
    if !pause.is_zero() {
        std::thread::sleep(pause);
    }
    // Tentar obter formatos reais do yt-dlp
    let ytdlp_path = get_ytdlp_exe_path();
    
    if ytdlp_path.exists() {
        let mut cmd = Command::new(&ytdlp_path);
        cmd.args(["-J"]);
        cmd.args(js_runtime_args());
        
        // Cookies - usar do Firefox ou arquivo
        if use_browser_cookies.unwrap_or(false) {
            cmd.args(["--cookies-from-browser", "firefox"]);
        } else if let Some(ref cookies) = cookies_path {
            cmd.args(["--cookies", cookies]);
        }
        
        cmd.arg(&url);
        configure_command(&mut cmd);
        
        if let Ok(output) = cmd.output() {
            if !output.status.success() {
                remember_block(&String::from_utf8_lossy(&output.stderr));
            }
            let stderr = String::from_utf8_lossy(&output.stderr);
            if let Some(notice) = challenge_notice(&stderr) {
                return Err(notice);
            }
            if output.status.success() {
                let stdout = String::from_utf8_lossy(&output.stdout);
                
                // Parsear JSON e extrair formatos
                if let Ok(json) = serde_json::from_str::<serde_json::Value>(&stdout) {
                    let mut video_formats: Vec<String> = vec!["best".to_string()];
                    let mut audio_formats: Vec<String> = vec!["best".to_string()];
                    let mut seen_resolutions: std::collections::HashSet<String> = std::collections::HashSet::new();
                    
                    if let Some(formats) = json.get("formats").and_then(|f| f.as_array()) {
                        for format in formats {
                            let vcodec = format.get("vcodec").and_then(|v| v.as_str()).unwrap_or("none");
                            let ext = format.get("ext").and_then(|v| v.as_str()).unwrap_or("");
                            let is_image = vcodec == "none"
                                || matches!(ext, "mhtml" | "jpg" | "jpeg" | "png" | "webp");
                            // Formatos de vídeo
                            if !is_image {
                            if let Some(height) = format.get("height").and_then(|h| h.as_i64()) {
                                let resolution = format!("{}p", height);
                                if !seen_resolutions.contains(&resolution) && height >= 360 {
                                    seen_resolutions.insert(resolution.clone());
                                    video_formats.push(resolution);
                                }
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
                    
                    return Ok(FormatsResult {
                        video: video_formats,
                        audio: audio_formats,
                    });
                }
            }
        }
    }
    
    Err("Não foi possível ler os formatos deste link.".to_string())
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
    run_download(options, |progress| {
        let _ = app.emit("download-progress", progress);
    })
    .await
    .map(|_| "Download concluído com sucesso!".to_string())
}

async fn run_download(
    options: DownloadOptions,
    mut emit: impl FnMut(&DownloadProgress),
) -> Result<Vec<String>, String> {
    let pause = cooldown_remaining();
    if !pause.is_zero() {
        emit(&DownloadProgress {
            status: "waiting".to_string(),
            progress: 0.0,
            speed: "".to_string(),
            eta: format!("{}s", pause.as_secs()),
            filename: "O YouTube limitou as consultas. Aguardando para tentar de novo...".to_string(),
            current: 0,
            total: 1,
        });
        wait_out_cooldown().await;
    }
    let ytdlp = get_ytdlp_path();
    let ffmpeg_location = locate_ffmpeg()
        .map(|path| path.to_string_lossy().to_string())
        .unwrap_or_else(|| get_app_tools_dir().to_string_lossy().to_string());
    
    // Construir argumentos base
    let mut args: Vec<String> = vec![
        "--ffmpeg-location".to_string(),
        ffmpeg_location,
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
    args.extend(js_runtime_args());
    
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
    emit(& DownloadProgress {
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
    let stderr = child.stderr.take().ok_or("Falha ao capturar stderr")?;
    let stderr_task = std::thread::spawn(move || {
        let reader = BufReader::new(stderr);
        let mut lines = Vec::new();
        for line in reader.lines().flatten() {
            lines.push(line);
            if lines.len() > 80 {
                lines.remove(0);
            }
        }
        lines
    });
    let reader = BufReader::new(stdout);
    
    let mut current_item: u32 = 1;
    let mut last_filename = String::new();
    let mut output_paths: Vec<String> = Vec::new();
    
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
                    
                    emit(& DownloadProgress {
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
                        output_paths.push(full_path.clone());
                        // Se tiver nome diferente, é um novo item
                        let new_name = std::path::Path::new(&full_path)
                            .file_name()
                            .map(|f| f.to_string_lossy().to_string())
                            .unwrap_or(full_path.clone());
                        
                        if !last_filename.is_empty() && new_name != last_filename {
                            current_item += 1;
                        }
                        last_filename = new_name;
                        
                        emit(& DownloadProgress {
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
                emit(& DownloadProgress {
                    status: "merging".to_string(),
                    progress: 99.0,
                    speed: "".to_string(),
                    eta: "".to_string(),
                    filename: format!("Mesclando: {}", last_filename),
                    current: current_item,
                    total: total_items,
                });
            } else if line.contains("[ExtractAudio]") {
                emit(& DownloadProgress {
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
                emit(& DownloadProgress {
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
    let stderr_lines = stderr_task.join().unwrap_or_default();
    let status = child.wait().map_err(|e| format!("Erro ao aguardar processo: {}", e))?;
    
    if status.success() {
        emit(& DownloadProgress {
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

        let files = output_paths
            .into_iter()
            .filter(|path| std::path::Path::new(path).exists())
            .collect();
        Ok(files)
    } else {
        emit(& DownloadProgress {
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
        
        remove_partial_outputs(&output_paths);
        Err(friendly_download_error(&stderr_lines))
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

fn print_agent_json(value: &serde_json::Value) {
    println!("{}", value);
    let _ = std::io::stdout().flush();
}

fn agent_fail(message: String) -> i32 {
    print_agent_json(&serde_json::json!({
        "event": "result",
        "ok": false,
        "error": message,
    }));
    1
}

struct AgentFlags {
    firefox: bool,
    quiet: bool,
    cookies: Option<String>,
    radio_limit: u32,
    mode: Option<String>,
    quality: Option<String>,
    out: Option<String>,
    items: Vec<String>,
    positionals: Vec<String>,
}

fn parse_agent_flags(args: &[String]) -> Result<AgentFlags, String> {
    let mut flags = AgentFlags {
        firefox: false,
        quiet: false,
        cookies: None,
        radio_limit: 50,
        mode: None,
        quality: None,
        out: None,
        items: Vec::new(),
        positionals: Vec::new(),
    };
    let mut index = 0;
    while index < args.len() {
        let arg = &args[index];
        let take = |index: &mut usize| -> Result<String, String> {
            *index += 1;
            args.get(*index)
                .cloned()
                .ok_or_else(|| format!("Falta o valor de {arg}"))
        };
        match arg.as_str() {
            "--firefox" => flags.firefox = true,
            "--quiet" => flags.quiet = true,
            "--radio-limit" => {
                let value = take(&mut index)?;
                flags.radio_limit = value
                    .parse()
                    .map_err(|_| format!("--radio-limit inválido: {value}"))?;
            }
            "--cookies" => flags.cookies = Some(take(&mut index)?),
            "--mode" => flags.mode = Some(take(&mut index)?),
            "--quality" => flags.quality = Some(take(&mut index)?),
            "--out" => flags.out = Some(take(&mut index)?),
            "--item" => flags.items.push(take(&mut index)?),
            value if value.starts_with('-') => {
                return Err(format!("Opção desconhecida: {value}"));
            }
            value => flags.positionals.push(value.to_string()),
        }
        index += 1;
    }
    Ok(flags)
}

fn agent_help() -> &'static str {
    "\
Uso manual: ytdlp-tauri-agent <comando> [opções]
Servidor MCP: ytdlp-tauri-agent mcp
Sem argumentos e com a entrada em pipe, também sobe o MCP.

Comandos:
  tools                         mostra yt-dlp e FFmpeg e instala o que faltar
  analyze <url>                 lista vídeo ou itens de playlist/rádio
  formats <url>                 lista qualidades de vídeo e áudio
  download <url>                baixa com o mesmo fluxo da interface
  mcp                           servidor MCP em stdin/stdout

Opções:
  --firefox                     usa os cookies do Firefox desta máquina
  --cookies <arquivo>           usa um arquivo de cookies no formato Netscape
  --radio-limit <n>             limite de itens de rádio (padrão 50)
  --mode video|audio            obrigatório no download
  --quality <valor>             1080p, 720p, best, 320, 192... obrigatório no download
  --out <pasta>                 pasta de destino, obrigatória no download
  --item <url>                  baixa só esta URL (pode repetir)
  --quiet                       omite o progresso e imprime só o resultado

A saída é uma linha JSON por evento. O resultado termina com \"event\":\"result\".
"
}

async fn ensure_tools() -> Result<ToolsStatus, String> {
    let status = check_tools().await?;
    if !status.ytdlp_installed {
        download_ytdlp().await?;
    }
    if !status.ffmpeg_installed {
        download_ffmpeg().await?;
    }
    check_tools().await
}

async fn agent_main(args: Vec<String>) -> i32 {
    let Some(command) = args.first() else {
        println!("{}", agent_help());
        return 0;
    };
    if matches!(command.as_str(), "help" | "--help" | "-h") {
        println!("{}", agent_help());
        return 0;
    }

    let flags = match parse_agent_flags(&args[1..]) {
        Ok(flags) => flags,
        Err(error) => return agent_fail(error),
    };
    let url = flags.positionals.first().cloned();
    let firefox = flags.firefox;
    let cookies = flags.cookies.clone();

    let run = async {
        match command.as_str() {
            "tools" => {
                let status = ensure_tools().await?;
                print_agent_json(&serde_json::json!({
                    "event": "result",
                    "ok": true,
                    "tools": status,
                }));
                Ok(0)
            }
            "analyze" => {
                let url = url.ok_or("Informe a URL")?;
                ensure_tools().await?;
                let result = analyze_url(url, flags.radio_limit, cookies, Some(firefox)).await?;
                print_agent_json(&serde_json::json!({
                    "event": "result",
                    "ok": true,
                    "analysis": result,
                }));
                Ok(0)
            }
            "formats" => {
                let url = url.ok_or("Informe a URL")?;
                ensure_tools().await?;
                let result = get_formats(url, cookies, Some(firefox))?;
                print_agent_json(&serde_json::json!({
                    "event": "result",
                    "ok": true,
                    "formats": result,
                }));
                Ok(0)
            }
            "download" => {
                let url = url.ok_or("Informe a URL")?;
                let mode = flags.mode.clone().ok_or("Informe --mode video ou --mode audio")?;
                if mode != "video" && mode != "audio" {
                    return Err("O modo precisa ser video ou audio".to_string());
                }
                let quality = flags.quality.clone().ok_or("Informe --quality")?;
                let destination = flags.out.clone().ok_or("Informe --out com a pasta de destino")?;
                ensure_tools().await?;
                let options = DownloadOptions {
                    url,
                    destination,
                    mode,
                    quality,
                    cookies_path: cookies,
                    use_browser_cookies: Some(firefox),
                    selected_items: if flags.items.is_empty() {
                        None
                    } else {
                        Some(flags.items.clone())
                    },
                };
                let files = run_download(options, |progress| {
                    if !flags.quiet {
                        print_agent_json(&serde_json::json!({
                            "event": "progress",
                            "progress": progress,
                        }));
                    }
                })
                .await?;
                print_agent_json(&serde_json::json!({
                    "event": "result",
                    "ok": true,
                    "files": files,
                }));
                Ok(0)
            }
            other => Err(format!("Comando desconhecido: {other}")),
        }
    };

    match run.await {
        Ok(code) => code,
        Err(error) => agent_fail(error),
    }
}

fn mcp_write(value: &serde_json::Value) {
    println!("{}", value);
    let _ = std::io::stdout().flush();
}

fn mcp_result(id: &serde_json::Value, result: serde_json::Value) {
    mcp_write(&serde_json::json!({
        "jsonrpc": "2.0",
        "id": id,
        "result": result,
    }));
}

fn mcp_error(id: &serde_json::Value, code: i32, message: String) {
    mcp_write(&serde_json::json!({
        "jsonrpc": "2.0",
        "id": id,
        "error": { "code": code, "message": message },
    }));
}

fn mcp_tool_text(value: serde_json::Value, is_error: bool) -> serde_json::Value {
    let text = if value.is_string() {
        value.as_str().unwrap_or("").to_string()
    } else {
        serde_json::to_string_pretty(&value).unwrap_or_else(|_| value.to_string())
    };
    serde_json::json!({
        "content": [{ "type": "text", "text": text }],
        "isError": is_error,
    })
}

fn mcp_arg_string(args: &serde_json::Value, key: &str) -> Option<String> {
    args.get(key).and_then(|value| match value {
        serde_json::Value::String(text) if !text.is_empty() => Some(text.clone()),
        _ => None,
    })
}

fn mcp_arg_bool(args: &serde_json::Value, key: &str) -> bool {
    args.get(key).and_then(|value| value.as_bool()).unwrap_or(false)
}

fn mcp_tools() -> serde_json::Value {
    serde_json::json!([
        {
            "name": "check_tools",
            "description": "Verifica yt-dlp e FFmpeg nesta máquina e instala o que faltar. Não abre a interface.",
            "inputSchema": { "type": "object", "properties": {} }
        },
        {
            "name": "analyze_url",
            "description": "Analisa uma URL. Vídeo único ou lista de itens de playlist e rádio, com título, link e duração.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "url": { "type": "string", "description": "URL do vídeo, playlist ou rádio" },
                    "radio_limit": { "type": "integer", "description": "Máximo de itens quando a URL é uma rádio. Padrão 50." },
                    "firefox": { "type": "boolean", "description": "Usar os cookies do Firefox desta máquina" },
                    "cookies": { "type": "string", "description": "Caminho de um arquivo de cookies Netscape" }
                },
                "required": ["url"]
            }
        },
        {
            "name": "list_formats",
            "description": "Lista as qualidades de vídeo e áudio disponíveis para a URL.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "url": { "type": "string" },
                    "firefox": { "type": "boolean", "description": "Usar os cookies do Firefox desta máquina" },
                    "cookies": { "type": "string", "description": "Caminho de um arquivo de cookies Netscape" }
                },
                "required": ["url"]
            }
        },
        {
            "name": "download",
            "description": "Baixa vídeo ou áudio com o mesmo fluxo da interface: formato por site, FFmpeg, cookies e limpeza se falhar.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "url": { "type": "string" },
                    "mode": { "type": "string", "enum": ["video", "audio"] },
                    "quality": { "type": "string", "description": "1080p, 720p, 480p, 360p, best, 320, 256, 192 ou 128" },
                    "out": { "type": "string", "description": "Pasta de destino" },
                    "firefox": { "type": "boolean", "description": "Usar os cookies do Firefox desta máquina" },
                    "cookies": { "type": "string", "description": "Caminho de um arquivo de cookies Netscape" },
                    "items": { "type": "array", "items": { "type": "string" }, "description": "URLs escolhidas de uma playlist. Vazio baixa a URL principal." }
                },
                "required": ["url", "mode", "quality", "out"]
            }
        }
    ])
}

async fn mcp_call_tool(name: &str, args: &serde_json::Value) -> Result<serde_json::Value, String> {
    let firefox = mcp_arg_bool(args, "firefox");
    let cookies = mcp_arg_string(args, "cookies");
    match name {
        "check_tools" => {
            let status = ensure_tools().await?;
            Ok(serde_json::json!({ "ok": true, "tools": status }))
        }
        "analyze_url" => {
            let url = mcp_arg_string(args, "url").ok_or("Informe url")?;
            let radio_limit = args.get("radio_limit").and_then(|value| value.as_u64()).unwrap_or(50) as u32;
            ensure_tools().await?;
            let analysis = analyze_url(url, radio_limit, cookies, Some(firefox)).await?;
            Ok(serde_json::json!({ "ok": true, "analysis": analysis }))
        }
        "list_formats" => {
            let url = mcp_arg_string(args, "url").ok_or("Informe url")?;
            ensure_tools().await?;
            let formats = get_formats(url, cookies, Some(firefox))?;
            Ok(serde_json::json!({ "ok": true, "formats": formats }))
        }
        "download" => {
            let url = mcp_arg_string(args, "url").ok_or("Informe url")?;
            let mode = mcp_arg_string(args, "mode").ok_or("Informe mode video ou audio")?;
            if mode != "video" && mode != "audio" {
                return Err("O modo precisa ser video ou audio".to_string());
            }
            let quality = mcp_arg_string(args, "quality").ok_or("Informe quality")?;
            let destination = mcp_arg_string(args, "out").ok_or("Informe out com a pasta de destino")?;
            let items = args.get("items").and_then(|value| value.as_array()).map(|list| {
                list.iter().filter_map(|item| item.as_str().map(|text| text.to_string())).collect::<Vec<_>>()
            });
            ensure_tools().await?;
            let files = run_download(
                DownloadOptions {
                    url,
                    destination,
                    mode,
                    quality,
                    cookies_path: cookies,
                    use_browser_cookies: Some(firefox),
                    selected_items: items.filter(|list| !list.is_empty()),
                },
                |_| {},
            )
            .await?;
            Ok(serde_json::json!({ "ok": true, "files": files }))
        }
        other => Err(format!("Ferramenta desconhecida: {other}")),
    }
}

fn read_mcp_message(reader: &mut impl std::io::BufRead) -> Result<Option<serde_json::Value>, String> {
    let mut line = String::new();
    loop {
        line.clear();
        let read = reader.read_line(&mut line).map_err(|error| error.to_string())?;
        if read == 0 {
            return Ok(None);
        }
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }
        if trimmed.to_ascii_lowercase().starts_with("content-length:") {
            let length: usize = trimmed
                .split(':')
                .nth(1)
                .unwrap_or("")
                .trim()
                .parse()
                .map_err(|_| "Content-Length inválido".to_string())?;
            loop {
                let mut extra = String::new();
                let extra_read = reader.read_line(&mut extra).map_err(|error| error.to_string())?;
                if extra_read == 0 || extra.trim().is_empty() {
                    break;
                }
            }
            let mut body = vec![0u8; length];
            std::io::Read::read_exact(reader, &mut body).map_err(|error| error.to_string())?;
            let value = serde_json::from_slice(&body).map_err(|error| error.to_string())?;
            return Ok(Some(value));
        }
        if trimmed.starts_with('{') {
            let value = serde_json::from_str(trimmed).map_err(|error| error.to_string())?;
            return Ok(Some(value));
        }
    }
}

fn handle_mcp_message(message: &serde_json::Value) -> Result<(), String> {
    let method = message.get("method").and_then(|value| value.as_str()).unwrap_or("");
    let id = match message.get("id") {
        Some(id) if !id.is_null() => id,
        _ => return Ok(()),
    };

    match method {
        "initialize" => {
            let requested = message
                .pointer("/params/protocolVersion")
                .and_then(|value| value.as_str())
                .unwrap_or("2024-11-05");
            let protocol = if requested == "2025-03-26" || requested == "2024-11-05" {
                requested
            } else {
                "2024-11-05"
            };
            mcp_result(id, serde_json::json!({
                "protocolVersion": protocol,
                "capabilities": { "tools": { "listChanged": false } },
                "serverInfo": { "name": "ytdlp-tauri-agent", "version": env!("CARGO_PKG_VERSION") }
            }));
        }
        "ping" => mcp_result(id, serde_json::json!({})),
        "tools/list" => mcp_result(id, serde_json::json!({ "tools": mcp_tools() })),
        "tools/call" => {
            let name = message.pointer("/params/name").and_then(|value| value.as_str()).unwrap_or("");
            let arguments = message.pointer("/params/arguments").cloned().unwrap_or_else(|| serde_json::json!({}));
            let result = match async_runtime::block_on(mcp_call_tool(name, &arguments)) {
                Ok(value) => mcp_tool_text(value, false),
                Err(error) => mcp_tool_text(serde_json::Value::String(error), true),
            };
            mcp_result(id, result);
        }
        _ => mcp_error(id, -32601, format!("Método não suportado: {method}")),
    }
    Ok(())
}

fn run_mcp() -> i32 {
    let stdin = std::io::stdin();
    let mut reader = std::io::BufReader::new(stdin.lock());
    loop {
        match read_mcp_message(&mut reader) {
            Ok(Some(message)) => {
                if let Err(error) = handle_mcp_message(&message) {
                    let _ = writeln!(std::io::stderr(), "{error}");
                    return 1;
                }
            }
            Ok(None) => return 0,
            Err(error) => {
                let _ = writeln!(std::io::stderr(), "{error}");
                return 1;
            }
        }
    }
}

fn wants_mcp(args: &[String]) -> bool {
    match args.first().map(|arg| arg.as_str()) {
        Some("mcp") | Some("--mcp") => true,
        Some(_) => false,
        None => !std::io::IsTerminal::is_terminal(&std::io::stdin()),
    }
}

/// Entrada sem interface.
/// Comando na linha vira uso manual. `mcp`, ou a entrada em pipe sem argumentos, fala MCP.
pub fn run_agent() -> i32 {
    let args: Vec<String> = env::args().skip(1).collect();
    if wants_mcp(&args) {
        return run_mcp();
    }
    async_runtime::block_on(agent_main(args))
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
