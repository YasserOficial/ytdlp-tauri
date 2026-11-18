<script setup>
import { ref, computed } from "vue";

const props = defineProps({
  items: Array,
  selectedItems: Array,
  type: String,
});

const emit = defineEmits(["update:selectedItems"]);

const searchQuery = ref("");
const selectAll = ref(false);

const filteredItems = computed(() => {
  if (!searchQuery.value) return props.items;
  const query = searchQuery.value.toLowerCase();
  return props.items.filter(item => 
    item.title.toLowerCase().includes(query)
  );
});

function isSelected(item) {
  return props.selectedItems.some(s => s.id === item.id);
}

function toggleItem(item) {
  if (isSelected(item)) {
    emit("update:selectedItems", props.selectedItems.filter(s => s.id !== item.id));
  } else {
    emit("update:selectedItems", [...props.selectedItems, item]);
  }
}

function toggleAll() {
  if (props.selectedItems.length === props.items.length) {
    emit("update:selectedItems", []);
  } else {
    emit("update:selectedItems", [...props.items]);
  }
}

function selectFiltered() {
  const newSelection = [...props.selectedItems];
  filteredItems.value.forEach(item => {
    if (!isSelected(item)) {
      newSelection.push(item);
    }
  });
  emit("update:selectedItems", newSelection);
}

function deselectFiltered() {
  const filteredIds = filteredItems.value.map(i => i.id);
  emit("update:selectedItems", props.selectedItems.filter(s => !filteredIds.includes(s.id)));
}

function formatDuration(seconds) {
  if (!seconds) return "";
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}
</script>

<template>
  <div class="step2-5">
    <h2>✅ Passo 2.5: Selecionar Itens</h2>
    <p class="description">
      Escolha quais {{ type === 'radio' ? 'músicas' : 'vídeos' }} você deseja baixar
    </p>

    <div class="controls">
      <div class="search-box">
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="🔍 Buscar por título..."
        />
      </div>

      <div class="bulk-actions">
        <button @click="toggleAll" class="btn-action">
          {{ selectedItems.length === items.length ? '☐ Desmarcar todos' : '☑ Selecionar todos' }}
        </button>
        <button v-if="searchQuery" @click="selectFiltered" class="btn-action">
          ☑ Selecionar filtrados
        </button>
        <button v-if="searchQuery" @click="deselectFiltered" class="btn-action">
          ☐ Desmarcar filtrados
        </button>
      </div>
    </div>

    <div class="selection-info">
      <span class="count">
        <strong>{{ selectedItems.length }}</strong> de <strong>{{ items.length }}</strong> selecionados
      </span>
      <span v-if="searchQuery" class="filtered">
        (mostrando {{ filteredItems.length }} resultados)
      </span>
    </div>

    <div class="items-list">
      <div 
        v-for="(item, index) in filteredItems" 
        :key="item.id"
        :class="['item-card', { selected: isSelected(item) }]"
        @click="toggleItem(item)"
      >
        <div class="item-checkbox">
          <span v-if="isSelected(item)">☑</span>
          <span v-else>☐</span>
        </div>
        
        <div class="item-index">{{ index + 1 }}</div>
        
        <div class="item-thumbnail" v-if="item.thumbnail">
          <img :src="item.thumbnail" :alt="item.title" />
        </div>
        
        <div class="item-info">
          <strong class="item-title">{{ item.title }}</strong>
          <div class="item-meta">
            <span v-if="item.duration" class="duration">
              ⏱ {{ formatDuration(item.duration) }}
            </span>
            <span v-if="item.channel" class="channel">
              👤 {{ item.channel }}
            </span>
          </div>
        </div>
      </div>

      <div v-if="filteredItems.length === 0" class="no-results">
        <span class="icon">🔍</span>
        <p>Nenhum item encontrado para "{{ searchQuery }}"</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.step2-5 h2 {
  margin-top: 0;
  color: #e94560;
}

.description {
  color: #888;
  margin-bottom: 20px;
}

.controls {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 15px;
}

.search-box input {
  width: 100%;
}

.bulk-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-action {
  background: #0f3460;
  color: #aaa;
  border: none;
  padding: 8px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.3s;
}

.btn-action:hover {
  background: #1a4a7a;
  color: white;
}

.selection-info {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
  font-size: 14px;
}

.count {
  color: #e94560;
}

.filtered {
  color: #888;
}

.items-list {
  max-height: 350px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.item-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #1a1a2e;
  border-radius: 8px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.item-card:hover {
  background: #1a2a3e;
  border-color: #0f3460;
}

.item-card.selected {
  border-color: #e94560;
  background: #2a1a2e;
}

.item-checkbox {
  font-size: 20px;
  width: 24px;
  color: #e94560;
}

.item-index {
  color: #666;
  font-size: 12px;
  width: 25px;
  text-align: center;
}

.item-thumbnail {
  width: 80px;
  height: 45px;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
}

.item-thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-title {
  display: block;
  color: #ddd;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  display: flex;
  gap: 15px;
  margin-top: 4px;
}

.duration, .channel {
  color: #666;
  font-size: 12px;
}

.no-results {
  text-align: center;
  padding: 40px;
  color: #666;
}

.no-results .icon {
  font-size: 40px;
  display: block;
  margin-bottom: 10px;
}

/* Scrollbar personalizada */
.items-list::-webkit-scrollbar {
  width: 8px;
}

.items-list::-webkit-scrollbar-track {
  background: #1a1a2e;
  border-radius: 4px;
}

.items-list::-webkit-scrollbar-thumb {
  background: #0f3460;
  border-radius: 4px;
}

.items-list::-webkit-scrollbar-thumb:hover {
  background: #1a4a7a;
}
</style>
