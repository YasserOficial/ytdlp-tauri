<script setup>
import { ref, onMounted } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { open } from "@tauri-apps/plugin-dialog";

const props = defineProps({
  path: String,
  mode: String,
});

const emit = defineEmits(["update:path"]);

const recentPaths = ref([]);
const defaultPath = ref("");

onMounted(async () => {
  // Buscar pasta padrão e recentes
  try {
    const result = await invoke("get_download_paths");
    defaultPath.value = result.default;
    recentPaths.value = result.recent || [];
    
    if (!props.path && defaultPath.value) {
      emit("update:path", defaultPath.value);
    }
  } catch (e) {
    console.error("Erro ao buscar caminhos:", e);
  }
});

async function selectFolder() {
  try {
    const selected = await open({
      directory: true,
      multiple: false,
      defaultPath: props.path || defaultPath.value
    });
    
    if (selected) {
      emit("update:path", selected);
    }
  } catch (e) {
    console.error("Erro ao selecionar pasta:", e);
  }
}

function selectRecent(path) {
  emit("update:path", path);
}

function formatPath(path) {
  if (!path) return "";
  // Mostrar apenas as últimas partes do caminho
  const parts = path.split(/[\\\/]/);
  if (parts.length <= 3) return path;
  return "..." + parts.slice(-3).join("\\");
}
</script>

<template>
  <div class="step4">
    <h2>📁 Passo 4: Destino do Download</h2>
    <p class="description">
      Escolha onde salvar {{ mode === 'audio' ? 'os arquivos de áudio' : 'os vídeos' }}
    </p>

    <div class="destination-box">
      <div class="current-path" v-if="path">
        <span class="icon">📂</span>
        <div class="path-info">
          <span class="label">Pasta selecionada:</span>
          <strong class="path">{{ path }}</strong>
        </div>
      </div>

      <div class="no-path" v-else>
        <span class="icon">📁</span>
        <p>Nenhuma pasta selecionada</p>
      </div>

      <button @click="selectFolder" class="btn-select">
        📂 {{ path ? 'Alterar pasta' : 'Selecionar pasta' }}
      </button>
    </div>

    <!-- Pastas recentes -->
    <div v-if="recentPaths.length > 0" class="recent-section">
      <h3>📋 Pastas recentes</h3>
      <div class="recent-list">
        <div 
          v-for="(recentPath, index) in recentPaths" 
          :key="index"
          :class="['recent-item', { active: path === recentPath }]"
          @click="selectRecent(recentPath)"
        >
          <span class="folder-icon">📁</span>
          <span class="path-text">{{ formatPath(recentPath) }}</span>
        </div>
      </div>
    </div>

    <!-- Dicas -->
    <div class="tips">
      <h3>💡 Dicas</h3>
      <ul>
        <li>Os arquivos serão salvos com o nome original do vídeo</li>
        <li v-if="mode === 'audio'">Formato de saída: <strong>MP3</strong></li>
        <li v-else>Formato de saída: <strong>MP4</strong></li>
        <li>Arquivos duplicados serão renomeados automaticamente</li>
      </ul>
    </div>

    <!-- Resumo final -->
    <div class="final-summary" v-if="path">
      <span class="icon">🚀</span>
      <div>
        <strong>Tudo pronto!</strong>
        <p>Clique em "Iniciar Download" para começar.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.step4 h2 {
  margin-top: 0;
  font-size: 22px;
  font-weight: 600;
  background: linear-gradient(135deg, #ff6b6b, #e94560);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.description {
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 25px;
  font-size: 14px;
}

.destination-box {
  background: rgba(255, 255, 255, 0.03);
  border-radius: 18px;
  padding: 24px;
  margin-bottom: 24px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.current-path {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.current-path .icon {
  font-size: 32px;
  width: 55px;
  height: 55px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 184, 148, 0.15);
  border-radius: 14px;
}

.path-info {
  flex: 1;
}

.path-info .label {
  display: block;
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.path-info .path {
  display: block;
  color: #00b894;
  word-break: break-all;
  font-size: 14px;
  font-weight: 500;
}

.no-path {
  text-align: center;
  padding: 30px;
  color: rgba(255, 255, 255, 0.3);
}

.no-path .icon {
  font-size: 50px;
  display: block;
  margin-bottom: 15px;
  opacity: 0.5;
}

.btn-select {
  width: 100%;
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  backdrop-filter: blur(10px);
}

.btn-select:hover {
  background: rgba(233, 69, 96, 0.15);
  border-color: rgba(233, 69, 96, 0.3);
  transform: translateY(-2px);
}

.recent-section {
  margin-bottom: 24px;
}

.recent-section h3 {
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 14px;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.04);
  cursor: pointer;
  transition: all 0.25s;
}

.recent-item:hover {
  background: rgba(255, 255, 255, 0.04);
  transform: translateX(4px);
}

.recent-item.active {
  border-color: rgba(233, 69, 96, 0.4);
  background: rgba(233, 69, 96, 0.08);
}

.folder-icon {
  font-size: 20px;
}

.path-text {
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tips {
  background: rgba(255, 255, 255, 0.02);
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 24px;
  border: 1px solid rgba(255, 255, 255, 0.04);
}

.tips h3 {
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  font-weight: 500;
  margin-top: 0;
  margin-bottom: 14px;
}

.tips ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.tips li {
  padding: 8px 0;
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.tips li::before {
  content: "•";
  color: #e94560;
  font-size: 18px;
}

.tips li strong {
  color: #ff8a80;
}

.final-summary {
  display: flex;
  gap: 20px;
  padding: 24px;
  background: rgba(0, 184, 148, 0.08);
  border-radius: 18px;
  border: 1px solid rgba(0, 184, 148, 0.2);
  animation: pulse-glow 2s ease-in-out infinite;
}

@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 0 20px rgba(0, 184, 148, 0.1); }
  50% { box-shadow: 0 0 35px rgba(0, 184, 148, 0.2); }
}

.final-summary .icon {
  font-size: 36px;
}

.final-summary strong {
  display: block;
  color: #00b894;
  font-size: 18px;
  font-weight: 600;
}

.final-summary p {
  color: rgba(255, 255, 255, 0.6);
  margin: 6px 0 0 0;
  font-size: 14px;
}
</style>
