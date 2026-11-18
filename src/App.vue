<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import SetupScreen from "./components/SetupScreen.vue";
import Step1Url from "./components/Step1Url.vue";
import Step1_5Auth from "./components/Step1_5Auth.vue";
import Step2Type from "./components/Step2Type.vue";
import Step2_5Selection from "./components/Step2_5Selection.vue";
import Step3Format from "./components/Step3Format.vue";
import Step4Destination from "./components/Step4Destination.vue";
import StepProgress from "./components/StepProgress.vue";

// Setup inicial
const setupComplete = ref(false);

function onSetupReady() {
  setupComplete.value = true;
}

// Estado global do wizard
const currentStep = ref(1);
const urlData = ref({
  url: "",
  radioLimit: 50,
});
const authData = ref({
  needsAuth: false,
  useBrowserCookies: false,
  authVerified: false,
});
const contentData = ref({
  type: "", // "video", "playlist", "radio"
  items: [],
  selectedItems: [],
});
const formatData = ref({
  mode: "", // "audio", "video"
  quality: "",
  availableFormats: [],
});
const destinationData = ref({
  path: "",
});
const downloadProgress = ref({
  active: false,
  current: 0,
  total: 0,
  currentFile: "",
  percent: 0,
  speed: "",
  eta: "",
  status: "",
});

// Listener para eventos de progresso do download
let unlistenProgress = null;

onMounted(async () => {
  unlistenProgress = await listen("download-progress", (event) => {
    const data = event.payload;
    downloadProgress.value.status = data.status;
    downloadProgress.value.percent = data.progress;
    downloadProgress.value.speed = data.speed;
    downloadProgress.value.eta = data.eta;
    downloadProgress.value.currentFile = data.filename;
    downloadProgress.value.current = data.current;
    downloadProgress.value.total = data.total;
  });
});

onUnmounted(() => {
  if (unlistenProgress) {
    unlistenProgress();
  }
});

// Navegação entre passos
const canGoNext = computed(() => {
  switch (currentStep.value) {
    case 1:
      return urlData.value.url.trim() !== "";
    case 1.5:
      return !authData.value.needsAuth || authData.value.authVerified;
    case 2:
      return contentData.value.type !== "";
    case 2.5:
      return contentData.value.selectedItems.length > 0;
    case 3:
      return formatData.value.mode !== "" && formatData.value.quality !== "";
    case 4:
      return destinationData.value.path !== "";
    default:
      return false;
  }
});

function nextStep() {
  if (currentStep.value === 1) {
    currentStep.value = 1.5;
  } else if (currentStep.value === 1.5) {
    currentStep.value = 2;
  } else if (currentStep.value === 2) {
    // Se precisa selecionar itens
    if (contentData.value.type !== "video" && contentData.value.items.length > 1) {
      currentStep.value = 2.5;
    } else {
      currentStep.value = 3;
    }
  } else if (currentStep.value === 2.5) {
    currentStep.value = 3;
  } else if (currentStep.value === 3) {
    currentStep.value = 4;
  }
}

function prevStep() {
  if (currentStep.value === 1.5) {
    currentStep.value = 1;
  } else if (currentStep.value === 2) {
    currentStep.value = 1.5;
  } else if (currentStep.value === 2.5) {
    currentStep.value = 2;
  } else if (currentStep.value === 3) {
    if (contentData.value.type !== "video" && contentData.value.items.length > 1) {
      currentStep.value = 2.5;
    } else {
      currentStep.value = 2;
    }
  } else if (currentStep.value === 4) {
    currentStep.value = 3;
  }
}

function startDownload() {
  downloadProgress.value.active = true;
  downloadProgress.value.total = contentData.value.selectedItems.length || 1;
  downloadProgress.value.current = 0;
  downloadProgress.value.percent = 0;
  downloadProgress.value.currentFile = "Preparando download...";
  downloadProgress.value.speed = "";
  downloadProgress.value.eta = "";
  downloadProgress.value.status = "starting";
  
  // Chamar o comando Rust (o progresso vem via eventos)
  invoke("start_download", {
    options: {
      url: urlData.value.url,
      destination: destinationData.value.path,
      mode: formatData.value.mode,
      quality: formatData.value.quality,
      cookies_path: null,
      use_browser_cookies: authData.value.useBrowserCookies,
      selected_items: contentData.value.selectedItems.length > 0 
        ? contentData.value.selectedItems.map(item => typeof item === 'object' ? item.url : item)
        : null,
    }
  }).then((result) => {
    // Progresso final já é emitido pelo Rust
  }).catch((error) => {
    console.error("Erro no download:", error);
    downloadProgress.value.currentFile = `Erro: ${error}`;
    // Mostrar erro mas permitir novo download
  });
}

