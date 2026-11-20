<script setup>
import { ref, onMounted, computed } from "vue";
import { invoke } from "@tauri-apps/api/core";

const props = defineProps({
  url: String,
  radioLimit: Number,
  useBrowserCookies: Boolean,
  cookiesPath: String,
  type: String,
  items: Array,
  selectedItems: Array,
  // Guardados no App para o passo voltar igual depois do "Voltar"
  detected: String,
  title: String,
  video: Object,
  choice: String,
});

const emit = defineEmits([
  "update:type",
  "update:items",
  "update:selectedItems",
  "update:detected",
  "update:title",
  "update:video",
  "update:choice",
]);

const loading = ref(true);
const error = ref("");
const detectedType = ref("");
const contentInfo = ref(null);
const userChoice = ref("");
const loadingMessage = ref("Analisando URL...");

// Detectar se é playlist/rádio pela URL
const isPlaylistUrl = computed(() => {
  return props.url.includes("list=") || 
         props.url.includes("playlist") || 
         props.url.includes("start_radio=1");
});

const isRadioUrl = computed(() => {
  return props.url.includes("list=RD") || props.url.includes("start_radio=1");
});

onMounted(async () => {
  // Já analisado: mostra o mesmo resultado e a mesma escolha, sem consultar de novo
  if (props.detected && props.items?.length) {
    detectedType.value = props.detected;
    contentInfo.value = { type: props.detected, title: props.title, video: props.video };
    userChoice.value = props.choice || "";
    loading.value = false;
    return;
  }
  await analyzeUrl();
});

async function analyzeUrl() {
  loading.value = true;
  error.value = "";
  
  // Mensagem dinâmica baseada no tipo de URL
  if (isRadioUrl.value) {
    loadingMessage.value = `Buscando até ${props.radioLimit} vídeos da rádio...`;
  } else if (isPlaylistUrl.value) {
    loadingMessage.value = "Analisando playlist... (pode demorar alguns segundos)";
  } else {
    loadingMessage.value = "Analisando vídeo...";
  }
  
  try {
    const result = await invoke("analyze_url", { 
      url: props.url,
      radioLimit: props.radioLimit,
      cookiesPath: props.cookiesPath || null,
      useBrowserCookies: props.useBrowserCookies || false
    });
    
    contentInfo.value = result;
    detectedType.value = result.type;
    userChoice.value = "";
    emit("update:detected", result.type);
    emit("update:title", result.title || "");
    emit("update:video", result.video || null);
    emit("update:choice", "");

    if (result.type === "video") {
      emit("update:type", "video");
      emit("update:items", [result.video]);
      emit("update:selectedItems", [result.video]);
    } else {
      emit("update:items", result.items || []);
      
      if (result.type === "video_with_playlist" || result.type === "video_with_radio") {
        // Não define o tipo ainda, espera escolha do usuário
      } else {
        emit("update:type", result.type);
        // Não seleciona automaticamente - deixa o usuário escolher no Step 2.5
        emit("update:selectedItems", []);
      }
    }
  } catch (e) {
    error.value = e.toString();
  }
  
  loading.value = false;
}

function selectOption(option) {
  const previous = userChoice.value;
  userChoice.value = option;
  emit("update:choice", option);

  if (option === "single") {
    emit("update:type", "video");
    emit("update:selectedItems", contentInfo.value?.video ? [contentInfo.value.video] : []);
  } else if (option === "playlist") {
    // "Baixar tudo" já leva todos os itens e pula a seleção
    emit("update:type", detectedType.value === "video_with_radio" ? "radio" : "playlist");
    emit("update:selectedItems", [...props.items]);
  } else if (option === "select") {
    emit("update:type", detectedType.value === "video_with_radio" ? "radio" : "playlist");
    // Só limpa se vier de outra escolha; ao voltar, a seleção feita continua
    if (previous !== "select") {
      emit("update:selectedItems", []);
    }
  }
}

function selectAll() {
  emit("update:selectedItems", [...props.items]);
}

function formatDuration(seconds) {
  if (!seconds) return "";
  const total = Math.round(seconds);
  const mins = Math.floor(total / 60);
  const secs = total % 60;
  return `${mins}:${secs.toString().padStart(2, "0")}`;
}
</script>

