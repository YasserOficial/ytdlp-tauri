<script setup>
import { ref, onMounted, computed } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { open as openUrl } from "@tauri-apps/plugin-shell";
import { open as openDialog } from "@tauri-apps/plugin-dialog";

const props = defineProps({
  url: String,
  needsAuth: Boolean,
  useBrowserCookies: Boolean,
  authVerified: Boolean,
  cookiesPath: String,
});

const emit = defineEmits([
  "update:needsAuth",
  "update:useBrowserCookies",
  "update:authVerified",
  "update:cookiesPath",
  "back",
  "verified",
]);

// Sub-passos do wizard de autenticação
// checking: Verificando disponibilidade (tenta com cookies do Firefox automaticamente)
// needs_auth (1.6): Mostra que precisa de auth com opções
// firefox_tutorial (1.7): Tutorial Firefox com botão de download
// checking_firefox (1.7.1): Verificando instalação do Firefox
// login_firefox (1.8): Botão para abrir Firefox e logar
// verifying_cookies (1.9): Verificando cookies automaticamente
// verifying_file: Conferindo um arquivo de cookies escolhido pela pessoa (opção avançada)
// success: Sucesso!
const subStep = ref("checking");

const statusMessage = ref("Verificando disponibilidade...");
const error = ref("");
const fileError = ref("");

// Opção avançada: usar um cookies.txt exportado (formato Netscape) em vez do Firefox
async function pickCookiesFile() {
  fileError.value = "";
  let selected;
  try {
    selected = await openDialog({
      multiple: false,
      directory: false,
      title: "Escolha o arquivo de cookies",
      filters: [
        { name: "Cookies (Netscape .txt)", extensions: ["txt"] },
        { name: "Todos os arquivos", extensions: ["*"] },
      ],
    });
  } catch (e) {
    fileError.value = "Não foi possível abrir a janela de arquivos: " + e;
    return;
  }
  if (!selected) return;

  subStep.value = "verifying_file";
  try {
    const result = await invoke("verify_cookies", { url: props.url, cookiesPath: selected });
    if (result.valid) {
      emit("update:useBrowserCookies", false);
      emit("update:cookiesPath", selected);
      emit("update:needsAuth", true);
      emit("update:authVerified", true);
      subStep.value = "success";
      emit("verified");
    } else {
      fileError.value =
        "Esse arquivo não liberou o acesso. Exporte os cookies de novo, com a conta logada no YouTube, no formato Netscape.";
      subStep.value = "needs_auth";
    }
  } catch (e) {
    fileError.value = "Erro ao conferir o arquivo: " + e;
    subStep.value = "needs_auth";
  }
}
const firefoxInstalled = ref(false);

// Detectar tipo de URL
const isPlaylistOrRadio = computed(() => {
  return props.url.includes("list=") || 
         props.url.includes("playlist") || 
         props.url.includes("start_radio=1");
});

onMounted(async () => {
  if (props.authVerified) {
    subStep.value = "success";
    return;
  }
  await checkAvailability();
});

// 1.5: Verificar disponibilidade - tenta primeiro com cookies do Firefox automaticamente
async function checkAvailability() {
  subStep.value = "checking";
  statusMessage.value = "Verificando disponibilidade...";
  error.value = "";
  
  try {
    // Primeiro, tenta acessar sem cookies
    const result = await invoke("check_url_protection", { url: props.url });
    
    if (!result.needs_auth) {
      // Conteúdo público - sucesso direto!
      emit("update:needsAuth", false);
      emit("update:authVerified", true);
      subStep.value = "success";
      emit("verified");
      return;
    }
    
    // Precisa de auth - tenta automaticamente com cookies do Firefox
    statusMessage.value = "Tentando autenticar automaticamente...";
    
    const cookiesResult = await invoke("check_firefox_youtube_cookies", { url: props.url });
    
    if (cookiesResult.valid) {
      // Cookies do Firefox funcionaram!
      emit("update:needsAuth", true);
      emit("update:useBrowserCookies", true);
      emit("update:authVerified", true);
      subStep.value = "success";
      emit("verified");
    } else {
      // Não conseguiu - mostra tela de autenticação (1.6)
      emit("update:needsAuth", true);
      error.value = result.error || "Este conteúdo requer autenticação para ser baixado.";
      subStep.value = "needs_auth";
    }
  } catch (e) {
    // Erro - tenta com cookies do Firefox como fallback
    try {
      statusMessage.value = "Tentando autenticar automaticamente...";
      const cookiesResult = await invoke("check_firefox_youtube_cookies", { url: props.url });
      
      if (cookiesResult.valid) {
        emit("update:needsAuth", true);
        emit("update:useBrowserCookies", true);
        emit("update:authVerified", true);
        subStep.value = "success";
        emit("verified");
        return;
      }
    } catch (e2) {
      // Ignora erro do fallback
    }
    
    emit("update:needsAuth", true);
    error.value = "Este conteúdo requer autenticação para ser baixado.";
    subStep.value = "needs_auth";
  }
}