function resetWizard() {
  currentStep.value = 1;
  urlData.value = { url: "", radioLimit: 50 };
  authData.value = { needsAuth: false, useBrowserCookies: false, authVerified: false };
  contentData.value = { type: "", items: [], selectedItems: [] };
  formatData.value = { mode: "", quality: "", availableFormats: [] };
  destinationData.value = { path: "" };
  downloadProgress.value = { active: false, current: 0, total: 0, currentFile: "", percent: 0 };
}

// Gerar estilos aleatórios para os ícones flutuantes
function getIconStyle(index) {
  const seed = index * 7;
  const size = 40 + (seed % 50); // 40-90px
  const left = (seed * 13) % 100;
  const delay = (seed % 20);
  const duration = 15 + (seed % 15); // 15-30s
  const opacity = 0.1 + (seed % 30) / 100; // 0.1-0.4
  const isGlass = index % 3 === 0;
  const isRed = index % 2 === 0;
  
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${left}%`,
    animationDelay: `-${delay}s`,
    animationDuration: `${duration}s`,
    opacity: opacity,
    '--icon-bg': isGlass 
      ? 'rgba(255, 255, 255, 0.15)' 
      : isRed 
        ? 'rgba(233, 69, 96, 0.6)' 
        : 'rgba(255, 107, 107, 0.4)',
    '--icon-color': isGlass ? 'rgba(233, 69, 96, 0.8)' : 'white',
    '--icon-blur': isGlass ? '10px' : '0px',
  };
}

// Pular autenticação se não precisar
function skipAuth() {
  authData.value.needsAuth = false;
  authData.value.authVerified = true;
  nextStep();
}
</script>

<template>
  <!-- Fundo animado com ícones de música e vídeo descendo -->
  <div class="animated-background">
    <div class="download-icons">
      <div v-for="i in 20" :key="i" class="floating-icon" :style="getIconStyle(i)">
        <!-- Ícone de música (notas) -->
        <svg v-if="i % 3 === 0" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/>
        </svg>
        <!-- Ícone de vídeo/play -->
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

  <!-- Tela de Setup Inicial -->
  <SetupScreen v-if="!setupComplete" @ready="onSetupReady" />

  <!-- Aplicação Principal -->
  <main v-else class="container">
    <h1>🎬 YT-DLP GUI</h1>
    
    <!-- Indicador de passos -->
    <div class="steps-indicator">
      <div :class="['step-dot', { active: currentStep >= 1, current: currentStep === 1 }]">1</div>
      <div class="step-line" :class="{ active: currentStep > 1 }"></div>
      <div :class="['step-dot', { active: currentStep >= 1.5, current: currentStep === 1.5 }]">🔒</div>
      <div class="step-line" :class="{ active: currentStep > 1.5 }"></div>
      <div :class="['step-dot', { active: currentStep >= 2, current: currentStep >= 2 && currentStep < 3 }]">2</div>
      <div class="step-line" :class="{ active: currentStep >= 3 }"></div>
      <div :class="['step-dot', { active: currentStep >= 3, current: currentStep === 3 }]">3</div>
      <div class="step-line" :class="{ active: currentStep >= 4 }"></div>
      <div :class="['step-dot', { active: currentStep >= 4, current: currentStep === 4 }]">4</div>
    </div>

    <!-- Componentes de cada passo -->
    <div class="step-content">
      <Step1Url 
        v-if="currentStep === 1" 
        v-model:url="urlData.url"
        v-model:radioLimit="urlData.radioLimit"
        @analyze="nextStep"
      />
      
      <Step1_5Auth 
        v-if="currentStep === 1.5"
        v-model:needsAuth="authData.needsAuth"
        v-model:useBrowserCookies="authData.useBrowserCookies"
        v-model:authVerified="authData.authVerified"
        :url="urlData.url"
        @skip="skipAuth"
        @verified="nextStep"
      />

      <Step2Type 
        v-if="currentStep === 2"
        v-model:type="contentData.type"
        v-model:items="contentData.items"
        v-model:selectedItems="contentData.selectedItems"
        :url="urlData.url"
        :radioLimit="urlData.radioLimit"
        :useBrowserCookies="authData.useBrowserCookies"
      />

      <Step2_5Selection 
        v-if="currentStep === 2.5"
        v-model:selectedItems="contentData.selectedItems"
        :items="contentData.items"
        :type="contentData.type"
      />

      <Step3Format 
        v-if="currentStep === 3"
        v-model:mode="formatData.mode"
        v-model:quality="formatData.quality"
        v-model:availableFormats="formatData.availableFormats"
        :url="urlData.url"
        :selectedItems="contentData.selectedItems"
        :useBrowserCookies="authData.useBrowserCookies"
      />

      <Step4Destination 
        v-if="currentStep === 4 && !downloadProgress.active"
        v-model:path="destinationData.path"
        :mode="formatData.mode"
      />

      <StepProgress 
        v-if="downloadProgress.active"
        :progress="downloadProgress"
        :destination="destinationData.path"
        @done="resetWizard"
      />
    </div>

    <!-- Navegação -->
    <div class="navigation" v-if="!downloadProgress.active">
      <button 
        v-if="currentStep > 1" 
        @click="prevStep" 
        class="btn-secondary"
      >
        ← Voltar
      </button>
      <div v-else></div>
      
      <button 
        v-if="currentStep < 4"
        @click="nextStep" 
        :disabled="!canGoNext"
        class="btn-primary"
      >
        Próximo →
      </button>
      
      <button 
        v-if="currentStep === 4"
        @click="startDownload" 
        :disabled="!canGoNext"
        class="btn-download"
      >
        🚀 Iniciar Download
      </button>
    </div>
  </main>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');

:root {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  font-size: 16px;
  line-height: 1.5;
  font-weight: 400;
  color: #ffffff;
  background: linear-gradient(135deg, #0a0a12 0%, #1a1a2e 50%, #0f0f1a 100%);
  min-height: 100vh;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  margin: 0;
  min-height: 100vh;
  background: linear-gradient(135deg, #0a0a12 0%, #1a1a2e 50%, #0f0f1a 100%);
  position: relative;
  overflow-x: hidden;
}

/* Decoração de fundo para glassmorphism */
.animated-background {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
  background: linear-gradient(135deg, #0a0a12 0%, #1a1a2e 50%, #0f0f1a 100%);
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
    opacity: var(--final-opacity, 0.3);
  }
  50% {
    transform: translateY(50vh) rotate(180deg) scale(1.1);
  }
  95% {
    opacity: var(--final-opacity, 0.3);
  }
  100% {
    transform: translateY(120vh) rotate(360deg) scale(1);
    opacity: 0;
  }
}

/* Overlay com gradiente para suavizar */
.gradient-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: 
    radial-gradient(circle at 20% 80%, rgba(233, 69, 96, 0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(233, 69, 96, 0.1) 0%, transparent 40%),
    radial-gradient(circle at 50% 50%, rgba(0, 0, 0, 0.3) 0%, transparent 70%);
  pointer-events: none;
}

* {
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  overflow: hidden;
}

.container {
  margin: 0 auto;
  padding: 25px;
  max-width: 750px;
  height: 100vh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
  overflow: hidden;
}

h1 {
  text-align: center;
  margin-bottom: 25px;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #ff6b6b, #e94560, #ff8a80);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-shadow: 0 0 40px rgba(233, 69, 96, 0.3);
}

/* Indicador de passos estilo glassmorphism */
.steps-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 25px;
  gap: 8px;
  padding: 15px 25px;
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.step-dot {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  color: rgba(255, 255, 255, 0.4);
}

.step-dot.active {
  background: rgba(233, 69, 96, 0.2);
  border-color: rgba(233, 69, 96, 0.4);
  color: #ff6b6b;
}

.step-dot.current {
  background: linear-gradient(135deg, #e94560, #ff6b6b);
  border-color: transparent;
  color: white;
  transform: scale(1.1);
  box-shadow: 0 0 25px rgba(233, 69, 96, 0.5);
}

.step-line {
  width: 25px;
  height: 2px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  transition: all 0.4s;
}

.step-line.active {
  background: linear-gradient(90deg, #e94560, #ff6b6b);
  box-shadow: 0 0 10px rgba(233, 69, 96, 0.4);
}

/* Conteúdo principal glassmorphism */
.step-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 30px;
  margin-bottom: 20px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

/* Navegação */
.navigation {
  display: flex;
  justify-content: space-between;
  gap: 15px;
  flex-shrink: 0;
}

button {
  padding: 14px 28px;
  border-radius: 14px;
  border: none;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  transform: none !important;
}

.btn-primary {
  background: linear-gradient(135deg, #e94560, #ff6b6b);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 20px rgba(233, 69, 96, 0.4);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(233, 69, 96, 0.5);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

.btn-download {
  background: linear-gradient(135deg, #e94560, #ff6b6b);
  color: white;
  flex: 1;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 25px rgba(233, 69, 96, 0.5);
  font-size: 16px;
}

.btn-download:hover:not(:disabled) {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 8px 35px rgba(233, 69, 96, 0.6);
}

/* Inputs estilo glassmorphism */
input, select {
  width: 100%;
  padding: 14px 18px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: #ffffff;
  font-size: 15px;
  transition: all 0.3s;
}

input:focus, select:focus {
  outline: none;
  border-color: rgba(233, 69, 96, 0.5);
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 20px rgba(233, 69, 96, 0.2);
}

input::placeholder {
  color: rgba(255, 255, 255, 0.3);
}

label {
  display: block;
  margin-bottom: 10px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
}

.form-group {
  margin-bottom: 24px;
}

/* Scrollbar personalizada */
::-webkit-scrollbar {
  width: 8px;
}

::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.02);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: rgba(233, 69, 96, 0.3);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(233, 69, 96, 0.5);
}
</style>