<template>
  <div class="step2">
    <h2>📋 Passo 2: Tipo de Conteúdo</h2>
    
    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>{{ loadingMessage }}</p>
      <div v-if="isPlaylistUrl" class="loading-hint">
        <span class="pulse-dot"></span>
        <span>{{ isRadioUrl ? 'Rádios podem ter muitos vídeos' : 'Buscando informações da playlist' }}</span>
      </div>
      <span v-else class="hint">Buscando informações do conteúdo</span>
    </div>

    <div v-else-if="error" class="error-box">
      <span class="icon">❌</span>
      <div>
        <strong>Erro ao analisar URL</strong>
        <p>{{ error }}</p>
        <button @click="analyzeUrl" class="btn-retry">🔄 Tentar novamente</button>
      </div>
    </div>

    <div v-else class="content-detected">
      <!-- Vídeo simples -->
      <div v-if="detectedType === 'video'" class="type-card active">
        <span class="type-icon">🎬</span>
        <div class="type-info">
          <strong>Vídeo Único</strong>
          <p class="video-title">{{ contentInfo?.video?.title }}</p>
          <span v-if="contentInfo?.video?.duration" class="duration">{{ formatDuration(contentInfo.video.duration) }}</span>
        </div>
      </div>

      <!-- Playlist simples -->
      <div v-else-if="detectedType === 'playlist'" class="type-card active">
        <span class="type-icon">📃</span>
        <div class="type-info">
          <strong>Playlist</strong>
          <p class="video-title">{{ contentInfo?.title }}</p>
          <span class="count">{{ items.length }} vídeos</span>
        </div>
      </div>

      <!-- Rádio/Mix simples -->
      <div v-else-if="detectedType === 'radio'" class="type-card active">
        <span class="type-icon">📻</span>
        <div class="type-info">
          <strong>Rádio / Mix</strong>
          <p class="video-title">{{ contentInfo?.title }}</p>
          <span class="count">{{ items.length }} vídeos encontrados</span>
        </div>
      </div>

      <!-- Vídeo com Playlist ou Rádio - Escolha do usuário -->
      <div v-else-if="detectedType === 'video_with_playlist' || detectedType === 'video_with_radio'" class="choice-section">
        <div class="info-box">
          <span class="icon">ℹ️</span>
          <div>
            <strong>Vídeo dentro de {{ detectedType === 'video_with_radio' ? 'uma Rádio' : 'uma Playlist' }}</strong>
            <p>O link contém um vídeo específico, mas também faz parte de uma coleção.</p>
          </div>
        </div>

        <p class="choice-label">O que você deseja fazer?</p>

        <div class="choice-options">
          <div 
            :class="['choice-card', { active: userChoice === 'single' }]"
            @click="selectOption('single')"
          >
            <span class="choice-icon">🎬</span>
            <strong>Baixar só este vídeo</strong>
            <p>{{ contentInfo?.video?.title }}</p>
          </div>

          <div 
            :class="['choice-card', { active: userChoice === 'playlist' }]"
            @click="selectOption('playlist')"
          >
            <span class="choice-icon">📥</span>
            <strong>Baixar tudo</strong>
            <p>Todos os {{ items.length }} vídeos da {{ detectedType === 'video_with_radio' ? 'rádio' : 'playlist' }}</p>
          </div>

          <div 
            :class="['choice-card', { active: userChoice === 'select' }]"
            @click="selectOption('select')"
          >
            <span class="choice-icon">✅</span>
            <strong>Selecionar itens</strong>
            <p>Escolher quais vídeos baixar</p>
          </div>
        </div>
      </div>

      <!-- Resumo se já tiver tipo definido -->
      <div v-if="type && (detectedType === 'playlist' || detectedType === 'radio')" class="summary">
        <div class="summary-info">
          <span class="summary-icon">📋</span>
          <div>
            <strong>{{ items.length }} vídeos</strong> encontrados
            <p>Clique em "Próximo" para escolher quais baixar</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.step2 h2 {
  margin-top: 0;
  font-size: 22px;
  font-weight: 600;
  background: linear-gradient(135deg, #ff6b6b, #e94560);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 50px 20px;
}

.spinner {
  width: 55px;
  height: 55px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #e94560;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading p {
  color: rgba(255, 255, 255, 0.8);
  font-size: 16px;
  margin-top: 20px;
}

.hint {
  color: rgba(255, 255, 255, 0.4);
  font-size: 14px;
  margin-top: 8px;
}

.loading-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 15px;
  padding: 12px 20px;
  background: rgba(243, 156, 18, 0.1);
  border-radius: 10px;
  border: 1px solid rgba(243, 156, 18, 0.2);
}

