<script setup>
import { ref, onMounted, computed } from "vue";
import { invoke } from "@tauri-apps/api/core";

const emit = defineEmits(["ready"]);

const status = ref("checking"); // checking, downloading, extracting, ready, error
const currentTool = ref("");
const progress = ref(0);
const error = ref("");
const logs = ref([]);

const tools = ref({
  ytdlp: { installed: false, version: "", downloading: false, latestVersion: "", updateAvailable: false },
  ffmpeg: { installed: false, version: "", downloading: false },
});

// Gerar estilos para ícones flutuantes
const floatingIcons = computed(() => {
  return Array.from({ length: 15 }, (_, i) => {
    const seed = i * 7;
    const size = 35 + (seed % 45);
    const left = (seed * 17) % 100;
    const delay = (seed % 18);
    const duration = 12 + (seed % 12);
    const isGlass = i % 3 === 0;
    const isRed = i % 2 === 0;
    
    return {
      width: `${size}px`,
      height: `${size}px`,
      left: `${left}%`,
      animationDelay: `-${delay}s`,
      animationDuration: `${duration}s`,
      opacity: 0.15 + (seed % 25) / 100,
      '--icon-bg': isGlass 
        ? 'rgba(255, 255, 255, 0.15)' 
        : isRed 
          ? 'rgba(233, 69, 96, 0.5)' 
          : 'rgba(255, 107, 107, 0.35)',
      '--icon-color': isGlass ? 'rgba(233, 69, 96, 0.8)' : 'white',
      '--icon-blur': isGlass ? '8px' : '0px',
    };
  });
});

function addLog(message) {
  logs.value.push({ time: new Date().toLocaleTimeString(), message });
}

onMounted(async () => {
  await checkTools();
});

async function checkTools() {
  status.value = "checking";
  addLog("Verificando ferramentas necessárias...");

  try {
    const result = await invoke("check_tools");
    tools.value.ytdlp.installed = result.ytdlp_installed;
    tools.value.ytdlp.version = result.ytdlp_version || "";
    tools.value.ytdlp.latestVersion = result.ytdlp_latest_version || "";
    tools.value.ytdlp.updateAvailable = result.ytdlp_update_available || false;
    tools.value.ffmpeg.installed = result.ffmpeg_installed;
    tools.value.ffmpeg.version = result.ffmpeg_version || "";

    if (result.ytdlp_installed) {
      addLog(`✓ yt-dlp encontrado: ${result.ytdlp_version}`);
      if (result.ytdlp_update_available) {
        addLog(`⬆ Nova versão disponível: ${result.ytdlp_latest_version}`);
      }
    } else {
      addLog("✗ yt-dlp não encontrado");
    }

    if (result.ffmpeg_installed) {
      addLog(`✓ FFmpeg encontrado: ${result.ffmpeg_version}`);
    } else {
      addLog("✗ FFmpeg não encontrado");
    }

    if (result.ytdlp_installed && result.ffmpeg_installed) {
      // Se há atualização, mostrar opção
      if (result.ytdlp_update_available) {
        status.value = "update_available";
        addLog("💡 Você pode atualizar o yt-dlp ou continuar com a versão atual.");
      } else {
        status.value = "ready";
        addLog("🎉 Tudo pronto e atualizado!");
        setTimeout(() => emit("ready"), 1500);
      }
    } else {
      status.value = "need_install";
    }
  } catch (e) {
    error.value = e.toString();
    status.value = "error";
    addLog(`Erro: ${e}`);
  }
}

async function installTools() {
  status.value = "downloading";
  error.value = "";

  try {
    // Instalar yt-dlp se necessário
    if (!tools.value.ytdlp.installed) {
      currentTool.value = "yt-dlp";
      tools.value.ytdlp.downloading = true;
      addLog("Baixando yt-dlp...");
      
      await invoke("download_ytdlp", {
        onProgress: (p) => {
          progress.value = p;
        }
      });
      
      tools.value.ytdlp.installed = true;
      tools.value.ytdlp.downloading = false;
      addLog("✓ yt-dlp instalado com sucesso!");
    }

    // Instalar FFmpeg se necessário
    if (!tools.value.ffmpeg.installed) {
      currentTool.value = "FFmpeg";
      tools.value.ffmpeg.downloading = true;
      progress.value = 0;
      addLog("Baixando FFmpeg (isso pode demorar um pouco)...");
      
      await invoke("download_ffmpeg");
      
      tools.value.ffmpeg.installed = true;
      tools.value.ffmpeg.downloading = false;
      addLog("✓ FFmpeg instalado com sucesso!");
    }

    status.value = "ready";
    addLog("🎉 Tudo pronto! Iniciando aplicação...");
    setTimeout(() => emit("ready"), 1500);
  } catch (e) {
    error.value = e.toString();
    status.value = "error";
    addLog(`Erro na instalação: ${e}`);
  }
}

