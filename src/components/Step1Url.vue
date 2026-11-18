<script setup>
import { ref, onUnmounted } from "vue";

const props = defineProps({
  url: String,
  radioLimit: Number,
});

const emit = defineEmits(["update:url", "update:radioLimit", "analyze"]);

const localUrl = ref(props.url);
const localRadioLimit = ref(props.radioLimit);
const showRadioOptions = ref(false);

// Controle do número com segurar - MUITO mais rápido
let holdInterval = null;
let holdTimeout = null;
let holdCount = 0;

function updateUrl(e) {
  localUrl.value = e.target.value;
  emit("update:url", localUrl.value);
  
  // Detecta se parece ser uma rádio/mix do YouTube
  const isRadio = localUrl.value.includes("&list=RD") || 
                  localUrl.value.includes("start_radio=1") ||
                  localUrl.value.includes("/playlist?list=RD");
  showRadioOptions.value = isRadio;
}

function setRadioLimit(value) {
  const newValue = Math.max(1, Math.min(1000, value));
  localRadioLimit.value = newValue;
  emit("update:radioLimit", newValue);
}

function increment(amount = 1) {
  setRadioLimit(localRadioLimit.value + amount);
}

function decrement(amount = 1) {
  setRadioLimit(localRadioLimit.value - amount);
}

function startHold(direction) {
  const baseAction = direction === 'up' ? increment : decrement;
  baseAction(1); // Ação imediata
  holdCount = 0;
  
  // Começa a repetir após 200ms (mais rápido)
  holdTimeout = setTimeout(() => {
    holdInterval = setInterval(() => {
      holdCount++;
      // Acelera baseado em quantas vezes já repetiu
      let step = 1;
      if (holdCount > 30) step = 10;      // Após ~1.5s: +10
      else if (holdCount > 15) step = 5;  // Após ~0.75s: +5
      else if (holdCount > 5) step = 2;   // Após ~0.25s: +2
      
      baseAction(step);
    }, 50); // Intervalo fixo de 50ms (20x por segundo)
  }, 200);
}

function stopHold() {
  if (holdTimeout) {
    clearTimeout(holdTimeout);
    holdTimeout = null;
  }
  if (holdInterval) {
    clearInterval(holdInterval);
    holdInterval = null;
  }
  holdCount = 0;
}

function handleAnalyze() {
  if (localUrl.value.trim()) {
    emit("analyze");
  }
}

function handlePaste() {
  navigator.clipboard.readText().then(text => {
    localUrl.value = text;
    emit("update:url", text);
    updateUrl({ target: { value: text } });
  });
}

// Presets rápidos
function setPreset(value) {
  setRadioLimit(value);
}

onUnmounted(() => {
  stopHold();
});
</script>

<template>
  <div class="step1">
    <h2>📎 Passo 1: Cole o Link</h2>
    <p class="description">Cole a URL do YouTube (vídeo, playlist ou rádio/mix)</p>
    
    <div class="form-group">
      <label for="url">URL do YouTube</label>
      <div class="input-with-button">
        <input 
          id="url"
          type="text" 
          :value="localUrl"
          @input="updateUrl"
          placeholder="https://www.youtube.com/watch?v=..."
          @keyup.enter="handleAnalyze"
        />
        <button type="button" @click="handlePaste" class="btn-paste" title="Colar da área de transferência">
          📋
        </button>
      </div>
    </div>

    <div v-if="showRadioOptions" class="radio-options">
      <div class="info-box">
        <span class="icon">📻</span>
        <div>
          <strong>Rádio/Mix detectado!</strong>
          <p>Rádios do YouTube podem ter muitos vídeos. Defina um limite:</p>
        </div>
      </div>
      
      <div class="form-group">
        <label>Limite de itens da rádio</label>
        <div class="number-control">
          <button 
            class="num-btn minus"
            @mousedown="startHold('down')"
            @mouseup="stopHold"
            @mouseleave="stopHold"
            @touchstart.prevent="startHold('down')"
            @touchend="stopHold"
          >−</button>
          <span class="num-value">{{ localRadioLimit }}</span>
          <button 
            class="num-btn plus"
            @mousedown="startHold('up')"
            @mouseup="stopHold"
            @mouseleave="stopHold"
            @touchstart.prevent="startHold('up')"
            @touchend="stopHold"
          >+</button>
        </div>
        <span class="hint">Segure para acelerar • Máximo: 1000</span>
      </div>
    </div>

    <div class="examples">
      <p><strong>Exemplos de URLs suportadas:</strong></p>
      <ul>
        <li>🎬 Vídeo único: <code>youtube.com/watch?v=abc123</code></li>
        <li>📃 Playlist: <code>youtube.com/playlist?list=PLxxx</code></li>
        <li>📻 Rádio/Mix: <code>youtube.com/watch?v=abc&list=RDabc</code></li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.step1 h2 {
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

.input-with-button {
  display: flex;
  gap: 12px;
}

.input-with-button input {
  flex: 1;
}

.btn-paste {
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  cursor: pointer;
  font-size: 18px;
  transition: all 0.3s;
  backdrop-filter: blur(10px);
}

.btn-paste:hover {
  background: rgba(233, 69, 96, 0.2);
  border-color: rgba(233, 69, 96, 0.4);
  transform: translateY(-2px);
}

.radio-options {
  margin-top: 24px;
  padding: 20px;
  background: rgba(243, 156, 18, 0.08);
  border-radius: 16px;
  border: 1px solid rgba(243, 156, 18, 0.2);
}

.info-box {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.info-box .icon {
  font-size: 28px;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(243, 156, 18, 0.15);
  border-radius: 12px;
}

.info-box strong {
  color: #f39c12;
}

.info-box p {
  margin: 6px 0 0 0;
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
}

.hint {
  display: block;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  margin-top: 8px;
}

/* Controle de número estilizado */
.number-control {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  overflow: hidden;
  user-select: none;
}

.num-btn {
  width: 56px;
  height: 50px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-size: 24px;
  font-weight: 300;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.num-btn:hover {
  background: rgba(233, 69, 96, 0.2);
  color: #ff6b6b;
}

.num-btn:active {
  background: rgba(233, 69, 96, 0.4);
}

.num-btn.minus {
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.num-btn.plus {
  border-left: 1px solid rgba(255, 255, 255, 0.08);
}

.num-value {
  min-width: 80px;
  height: 50px;
  line-height: 50px;
  text-align: center;
  font-size: 22px;
  font-weight: 600;
  color: #fff;
  padding: 0 10px;
  background: rgba(0, 0, 0, 0.2);
}

.examples {
  margin-top: 30px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.examples p {
  margin-top: 0;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  margin-bottom: 15px;
}

.examples ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.examples li {
  padding: 10px 0;
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
}

.examples li:last-child {
  border-bottom: none;
}

.examples code {
  background: rgba(233, 69, 96, 0.15);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: #ff8a80;
  font-family: 'SF Mono', 'Consolas', monospace;
}
</style>
