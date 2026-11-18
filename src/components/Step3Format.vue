<script setup>
import { ref, onMounted, computed } from "vue";
import { invoke } from "@tauri-apps/api/core";

const props = defineProps({
  url: String,
  selectedItems: Array,
  useBrowserCookies: Boolean,
  mode: String,
  quality: String,
  availableFormats: Array,
});

const emit = defineEmits(["update:mode", "update:quality", "update:availableFormats"]);

const loading = ref(true);
const error = ref("");
const formats = ref({ video: [], audio: [] });

// Calcular duração total dos itens selecionados (em segundos)
const totalDuration = computed(() => {
  if (!props.selectedItems || props.selectedItems.length === 0) return 0;
  
  const sum = props.selectedItems.reduce((acc, item) => acc + (item.duration || 0), 0);
  
  // Se não tiver duração, estimar 4 minutos (240s) por item
  if (sum === 0) {
    return props.selectedItems.length * 240;
  }
  
  return sum;
});

// Quantidade de itens
const itemCount = computed(() => {
  return props.selectedItems?.length || 0;
});

// Estimativas de bitrate médio por qualidade (em kbps)
const bitrateEstimates = {
  // Vídeo (vídeo + áudio combinados)
    video: {
    best: 2500,
    "1080p": 2500,
    "720p": 1200,
    "480p": 700,
    "360p": 400,
  },
  // Áudio (kbps)
  audio: {
    best: 256,
    "320": 320,
    "256": 256,
    "192": 192,
    "128": 128,
  }
};

// Calcular tamanho estimado em MB
const estimatedSize = computed(() => {
  if (!props.mode || !props.quality || itemCount.value === 0) return 0;

  const knownBytes = (props.selectedItems || []).reduce((acc, item) => acc + (item.filesize || 0), 0);
  if (props.mode === "video" && knownBytes > 0) {
    const scale = { best: 1, "1080p": 0.7, "720p": 0.4, "480p": 0.2, "360p": 0.12 }[props.quality] || 1;
    return (knownBytes / 1024 / 1024) * scale;
  }
  
  const bitrates = props.mode === 'audio' ? bitrateEstimates.audio : bitrateEstimates.video;
  const bitrate = bitrates[props.quality] || bitrates.best;
  
  // Tamanho = (bitrate em kbps * duração em segundos) / 8 / 1024 = MB
  const sizeMB = (bitrate * totalDuration.value) / 8 / 1024;
  return sizeMB;
});

// Flag para indicar se é estimativa aproximada (sem duração real)
const isApproximate = computed(() => {
  if (!props.selectedItems || props.selectedItems.length === 0) return true;
  const realDuration = props.selectedItems.reduce((acc, item) => acc + (item.duration || 0), 0);
  return realDuration === 0;
});

