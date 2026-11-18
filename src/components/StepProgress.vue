<script setup>
import { computed } from "vue";
import { invoke } from "@tauri-apps/api/core";

const props = defineProps({
  progress: Object,
  destination: String,
});

const emit = defineEmits(["done"]);

const progressPercent = computed(() => {
  return Math.round(props.progress.percent || 0);
});

const isCompleted = computed(() => {
  return props.progress.status === "completed";
});

const statusText = computed(() => {
  switch (props.progress.status) {
    case "starting":
      return "Iniciando download...";
    case "downloading":
      return "Baixando...";
    case "merging":
      return "Mesclando vídeo e áudio...";
    case "converting":
      return "Convertendo para MP3...";
    case "completed":
      return "Download concluído!";
    case "error":
      return "Erro no download";
    default:
      return "Processando...";
  }
});

const statusEmoji = computed(() => {
  switch (props.progress.status) {
    case "starting":
      return "🚀";
    case "downloading":
      return "⬇️";
    case "merging":
      return "🔗";
    case "converting":
      return "🎵";
    case "completed":
      return "✅";
    case "error":
      return "❌";
    default:
      return "⏳";
  }
});

async function openDownloadsFolder() {
  if (props.destination) {
    try {
      await invoke("open_folder", { path: props.destination });
    } catch (err) {
      console.error("Erro ao abrir pasta:", err);
    }
  }
}
</script>

<template>
  <div class="step-progress">
    <div v-if="!isCompleted" class="downloading">
      <!-- Contador grande no topo quando tem múltiplos arquivos -->
      <div class="main-counter" v-if="progress.total > 1">
        <span class="counter-current">{{ progress.current || 0 }}</span>
        <span class="counter-separator">/</span>
        <span class="counter-total">{{ progress.total }}</span>
      </div>

      <h2>{{ statusEmoji }} {{ statusText }}</h2>
      
      <div class="progress-container">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
        <span class="progress-text">{{ progressPercent }}%</span>
      </div>

      <div class="current-file">
        <span class="label">Arquivo atual:</span>
        <strong>{{ progress.currentFile || 'Iniciando...' }}</strong>
      </div>

      <div class="stats-row" v-if="progress.speed || progress.eta">
        <div class="stat" v-if="progress.speed">
          <span class="label">Velocidade</span>
          <span class="value speed">{{ progress.speed }}</span>
        </div>
        <div class="stat" v-if="progress.eta">
          <span class="label">Tempo restante</span>
          <span class="value eta">{{ progress.eta }}</span>
        </div>
      </div>
    </div>

    <div v-else class="completed">
      <div class="success-icon">✅</div>
      <h2>Download Concluído!</h2>
      <p>{{ progress.total > 1 ? `Todos os ${progress.total} arquivos foram baixados com sucesso.` : 'Arquivo baixado com sucesso!' }}</p>
      
      <div class="actions">
        <button @click="openDownloadsFolder" class="btn-open">
          📂 Ver Downloads
        </button>
        <button @click="$emit('done')" class="btn-new">
          🔄 Novo Download
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.step-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 30px;
  min-height: 300px;
}

.downloading {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.downloading h2 {
  font-size: 22px;
  font-weight: 600;
  background: linear-gradient(135deg, #ff6b6b, #e94560);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0 0 30px 0;
}

/* Contador principal grande */
.main-counter {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
  margin-bottom: 20px;
  padding: 20px 40px;
  background: rgba(233, 69, 96, 0.1);
  border-radius: 20px;
  border: 1px solid rgba(233, 69, 96, 0.3);
  box-shadow: 0 0 40px rgba(233, 69, 96, 0.15);
}

.counter-current {
  font-size: 56px;
  font-weight: 800;
  background: linear-gradient(135deg, #ff6b6b, #e94560);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-shadow: 0 0 40px rgba(233, 69, 96, 0.5);
  line-height: 1;
}

.counter-separator {
  font-size: 36px;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.3);
  margin: 0 5px;
}

.counter-total {
  font-size: 36px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
}

.progress-container {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 30px;
  width: 100%;
  max-width: 500px;
}

.progress-bar {
  flex: 1;
  height: 12px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #e94560, #ff6b6b, #e94560);
  background-size: 200% 100%;
  border-radius: 6px;
  transition: width 0.3s ease;
  box-shadow: 0 0 20px rgba(233, 69, 96, 0.5);
  animation: shimmer 2s infinite linear;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.progress-text {
  font-size: 24px;
  font-weight: 700;
  color: #e94560;
  min-width: 70px;
  text-align: right;
  text-shadow: 0 0 20px rgba(233, 69, 96, 0.4);
}

.current-file {
  background: rgba(255, 255, 255, 0.03);
  padding: 20px;
  border-radius: 16px;
  margin-bottom: 25px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  width: 100%;
  max-width: 500px;
}

.current-file .label {
  display: block;
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.current-file strong {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  word-break: break-all;
  font-weight: 500;
}

.stats-row {
  display: flex;
  justify-content: center;
  gap: 15px;
  flex-wrap: wrap;
}

.stat {
  background: rgba(255, 255, 255, 0.03);
  padding: 18px 28px;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  min-width: 120px;
}

.stat .label {
  display: block;
  color: rgba(255, 255, 255, 0.4);
  font-size: 11px;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat .value {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #ff6b6b, #e94560);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat .value.speed {
  font-size: 18px;
  background: linear-gradient(135deg, #00b894, #00cec9);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat .value.eta {
  font-size: 18px;
  background: linear-gradient(135deg, #fdcb6e, #f39c12);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat .value .of {
  font-size: 14px;
  opacity: 0.6;
}

.completed {
  padding: 50px 25px;
}

.success-icon {
  font-size: 90px;
  margin-bottom: 25px;
  display: block;
  filter: drop-shadow(0 0 30px rgba(0, 184, 148, 0.4));
  animation: bounce 0.6s ease;
}

@keyframes bounce {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.completed h2 {
  color: #00b894;
  margin-bottom: 12px;
  font-size: 26px;
  font-weight: 700;
}

.completed p {
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 35px;
  font-size: 15px;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 15px;
  flex-wrap: wrap;
}

.btn-open {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.15);
  padding: 16px 35px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.btn-open:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.3);
  transform: translateY(-3px);
}

.btn-new {
  background: linear-gradient(135deg, #e94560, #ff6b6b);
  color: white;
  border: none;
  padding: 16px 35px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 8px 30px rgba(233, 69, 96, 0.4);
}

.btn-new:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 40px rgba(233, 69, 96, 0.5);
}
</style>