function retry() {
  error.value = "";
  checkTools();
}

async function updateYtdlp() {
  status.value = "updating";
  addLog("Atualizando yt-dlp...");
  
  try {
    const newVersion = await invoke("update_ytdlp");
    tools.value.ytdlp.version = newVersion;
    tools.value.ytdlp.updateAvailable = false;
    addLog(`✓ yt-dlp atualizado para: ${newVersion}`);
    status.value = "ready";
    addLog("🎉 Tudo pronto!");
    setTimeout(() => emit("ready"), 1500);
  } catch (e) {
    error.value = e.toString();
    status.value = "error";
    addLog(`Erro ao atualizar: ${e}`);
  }
}

function skipUpdate() {
  addLog("Continuando com a versão atual...");
  status.value = "ready";
  setTimeout(() => emit("ready"), 500);
}
</script>

<template>
  <div class="setup-screen">
    <!-- Fundo animado com ícones de música e vídeo descendo -->
    <div class="animated-bg">
      <div class="download-icons">
        <div v-for="(style, i) in floatingIcons" :key="i" class="floating-icon" :style="style">
          <!-- Ícone de música -->
          <svg v-if="i % 3 === 0" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/>
          </svg>
          <!-- Ícone de play -->
          <svg v-else-if="i % 3 === 1" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z"/>
          </svg>
          <!-- Ícone de filme -->
          <svg v-else viewBox="0 0 24 24" fill="currentColor">
            <path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/>
          </svg>
        </div>
      </div>
      <div class="gradient-overlay"></div>
    </div>
    
    <div class="setup-container">
      <h1>🎬 YT-DLP GUI</h1>
      <p class="subtitle">Configuração Inicial</p>

      <!-- Verificando -->
      <div v-if="status === 'checking'" class="status-section">
        <div class="spinner"></div>
        <p>Verificando dependências...</p>
      </div>

      <!-- Precisa instalar -->
      <div v-else-if="status === 'need_install'" class="status-section">
        <div class="tools-status">
          <div :class="['tool-item', { installed: tools.ytdlp.installed }]">
            <span class="tool-icon">{{ tools.ytdlp.installed ? '✅' : '📦' }}</span>
            <div class="tool-info">
              <strong>yt-dlp</strong>
              <span v-if="tools.ytdlp.installed">{{ tools.ytdlp.version }}</span>
              <span v-else class="not-found">Não instalado</span>
            </div>
          </div>

          <div :class="['tool-item', { installed: tools.ffmpeg.installed }]">
            <span class="tool-icon">{{ tools.ffmpeg.installed ? '✅' : '📦' }}</span>
            <div class="tool-info">
              <strong>FFmpeg</strong>
              <span v-if="tools.ffmpeg.installed">{{ tools.ffmpeg.version }}</span>
              <span v-else class="not-found">Não instalado</span>
            </div>
          </div>
        </div>

        <div class="install-info">
          <p>Algumas ferramentas precisam ser instaladas para o app funcionar.</p>
          <p class="note">📁 Serão instaladas na pasta do aplicativo (não afeta seu sistema).</p>
        </div>

        <button @click="installTools" class="btn-install">
          📥 Instalar Dependências
        </button>
      </div>

      <!-- Baixando -->
      <div v-else-if="status === 'downloading'" class="status-section">
        <div class="download-status">
          <div class="spinner"></div>
          <p>Baixando {{ currentTool }}...</p>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progress + '%' }"></div>
          </div>
          <span class="progress-text">{{ progress }}%</span>
        </div>
      </div>

      <!-- Atualização disponível -->
      <div v-else-if="status === 'update_available'" class="status-section">
        <div class="update-available">
          <span class="update-icon">⬆️</span>
          <h3>Atualização Disponível</h3>
          <div class="version-comparison">
            <div class="version-box current">
              <span class="label">Atual</span>
              <span class="version">{{ tools.ytdlp.version }}</span>
            </div>
            <span class="arrow">→</span>
            <div class="version-box latest">
              <span class="label">Nova</span>
              <span class="version">{{ tools.ytdlp.latestVersion }}</span>
            </div>
          </div>
          <p class="update-note">Uma nova versão do yt-dlp está disponível com melhorias e correções.</p>
          <div class="update-buttons">
            <button @click="updateYtdlp" class="btn-update">
              ⬆️ Atualizar Agora
            </button>
            <button @click="skipUpdate" class="btn-skip">
              Continuar sem atualizar
            </button>
          </div>
        </div>
      </div>

      <!-- Atualizando -->
      <div v-else-if="status === 'updating'" class="status-section">
        <div class="download-status">
          <div class="spinner"></div>
          <p>Atualizando yt-dlp...</p>
        </div>
      </div>

      <!-- Pronto -->
      <div v-else-if="status === 'ready'" class="status-section success">
        <span class="success-icon">✅</span>
        <p>Tudo pronto!</p>
      </div>

      <!-- Erro -->
      <div v-else-if="status === 'error'" class="status-section error">
        <span class="error-icon">❌</span>
        <p>Ocorreu um erro</p>
        <p class="error-message">{{ error }}</p>
        <button @click="retry" class="btn-retry">🔄 Tentar novamente</button>
      </div>

      <!-- Logs -->
      <div class="logs-section">
        <div class="logs-header" @click="showLogs = !showLogs">
          📋 Logs
        </div>
        <div class="logs-content">
          <div v-for="(log, index) in logs" :key="index" class="log-entry">
            <span class="log-time">{{ log.time }}</span>
            <span class="log-message">{{ log.message }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.setup-screen {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0a0a12 0%, #1a1a2e 50%, #0f0f1a 100%);
  padding: 20px;
  position: relative;
  overflow: hidden;
}

/* Fundo animado */
.animated-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
}