// 1.6: Usuário quer autenticar
function startAuthFlow() {
  error.value = "";
  subStep.value = "firefox_tutorial";
}

// 1.6: Usuário mudou de ideia: volta para o link
function cancelAuth() {
  emit("back");
}

// Dentro do tutorial, "Voltar" volta uma tela do próprio tutorial
function backInAuthFlow() {
  error.value = "";
  subStep.value = subStep.value === "login_firefox" ? "firefox_tutorial" : "needs_auth";
}

// 1.7: Abrir link para download do Firefox
async function downloadFirefox() {
  await openUrl("https://www.mozilla.org/pt-BR/firefox/new/");
}

// 1.7.1: Verificar se Firefox está instalado
async function checkFirefoxInstalled() {
  subStep.value = "checking_firefox";
  error.value = "";
  
  try {
    const result = await invoke("check_firefox_installed");
    firefoxInstalled.value = result.installed;
    
    if (result.installed) {
      subStep.value = "login_firefox";
    } else {
      error.value = "Firefox não encontrado. Por favor, instale e tente novamente.";
      subStep.value = "firefox_tutorial";
    }
  } catch (e) {
    error.value = e.toString();
    subStep.value = "firefox_tutorial";
  }
}

// 1.8: Abrir Firefox no YouTube
async function openFirefoxLogin() {
  error.value = "";
  try {
    await invoke("open_firefox_youtube");
  } catch (e) {
    error.value = "Erro ao abrir Firefox: " + e.toString();
  }
}

// 1.9: Verificar cookies após login
async function verifyCookiesAfterLogin() {
  subStep.value = "verifying_cookies";
  error.value = "";
  
  try {
    const result = await invoke("check_firefox_youtube_cookies", { url: props.url });
    
    if (result.valid) {
      emit("update:useBrowserCookies", true);
      emit("update:authVerified", true);
      subStep.value = "success";
      emit("verified");
    } else {
      // Não conseguiu - volta para 1.8 com mensagem de erro
      error.value = "Não foi possível verificar o login. Certifique-se de estar logado no YouTube pelo Firefox.";
      subStep.value = "login_firefox";
    }
  } catch (e) {
    error.value = "Erro ao verificar: " + e.toString();
    subStep.value = "login_firefox";
  }
}
</script>