// Formatar tamanho para exibição
const formattedSize = computed(() => {
  const size = estimatedSize.value;
  if (size === 0) return "";
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} GB`;
  }
  return `${size.toFixed(0)} MB`;
});

// Cor baseada no tamanho: verde <= 300MB, laranja <= 1GB, vermelho > 1GB
const sizeColor = computed(() => {
  const size = estimatedSize.value;
  if (size === 0) return "gray";
  if (size <= 300) return "#22c55e";      // Verde
  if (size <= 1024) return "#f59e0b";     // Laranja
  return "#ef4444";                        // Vermelho
});

const sizeLabel = computed(() => {
  const size = estimatedSize.value;
  if (size === 0) return "";
  if (size <= 300) return "Tamanho pequeno";
  if (size <= 1024) return "Tamanho médio";
  return "Tamanho grande";
});

onMounted(async () => {
  await fetchFormats();
});

async function fetchFormats() {
  loading.value = true;
  error.value = "";
  
  try {
    // Pegar formatos do primeiro item selecionado como referência
    const sampleUrl = props.selectedItems[0]?.url || props.url;
    
    const result = await invoke("get_formats", { 
      url: sampleUrl,
      cookiesPath: null,
      useBrowserCookies: props.useBrowserCookies || false
    });
    
    formats.value = result;
    emit("update:availableFormats", result);
  } catch (e) {
    // Se falhar, usar formatos padrão (o yt-dlp vai lidar com a seleção)
    console.warn("Não foi possível obter formatos reais, usando padrões:", e);
    formats.value = {
      video: ["best", "1080p", "720p", "480p", "360p"],
      audio: ["best", "320", "256", "192", "128"]
    };
    emit("update:availableFormats", formats.value);
  }
  
  loading.value = false;
}

function selectMode(m) {
  emit("update:mode", m);
  emit("update:quality", ""); // Reset quality when changing mode
}

function selectQuality(q) {
  emit("update:quality", q);
}

const videoQualities = computed(() => {
  const qualities = [
    { id: "best", label: "Melhor qualidade", desc: "Maior resolução disponível" },
    { id: "1080p", label: "1080p (Full HD)", desc: "até 1080p de altura" },
    { id: "720p", label: "720p (HD)", desc: "até 720p de altura" },
    { id: "480p", label: "480p (SD)", desc: "até 480p de altura" },
    { id: "360p", label: "360p", desc: "até 360p de altura" },
  ];
  return qualities;
});

const audioQualities = computed(() => {
  const qualities = [
    { id: "best", label: "Melhor qualidade", desc: "Maior bitrate disponível" },
    { id: "320", label: "320 kbps", desc: "Alta qualidade" },
    { id: "256", label: "256 kbps", desc: "Boa qualidade" },
    { id: "192", label: "192 kbps", desc: "Qualidade média" },
    { id: "128", label: "128 kbps", desc: "Qualidade padrão" },
  ];
  return qualities;
});
</script>

<template>
  <div class="step3">
    <h2>🎚 Passo 3: Formato e Qualidade</h2>

    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>Buscando formatos disponíveis...</p>
    </div>

    <div v-else-if="error" class="error-box">
      <span class="icon">❌</span>
      <div>
        <strong>Erro ao buscar formatos</strong>
        <p>{{ error }}</p>
        <button @click="fetchFormats" class="btn-retry">🔄 Tentar novamente</button>
      </div>
    </div>

    <div v-else class="format-selection">
      <!-- Seleção de modo -->
      <div class="mode-selection">
        <h3>O que você deseja baixar?</h3>
        
        <div class="mode-options">
          <div 
            :class="['mode-card', { active: mode === 'audio' }]"
            @click="selectMode('audio')"
          >
            <span class="mode-icon">🎵</span>
            <div class="mode-info">
              <strong>Apenas Áudio</strong>
              <p>Extrair somente a música (MP3)</p>
            </div>
          </div>

          <div 
            :class="['mode-card', { active: mode === 'video' }]"
            @click="selectMode('video')"
          >
            <span class="mode-icon">🎬</span>
            <div class="mode-info">
              <strong>Vídeo Completo</strong>
              <p>Baixar vídeo com áudio (MP4)</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Seleção de qualidade -->
      <div v-if="mode" class="quality-selection">
        <h3>Selecione a qualidade:</h3>
        
        <!-- Qualidades de áudio -->
        <div v-if="mode === 'audio'" class="quality-options">
          <div 
            v-for="q in audioQualities"
            :key="q.id"
            :class="['quality-card', { active: quality === q.id }]"
            @click="selectQuality(q.id)"
          >
            <div class="quality-radio">
              <span v-if="quality === q.id">●</span>
              <span v-else>○</span>
            </div>
            <div class="quality-info">
              <strong>{{ q.label }}</strong>
              <span>{{ q.desc }}</span>
            </div>
          </div>
        </div>

        <!-- Qualidades de vídeo -->
        <div v-if="mode === 'video'" class="quality-options">
          <div 
            v-for="q in videoQualities"
            :key="q.id"
            :class="['quality-card', { active: quality === q.id }]"
            @click="selectQuality(q.id)"
          >
            <div class="quality-radio">
              <span v-if="quality === q.id">●</span>
              <span v-else>○</span>
            </div>
            <div class="quality-info">
              <strong>{{ q.label }}</strong>
              <span>{{ q.desc }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Resumo -->
      <div v-if="mode && quality" class="summary">
        <span class="icon">✅</span>
        <div>
          <strong>Configuração selecionada:</strong>
          <p>
            {{ mode === 'audio' ? '🎵 Áudio' : '🎬 Vídeo' }} 
            em {{ quality === 'best' ? 'melhor qualidade' : quality }}
            para {{ selectedItems.length }} {{ selectedItems.length === 1 ? 'item' : 'itens' }}
          </p>
        </div>
      </div>

      <!-- Estimativa de tamanho -->
      <div v-if="mode && quality && itemCount > 0" class="size-estimate">
        <div class="size-indicator" :style="{ borderColor: sizeColor }">
          <div class="size-icon" :style="{ backgroundColor: sizeColor }">
            <span v-if="estimatedSize <= 300">📦</span>
            <span v-else-if="estimatedSize <= 1024">📁</span>
            <span v-else>⚠️</span>
          </div>
          <div class="size-info">
            <div class="size-value" :style="{ color: sizeColor }">
              ~{{ formattedSize }}
            </div>
            <div class="size-label">{{ sizeLabel }}</div>
          </div>
          <div class="size-bar">
            <div 
              class="size-bar-fill" 
              :style="{ 
                width: Math.min(estimatedSize / 1024 * 100, 100) + '%',
                backgroundColor: sizeColor 
              }"
            ></div>
          </div>
        </div>
        <p class="size-disclaimer">
          <span v-if="isApproximate">* Estimativa aproximada (4min por item). O tamanho real pode variar bastante.</span>
          <span v-else>* Estimativa baseada na duração total. O tamanho real pode variar.</span>
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.step3 h2 {
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
  padding: 50px;
}

.spinner {
  width: 50px;
  height: 50px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #e94560;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading p {
  color: rgba(255, 255, 255, 0.5);
  margin-top: 20px;
}

.error-box {
  display: flex;
  gap: 18px;
  padding: 22px;
  background: rgba(231, 76, 60, 0.1);
  border-radius: 16px;
  border: 1px solid rgba(231, 76, 60, 0.2);
}

.error-box .icon {
  font-size: 32px;
}

.error-box strong {
  color: #ff6b6b;
}

.error-box p {
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
  margin: 8px 0 18px 0;
}

.btn-retry {
  background: rgba(231, 76, 60, 0.2);
  color: #ff6b6b;
  border: 1px solid rgba(231, 76, 60, 0.3);
  padding: 10px 20px;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s;
}

.btn-retry:hover {
  background: rgba(231, 76, 60, 0.3);
}

.mode-selection h3,
.quality-selection h3 {
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 18px;
}

.mode-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 15px;
  margin-bottom: 30px;
}

.mode-card {
  display: flex;
  gap: 16px;
  padding: 22px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.mode-card:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

.mode-card.active {
  border-color: rgba(233, 69, 96, 0.5);
  background: rgba(233, 69, 96, 0.1);
  box-shadow: 0 0 30px rgba(233, 69, 96, 0.15);
}

.mode-icon {
  font-size: 38px;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 14px;
}

.mode-card.active .mode-icon {
  background: rgba(233, 69, 96, 0.2);
}

.mode-info strong {
  display: block;
  color: #fff;
  margin-bottom: 6px;
  font-size: 15px;
}

.mode-info p {
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
  margin: 0;
}

.quality-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quality-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  cursor: pointer;
  transition: all 0.25s;
}

.quality-card:hover {
  background: rgba(255, 255, 255, 0.04);
  transform: translateX(4px);
}

.quality-card.active {
  border-color: rgba(233, 69, 96, 0.4);
  background: rgba(233, 69, 96, 0.08);
}

.quality-radio {
  font-size: 18px;
  color: #e94560;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 50%;
}

.quality-card.active .quality-radio {
  background: #e94560;
  color: white;
}

.quality-info {
  flex: 1;
}

.quality-info strong {
  display: block;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  font-weight: 500;
}

.quality-info span {
  color: rgba(255, 255, 255, 0.35);
  font-size: 12px;
}

.summary {
  display: flex;
  gap: 18px;
  margin-top: 28px;
  padding: 20px;
  background: rgba(0, 184, 148, 0.08);
  border-radius: 16px;
  border: 1px solid rgba(0, 184, 148, 0.2);
}

.summary .icon {
  font-size: 28px;
}

.summary strong {
  display: block;
  color: #00b894;
  font-size: 14px;
}

.summary p {
  color: rgba(255, 255, 255, 0.6);
  margin: 6px 0 0 0;
  font-size: 13px;
}

/* Estimativa de tamanho */
.size-estimate {
  margin-top: 20px;
}

.size-indicator {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 16px;
  border: 2px solid;
  flex-wrap: wrap;
}

.size-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.size-info {
  flex: 1;
  min-width: 120px;
}

.size-value {
  font-size: 24px;
  font-weight: 700;
}

.size-label {
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  margin-top: 2px;
}

.size-bar {
  width: 100%;
  height: 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  overflow: hidden;
  margin-top: 8px;
}

.size-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease, background-color 0.3s ease;
}

.size-disclaimer {
  color: rgba(255, 255, 255, 0.35);
  font-size: 11px;
  margin-top: 8px;
  text-align: center;
  font-style: italic;
}
</style>