.download-icons {
  position: absolute;
  width: 100%;
  height: 100%;
}

.floating-icon {
  position: absolute;
  top: -100px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--icon-bg);
  backdrop-filter: blur(var(--icon-blur));
  -webkit-backdrop-filter: blur(var(--icon-blur));
  border: 1px solid rgba(255, 255, 255, 0.1);
  animation: floatDown linear infinite;
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.floating-icon svg {
  width: 50%;
  height: 50%;
  color: var(--icon-color);
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}

@keyframes floatDown {
  0% {
    transform: translateY(0) rotate(0deg) scale(1);
    opacity: 0;
  }
  5% {
    opacity: 0.3;
  }
  50% {
    transform: translateY(50vh) rotate(180deg) scale(1.1);
  }
  95% {
    opacity: 0.3;
  }
  100% {
    transform: translateY(120vh) rotate(360deg) scale(1);
    opacity: 0;
  }
}

.gradient-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: 
    radial-gradient(circle at 30% 70%, rgba(233, 69, 96, 0.2) 0%, transparent 50%),
    radial-gradient(circle at 70% 30%, rgba(233, 69, 96, 0.15) 0%, transparent 40%),
    radial-gradient(circle at 50% 50%, rgba(0, 0, 0, 0.2) 0%, transparent 60%);
  pointer-events: none;
}

.setup-container {
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 45px;
  max-width: 480px;
  width: 100%;
  text-align: center;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 
    0 20px 60px rgba(0, 0, 0, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
  position: relative;
  z-index: 1;
}

h1 {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 8px 0;
  background: linear-gradient(135deg, #ff6b6b, #e94560, #ff8a80);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  color: rgba(255, 255, 255, 0.5);
  margin: 0 0 35px 0;
  font-size: 15px;
  font-weight: 400;
}

.status-section {
  padding: 25px 0;
}

.spinner {
  width: 56px;
  height: 56px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #e94560;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 25px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.tools-status {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 28px;
}

.tool-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: all 0.3s;
}

.tool-item.installed {
  border-color: rgba(0, 184, 148, 0.3);
  background: rgba(0, 184, 148, 0.08);
}

.tool-icon {
  font-size: 28px;
  width: 45px;
  height: 45px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(233, 69, 96, 0.15);
  border-radius: 12px;
}

.tool-item.installed .tool-icon {
  background: rgba(0, 184, 148, 0.15);
}

.tool-info {
  text-align: left;
  flex: 1;
}

.tool-info strong {
  display: block;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 3px;
}

.tool-info span {
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
}

.not-found {
  color: rgba(233, 69, 96, 0.8) !important;
}

.install-info {
  margin-bottom: 25px;
}

.install-info p {
  color: rgba(255, 255, 255, 0.6);
  margin: 8px 0;
  font-size: 14px;
}

.install-info .note {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.03);
  padding: 12px 16px;
  border-radius: 12px;
  margin-top: 15px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.btn-install {
  background: linear-gradient(135deg, #e94560, #ff6b6b);
  color: white;
  border: none;
  padding: 16px 45px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 8px 30px rgba(233, 69, 96, 0.4);
}

.btn-install:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 40px rgba(233, 69, 96, 0.5);
}

.download-status p {
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: 20px;
  font-size: 15px;
}

.progress-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 12px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #e94560, #ff6b6b);
  transition: width 0.3s;
  box-shadow: 0 0 20px rgba(233, 69, 96, 0.5);
}