<template>
  <div class="step1-5">
    <!-- Verificando disponibilidade (tenta cookies automaticamente) -->
    <template v-if="subStep === 'checking'">
      <h2>🔍 Verificando Acesso</h2>
      <div class="checking">
        <div class="spinner"></div>
        <p>{{ statusMessage }}</p>
        <span class="checking-hint">Isso geralmente leva poucos segundos</span>
      </div>
    </template>

    <!-- Sucesso: Conteúdo acessível -->
    <template v-else-if="subStep === 'success'">
      <h2>✅ Acesso Verificado</h2>
      <div class="success">
        <span class="icon">✅</span>
        <div>
          <strong>Acesso liberado!</strong>
          <p v-if="useBrowserCookies">Autenticado automaticamente via Firefox.</p>
          <p v-else-if="cookiesPath">Autenticado com o arquivo de cookies.</p>
          <p v-else>O conteúdo está disponível publicamente.</p>
          <p v-if="isPlaylistOrRadio" class="playlist-note">
            📋 Playlist/Rádio detectada - a análise completa será feita no próximo passo.
          </p>
        </div>
      </div>
    </template>

    <!-- 1.6: Precisa de autenticação -->
    <template v-else-if="subStep === 'needs_auth'">
      <h2>🔒 Autenticação Necessária</h2>
      
      <div class="auth-card">
        <div class="auth-icon">🔐</div>
        <h3>Este conteúdo precisa de autenticação</h3>
        <p class="auth-reason">{{ error || "O vídeo é privado, com restrição de idade, ou exclusivo para membros." }}</p>
        
        <div class="auth-options">
          <button @click="startAuthFlow" class="btn-primary">
            🦊 Quero Autenticar
          </button>
          <button @click="cancelAuth" class="btn-secondary">
            ← Mudei de Ideia
          </button>
        </div>
        
        <p class="auth-note">
          💡 Usaremos o Firefox para autenticar automaticamente - sem precisar exportar cookies!
        </p>

        <div class="expert-option">
          <span class="expert-label">Avançado</span>
          <button @click="pickCookiesFile" class="btn-link expert-link">
            📄 Usar um arquivo de cookies (cookies.txt)
          </button>
          <p v-if="fileError" class="file-error">⚠️ {{ fileError }}</p>
        </div>
      </div>
    </template>

    <!-- Conferindo o arquivo de cookies -->
    <template v-else-if="subStep === 'verifying_file'">
      <h2>📄 Conferindo o Arquivo de Cookies</h2>
      <div class="checking">
        <div class="spinner"></div>
        <p>Testando se o arquivo libera o acesso...</p>
        <span class="checking-hint">Isso pode levar alguns segundos</span>
      </div>
    </template>

    <!-- 1.7: Tutorial Firefox -->
    <template v-else-if="subStep === 'firefox_tutorial'">
      <h2>🦊 Configurar Firefox</h2>
      
      <div class="tutorial-card">
        <div class="step-indicator">Passo 1 de 3</div>
        
        <h3>Você precisa ter o Firefox instalado</h3>
        <p>Usamos o Firefox porque ele permite acessar os cookies de forma segura e automática.</p>
        
        <div class="firefox-download">
          <div class="firefox-logo">🦊</div>
          <div class="firefox-info">
            <strong>Mozilla Firefox</strong>
            <span>Navegador rápido e privado</span>
          </div>
          <button @click="downloadFirefox" class="btn-download">
            ⬇️ Baixar Firefox
          </button>
        </div>
        
        <div v-if="error" class="error-message">
          ⚠️ {{ error }}
        </div>
        
        <div class="tutorial-actions">
          <button @click="checkFirefoxInstalled" class="btn-primary">
            Já tenho o Firefox instalado →
          </button>
        </div>
        
        <button @click="backInAuthFlow" class="btn-link">
          ← Voltar
        </button>
      </div>
    </template>

    <!-- 1.7.1: Verificando Firefox -->
    <template v-else-if="subStep === 'checking_firefox'">
      <h2>🔍 Verificando Firefox</h2>
      <div class="checking">
        <div class="spinner"></div>
        <p>Procurando instalação do Firefox...</p>
      </div>
    </template>

    <!-- 1.8: Login no Firefox -->
    <template v-else-if="subStep === 'login_firefox'">
      <h2>🔑 Fazer Login no YouTube</h2>
      
      <div class="tutorial-card">
        <div class="step-indicator">Passo 2 de 3</div>
        
        <div class="firefox-found">
          <span class="check">✓</span> Firefox encontrado!
        </div>
        
        <h3>Faça login no YouTube pelo Firefox</h3>
        <p>Clique no botão abaixo para abrir o Firefox. Faça login na sua conta do YouTube e depois volte aqui.</p>
        
        <div class="login-steps">
          <div class="login-step">
            <span class="step-num">1</span>
            <span>Clique em "Abrir Firefox"</span>
          </div>
          <div class="login-step">
            <span class="step-num">2</span>
            <span>Faça login na sua conta do YouTube</span>
          </div>
          <div class="login-step">
            <span class="step-num">3</span>
            <span>Volte aqui e clique em "Verificar Login"</span>
          </div>
        </div>
        
        <div v-if="error" class="error-message">
          ⚠️ {{ error }}
        </div>
        
        <div class="tutorial-actions">
          <button @click="openFirefoxLogin" class="btn-firefox">
            🦊 Abrir Firefox no YouTube
          </button>
          <button @click="verifyCookiesAfterLogin" class="btn-primary">
            ✓ Verificar Login
          </button>
        </div>
        
        <button @click="backInAuthFlow" class="btn-link">
          ← Voltar
        </button>
      </div>
    </template>

    <!-- 1.9: Verificando cookies -->
    <template v-else-if="subStep === 'verifying_cookies'">
      <h2>🍪 Verificando Autenticação</h2>
      
      <div class="tutorial-card">
        <div class="step-indicator">Passo 3 de 3</div>
        
        <div class="checking">
          <div class="spinner"></div>
          <p>Verificando se você está logado...</p>
          <span class="checking-hint">Isso pode levar alguns segundos</span>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.step1-5 h2 {
  margin-top: 0;
  color: #e94560;
  text-align: center;
}

.checking {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;
}

.checking-hint {
  color: #666;
  font-size: 13px;
  margin-top: 10px;
}

