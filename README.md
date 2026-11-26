<p align="center">
  <img src="src-tauri/icons/128x128.png" width="96" alt="Ícone do YT-DLP GUI">
</p>

<h1 align="center">YT-DLP GUI</h1>

<p align="center">
  Interface gráfica para Windows, Linux e Android que baixa vídeo e áudio com
  <a href="https://github.com/yt-dlp/yt-dlp">yt-dlp</a> e FFmpeg.
  No computador, o mesmo fluxo existe sem janela, para script e agente.
</p>

<p align="center">
  <img alt="Tauri 2" src="https://img.shields.io/badge/Tauri-2-24C8DB?logo=tauri&logoColor=white">
  <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white">
  <img alt="Windows" src="https://img.shields.io/badge/Windows-10%2F11-0078D6?logo=windows&logoColor=white">
  <img alt="Linux" src="https://img.shields.io/badge/Linux-Ubuntu%2024.04-E95420?logo=linux&logoColor=white">
  <img alt="Android" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white">
</p>

<p align="center">
  <a href="https://github.com/YasserOficial/ytdlp-tauri/releases/latest">Baixar a versão mais recente</a>
</p>

Um assistente em passos: cole o link, escolha o que baixar, o formato e a pasta. Na primeira abertura o aplicativo instala o yt-dlp sozinho. No Windows ele também instala o FFmpeg; no Linux usa o `ffmpeg` do sistema quando ele já está no PATH e, se não estiver, baixa um.

## O que dá para fazer

- Baixar um vídeo, uma playlist ou uma rádio
- Escolher itens de uma lista antes de começar
- Salvar em vídeo (melhor, 1080p, 720p, 480p, 360p) ou em áudio (melhor, 320, 256, 192, 128 kbps)
- Ver duração, estimativa de tamanho e o resumo da qualidade escolhida
- Usar os cookies do Firefox quando o conteúdo pede login, sem exportar arquivo, ou um `cookies.txt` (opção avançada)
- Esperar um minuto depois de um bloqueio do YouTube antes de consultar de novo
- Voltar nos passos sem repetir a consulta ao site
- Acompanhar progresso, velocidade e tempo restante, cancelar a qualquer momento e ver o erro real se o download falhar
- Abrir a pasta quando o download termina

## Como baixar