.progress-text {
  color: #e94560;
  font-weight: 600;
  font-size: 18px;
}

.success {
  color: #00b894;
}

.success-icon {
  font-size: 70px;
  display: block;
  margin-bottom: 20px;
  filter: drop-shadow(0 0 20px rgba(0, 184, 148, 0.4));
}

.error {
  color: #e74c3c;
}

.error-icon {
  font-size: 70px;
  display: block;
  margin-bottom: 20px;
}

.error-message {
  background: rgba(231, 76, 60, 0.1);
  border: 1px solid rgba(231, 76, 60, 0.2);
  padding: 15px;
  border-radius: 12px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  margin: 20px 0;
  text-align: left;
  max-height: 100px;
  overflow-y: auto;
}

.btn-retry {
  background: rgba(231, 76, 60, 0.2);
  color: #ff6b6b;
  border: 1px solid rgba(231, 76, 60, 0.3);
  padding: 12px 28px;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s;
}

.btn-retry:hover {
  background: rgba(231, 76, 60, 0.3);
}

.logs-section {
  margin-top: 35px;
  padding-top: 25px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.logs-header {
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
  margin-bottom: 12px;
  cursor: pointer;
  font-weight: 500;
}

.logs-content {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 12px;
  padding: 12px 15px;
  max-height: 130px;
  overflow-y: auto;
  text-align: left;
  border: 1px solid rgba(255, 255, 255, 0.03);
}

.log-entry {
  font-size: 11px;
  padding: 5px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  font-family: 'SF Mono', 'Consolas', monospace;
}

.log-entry:last-child {
  border-bottom: none;
}

.log-time {
  color: rgba(255, 255, 255, 0.3);
  margin-right: 12px;
}

.log-message {
  color: rgba(255, 255, 255, 0.6);
}

.logs-content::-webkit-scrollbar {
  width: 5px;
}

.logs-content::-webkit-scrollbar-track {
  background: transparent;
}

.logs-content::-webkit-scrollbar-thumb {
  background: rgba(233, 69, 96, 0.3);
  border-radius: 3px;
}

/* Estilos para atualização disponível */
.update-available {
  text-align: center;
}

.update-icon {
  font-size: 50px;
  display: block;
  margin-bottom: 15px;
}

.update-available h3 {
  color: #fff;
  font-size: 20px;
  margin: 0 0 20px 0;
  font-weight: 600;
}

.version-comparison {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
  margin-bottom: 20px;
}

.version-box {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 12px 20px;
  min-width: 100px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.version-box .label {
  display: block;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  margin-bottom: 4px;
}

.version-box .version {
  display: block;
  font-size: 14px;
  color: #fff;
  font-weight: 600;
  font-family: 'SF Mono', 'Consolas', monospace;
}

.version-box.current {
  border-color: rgba(255, 255, 255, 0.1);
}

.version-box.latest {
  border-color: rgba(0, 184, 148, 0.4);
  background: rgba(0, 184, 148, 0.1);
}

.version-box.latest .version {
  color: #00b894;
}

.arrow {
  color: rgba(255, 255, 255, 0.3);
  font-size: 20px;
}

.update-note {
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  margin-bottom: 25px;
}

.update-buttons {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.btn-update {
  background: linear-gradient(135deg, #00b894, #00cec9);
  color: white;
  border: none;
  padding: 14px 35px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 8px 25px rgba(0, 184, 148, 0.35);
}

.btn-update:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 35px rgba(0, 184, 148, 0.45);
}

.btn-skip {
  background: transparent;
  color: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 12px 25px;
  border-radius: 10px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-skip:hover {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
}
</style>