.spinner {
  width: 50px;
  height: 50px;
  border: 4px solid rgba(255, 255, 255, 0.1);
  border-top-color: #e94560;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.success {
  display: flex;
  gap: 15px;
  padding: 25px;
  background: linear-gradient(135deg, #1a4a3a 0%, #0f3a2a 100%);
  border-radius: 12px;
  border-left: 4px solid #00b894;
}

.success .icon {
  font-size: 35px;
}

.success p {
  margin: 5px 0 0 0;
  color: #aaa;
}

.playlist-note {
  margin-top: 10px !important;
  color: #00b894 !important;
}

/* Card de autenticação */
.auth-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 40px;
  text-align: center;
}

.auth-icon {
  font-size: 60px;
  margin-bottom: 20px;
}

.auth-card h3 {
  color: #fff;
  margin: 0 0 10px 0;
  font-size: 22px;
}

.auth-reason {
  color: #888;
  margin-bottom: 30px;
  font-size: 14px;
}

.auth-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 300px;
  margin: 0 auto 25px auto;
}

.auth-note {
  color: rgba(255, 255, 255, 0.45);
  font-size: 13px;
  margin: 0;
}

/* Opção para quem já tem um cookies.txt exportado */
.expert-option {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.expert-label {
  display: block;
  color: rgba(255, 255, 255, 0.35);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.expert-link {
  margin-top: 4px;
}

.file-error {
  color: #ff8a80;
  font-size: 13px;
  margin: 8px 0 0;
}

/* Tutorial Card */
.tutorial-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 35px;
}

.step-indicator {
  background: rgba(233, 69, 96, 0.15);
  border: 1px solid rgba(233, 69, 96, 0.3);
  color: #ff8a80;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  display: inline-block;
  vertical-align: middle;
  margin-bottom: 20px;
}

.tutorial-card h3 {
  color: #fff;
  margin: 0 0 10px 0;
}

.tutorial-card > p {
  color: #888;
  margin-bottom: 25px;
}

/* Firefox download section */
.firefox-download {
  display: flex;
  align-items: center;
  gap: 15px;
  background: #0f0f1a;
  padding: 20px;
  border-radius: 12px;
  margin-bottom: 25px;
}

.firefox-logo {
  font-size: 45px;
}

.firefox-info {
  flex: 1;
  text-align: left;
}

.firefox-info strong {
  display: block;
  color: #fff;
  margin-bottom: 3px;
}

.firefox-info span {
  color: #666;
  font-size: 13px;
}

.btn-download {
  background: #ff6b35;
  color: white;
  border: none;
  padding: 12px 20px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s;
}

.btn-download:hover {
  background: #ff8b55;
  transform: translateY(-2px);
}

/* Firefox found */
.firefox-found {
  background: #1a4a3a;
  color: #00b894;
  padding: 6px 14px;
  margin-left: 8px;
  font-size: 12px;
  vertical-align: middle;
  border: 1px solid rgba(0, 184, 148, 0.3);
  border-radius: 20px;
  margin-bottom: 20px;
  display: inline-block;
}

.firefox-found .check {
  margin-right: 8px;
}

/* Login steps */
.login-steps {
  background: #0f0f1a;
  padding: 20px;
  border-radius: 12px;
  margin-bottom: 25px;
}

.login-step {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 12px 0;
  border-bottom: 1px solid #1a1a2e;
}

.login-step:last-child {
  border-bottom: none;
}

.step-num {
  width: 30px;
  height: 30px;
  background: #e94560;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
}

.login-step span:last-child {
  color: #ccc;
}

/* Botões */
.btn-primary {
  background: linear-gradient(135deg, #e94560 0%, #c73e54 100%);
  color: white;
  border: none;
  padding: 14px 28px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  transition: all 0.3s;
  width: 100%;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 20px rgba(233, 69, 96, 0.4);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: rgba(255, 255, 255, 0.8);
  border: none;
  padding: 14px 28px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 15px;
  transition: all 0.3s;
  width: 100%;
}

.btn-secondary:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.btn-firefox {
  background: linear-gradient(135deg, #ff6b35 0%, #e55a25 100%);
  color: white;
  border: none;
  padding: 14px 28px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  transition: all 0.3s;
  flex: 1;
}

.btn-firefox:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 20px rgba(255, 107, 53, 0.4);
}

.btn-link {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  font-size: 14px;
  padding: 10px;
  margin-top: 15px;
}

.btn-link:hover {
  color: #e94560;
}

.tutorial-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.tutorial-actions .btn-primary {
  flex: 1;
  min-width: 200px;
}

/* Error message */
.error-message {
  background: rgba(231, 76, 60, 0.1);
  border: 1px solid rgba(231, 76, 60, 0.3);
  color: #e74c3c;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 20px;
  font-size: 14px;
}
</style>