.loading-hint span:last-child {
  color: rgba(243, 156, 18, 0.9);
  font-size: 13px;
}

.pulse-dot {
  width: 10px;
  height: 10px;
  background: #f39c12;
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

.error-box {
  display: flex;
  gap: 15px;
  padding: 20px;
  background: rgba(231, 76, 60, 0.1);
  border-radius: 16px;
  border: 1px solid rgba(231, 76, 60, 0.3);
}

.error-box .icon {
  font-size: 30px;
}

.error-box p {
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  margin: 5px 0 15px 0;
}

.btn-retry {
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.3s;
}

.btn-retry:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 20px rgba(231, 76, 60, 0.4);
}

.type-card {
  display: flex;
  gap: 18px;
  padding: 22px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  transition: all 0.3s;
}

.type-card.active {
  border-color: rgba(233, 69, 96, 0.5);
  background: rgba(233, 69, 96, 0.1);
  box-shadow: 0 0 30px rgba(233, 69, 96, 0.15);
}

.type-icon {
  font-size: 42px;
  width: 65px;
  height: 65px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(233, 69, 96, 0.15);
  border-radius: 14px;
}

.type-info {
  flex: 1;
}

.type-info strong {
  font-size: 18px;
  background: linear-gradient(135deg, #ff6b6b, #e94560);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.video-title {
  color: rgba(255, 255, 255, 0.7);
  margin: 8px 0;
  font-size: 14px;
  line-height: 1.4;
}

.duration, .count {
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
}

.info-box {
  display: flex;
  gap: 15px;
  padding: 18px;
  background: rgba(52, 152, 219, 0.1);
  border-radius: 14px;
  margin-bottom: 22px;
  border: 1px solid rgba(52, 152, 219, 0.2);
}

.info-box .icon {
  font-size: 26px;
}

.info-box strong {
  color: #5dade2;
}

.info-box p {
  color: rgba(255, 255, 255, 0.5);
  margin: 5px 0 0 0;
  font-size: 14px;
}

.choice-label {
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 15px;
  font-size: 14px;
}

.choice-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.choice-card {
  display: flex;
  flex-direction: column;
  padding: 18px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  cursor: pointer;
  transition: all 0.3s;
}

.choice-card:hover {
  border-color: rgba(233, 69, 96, 0.3);
  background: rgba(255, 255, 255, 0.05);
}

.choice-card.active {
  border-color: rgba(233, 69, 96, 0.6);
  background: rgba(233, 69, 96, 0.1);
  box-shadow: 0 0 25px rgba(233, 69, 96, 0.15);
}

.choice-icon {
  font-size: 26px;
  margin-bottom: 8px;
}

.choice-card strong {
  color: #fff;
  font-size: 15px;
}

.choice-card p {
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
  margin: 6px 0 0 0;
}

.summary {
  margin-top: 22px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary p {
  margin: 0;
  color: rgba(255, 255, 255, 0.6);
}

.summary strong {
  color: #e94560;
}

.btn-select-all {
  background: rgba(233, 69, 96, 0.2);
  color: #ff6b6b;
  border: 1px solid rgba(233, 69, 96, 0.3);
  padding: 10px 18px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s;
}

.btn-select-all:hover {
  background: rgba(233, 69, 96, 0.3);
  transform: translateY(-2px);
}

.summary-info {
  display: flex;
  align-items: center;
  gap: 15px;
}

.summary-icon {
  font-size: 32px;
}

.summary-info strong {
  color: #e94560;
  font-size: 16px;
}

.summary-info p {
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  margin: 4px 0 0 0;
}
</style>