A versão atual é a **0.1.9**, em [Releases](https://github.com/YasserOficial/ytdlp-tauri/releases/tag/v0.1.9).

- **Windows:** `YT-DLP GUI_0.1.9_x64-setup.exe` (instalador NSIS). O `.msi` é a alternativa. O instalador pede permissão de administrador.
- **Linux (Ubuntu 24.04 e derivados):** `YT-DLP GUI_0.1.9_amd64.deb`. Instale com `sudo apt install ./YT-DLP\ GUI_0.1.9_amd64.deb` para o apt trazer o WebKitGTK. O pacote depende de `libwebkit2gtk-4.1-0` e `libgtk-3-0`. No Ubuntu 22.04 esses nomes não existem, então este `.deb` não serve lá.
- **Android 7.0 ou mais novo:** `YT-DLP-GUI_0.1.9_android-universal.apk` funciona em qualquer aparelho; `YT-DLP-GUI_0.1.9_android-arm64.apk` é menor e serve para quase todos os celulares atuais. Veja [No Android](#no-android).
- **Agente:** `ytdlp-tauri-agent-windows-x64.zip` ou `ytdlp-tauri-agent-linux-x64.tar.gz`. É o mesmo programa sem a janela: comando manual ou servidor MCP.

## Como usar

1. Abra o aplicativo.
2. Espere a checagem do yt-dlp e do FFmpeg. Se faltarem, o app baixa o que precisa.
3. Cole a URL.
4. Se o link estiver protegido, entre com o Firefox e confirme os cookies.
5. Escolha o tipo de conteúdo e, se for uma lista, marque os itens.
6. Escolha vídeo ou áudio e a qualidade.
7. Escolha a pasta e comece o download.

As ferramentas ficam em `%LOCALAPPDATA%\ytdlp-tauri\tools` no Windows e em `~/.local/share/ytdlp-tauri/tools` no Linux (`$XDG_DATA_HOME`, se estiver definido).

## No Android

O app Android tem os mesmos passos, com o yt-dlp e o FFmpeg já dentro do APK. Ele não está na Play Store, que não aceita apps desse tipo; a instalação é pelo APK da release.

1. Baixe o APK no celular. O **universal** funciona em qualquer aparelho; o **arm64** é menor e serve para quase todos os celulares atuais.
2. Abra o arquivo. Na primeira vez, o Android pede para permitir a instalação a partir do navegador ou do gerenciador de arquivos.
3. Se já tiver uma versão antiga instalada e o Android disser que o app não é compatível, desinstale a antiga e instale de novo. O app não guarda nada importante.
4. Na primeira abertura, o app prepara o yt-dlp e oferece atualizá-lo.

Para conteúdo que pede login no YouTube, no Instagram ou no X, o app abre o site num navegador interno e guarda os cookies depois que você entra. Os arquivos podem ir para a pasta de Downloads, Músicas ou Vídeos do celular, ou para uma pasta só do app.

## Uso por agentes

O `ytdlp-tauri-agent` é o mesmo programa sem janela. Ele não precisa da interface aberta: o cliente MCP ou o script o abre quando precisa. A pasta das ferramentas é a mesma da interface, então o yt-dlp e o FFmpeg instalados por um servem para o outro.

Quem instalou o app já tem o agente:

- **Windows:** `C:\Program Files\YT-DLP GUI\ytdlp-tauri-agent.exe`, ao lado do app. Ele não entra no PATH, então use o caminho completo.
- **Linux (`.deb`):** `/usr/bin/ytdlp-tauri-agent`, já no PATH.

Sem instalar a interface, baixe o `ytdlp-tauri-agent-windows-x64.zip` ou o `ytdlp-tauri-agent-linux-x64.tar.gz` da release. Os dois usos, manual e MCP, cabem nesse mesmo arquivo.

Uso manual: um comando, resposta em JSON. A última linha tem `"event":"result"`.

```bash
ytdlp-tauri-agent tools
ytdlp-tauri-agent analyze "https://www.youtube.com/playlist?list=..."
ytdlp-tauri-agent formats "https://www.youtube.com/watch?v=..."
ytdlp-tauri-agent download "https://www.youtube.com/watch?v=..." --mode video --quality 720p --out "~/Videos"
```

Se parte de uma lista falhar, o resultado traz os arquivos salvos e o campo `warning`, e o comando sai com código 2.

`--firefox` usa os cookies do Firefox da máquina onde o comando roda. `--cookies` aponta um arquivo Netscape. `--item` pode repetir para baixar só alguns itens da lista. `--quiet` omite o progresso. `--radio-limit` corta a rádio. O padrão é 50. `--out` e `--cookies` aceitam `~` para a pasta pessoal.

`--quality` depende do modo: em vídeo, `best` ou uma altura (`1080p`, `720p`...); em áudio, `best` ou um bitrate em kbps (`320`, `256`, `192`, `128`). Combinação inválida é recusada antes de começar.

Se o yt-dlp ou o FFmpeg não estiverem instalados, o comando baixa os dois antes de continuar. O yt-dlp do app também se atualiza sozinho: a cada 6 horas o agente pergunta ao GitHub se há versão nova e, se houver, atualiza antes de seguir. Quando isso acontece, o resultado traz `ytdlp_updated` com as duas versões. `tools` faz essa checagem na hora.

Servidor MCP: `ytdlp-tauri-agent mcp`. Sem argumentos, se a entrada já vier por pipe, ele também sobe como MCP. As ferramentas são `check_tools`, `analyze_url`, `list_formats`, `download` e `cancel_download`.

`download` faz um download por vez. Se a chamada trouxer `progressToken`, o servidor manda `notifications/progress` de 0 a 100 enquanto baixa. Dá para interromper com `cancel_download` ou com `notifications/cancelled`; os arquivos que já terminaram ficam na pasta. Enquanto um download corre, o servidor continua respondendo às outras chamadas.

```json
{
  "mcpServers": {
    "ytdlp": {
      "command": "C:\\caminho\\ytdlp-tauri-agent.exe",
      "args": ["mcp"]
    }
  }
}
```

Com o app instalado no Windows, `command` é `"C:\\Program Files\\YT-DLP GUI\\ytdlp-tauri-agent.exe"` (no JSON a barra vai dobrada). No Linux, `ytdlp-tauri-agent` (instalado pelo `.deb`) ou o caminho do binário extraído do `.tar.gz`.

No Claude Code, um comando só registra o servidor:

```powershell
claude mcp add ytdlp -- "C:\Program Files\YT-DLP GUI\ytdlp-tauri-agent.exe" mcp
```

```bash
claude mcp add ytdlp -- ytdlp-tauri-agent mcp
```

## Desenvolvimento

Requisitos comuns: [Node.js](https://nodejs.org/) 18+ e [Rust](https://www.rust-lang.org/tools/install).

No Windows também é preciso o [WebView2](https://developer.microsoft.com/microsoft-edge/webview2/) (já vem no Windows 11). No Ubuntu 24.04:

```bash
sudo apt install build-essential curl wget xz-utils pkg-config libssl-dev \
  libwebkit2gtk-4.1-dev libgtk-3-dev libayatana-appindicator3-dev librsvg2-dev patchelf
```

```bash
npm install
npm run tauri dev
```

Para gerar o instalador NSIS e o MSI no Windows:

```bash
npm run tauri build
```

Para gerar só o `.deb` no Linux:

```bash
npm run tauri build -- --bundles deb
```

Os pacotes saem em `src-tauri/target/release/bundle/`.

Se o Rust no Windows estiver no toolchain GNU (`x86_64-pc-windows-gnu`), crie `src-tauri/.cargo/config.toml` apontando `linker` e `ar` para o `gcc.exe` e o `ar.exe` do MinGW. Esse arquivo é local e fica fora do Git.

Para gerar o APK do Android, com [Java](https://adoptium.net/) 17+ e o SDK do Android (o do Android Studio serve), indicado em `android/local.properties` como `sdk.dir=C:/Users/.../Android/Sdk`:

```powershell
cd android
.\gradlew.bat assembleRelease
```

No Linux, `./gradlew assembleRelease`. Os APKs saem em `android/app/build/outputs/apk/release/`: um universal e um por arquitetura. A versão vem do `package.json` da raiz. O APK é assinado com a chave de teste do computador (`~/.android/debug.keystore`); para usar outra, defina `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`.

Para regenerar os ícones a partir de `src-tauri/icons/icon.png`:

```powershell
powershell -ExecutionPolicy Bypass -File .\create-ico.ps1
```

## Estrutura

```
src/                          interface Vue (passos do assistente)
src-tauri/src/                comandos Rust: ferramentas, análise e download
src-tauri/src/bin/            ytdlp-tauri-agent, uso manual e MCP
src-tauri/icons/              ícones do aplicativo
android/                      app Android (Kotlin e Jetpack Compose)
docs/                         decisões e roadmap
```

## Aviso

O aplicativo só organiza o uso do yt-dlp. Respeite os termos de cada site e os direitos de quem publicou o conteúdo. Baixe o que você tem permissão para baixar.

yt-dlp e FFmpeg são projetos de terceiros e não fazem parte deste repositório.
