<p align="center">
  <img src="src-tauri/icons/128x128.png" width="96" alt="Ícone do YT-DLP GUI">
</p>

<h1 align="center">YT-DLP GUI</h1>

<p align="center">
  Interface gráfica para Windows que baixa vídeo e áudio com
  <a href="https://github.com/yt-dlp/yt-dlp">yt-dlp</a> e FFmpeg.
</p>

<p align="center">
  <img alt="Tauri 2" src="https://img.shields.io/badge/Tauri-2-24C8DB?logo=tauri&logoColor=white">
  <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white">
  <img alt="Windows" src="https://img.shields.io/badge/Windows-10%2F11-0078D6?logo=windows&logoColor=white">
</p>

Um assistente em passos: cole o link, escolha o que baixar, o formato e a pasta. Na primeira abertura o aplicativo instala o yt-dlp e o FFmpeg sozinho e avisa quando há atualização do yt-dlp.

## O que dá para fazer

- Baixar um vídeo, uma playlist ou uma rádio
- Escolher itens de uma lista antes de começar
- Salvar em vídeo (melhor, 1080p, 720p, 480p, 360p) ou em áudio (melhor, 320, 256, 192, 128 kbps)
- Usar cookies do Firefox quando o conteúdo pede login
- Acompanhar progresso, velocidade e tempo restante
- Abrir a pasta quando o download termina

Sites reconhecidos de forma específica: YouTube, Instagram, X (Twitter) e Vimeo. Os demais seguem o que o yt-dlp já suporta.

## Como usar

1. Abra o aplicativo.
2. Espere a checagem do yt-dlp e do FFmpeg. Se faltarem, o app baixa os dois.
3. Cole a URL.
4. Se o link estiver protegido, entre com o Firefox e confirme os cookies.
5. Escolha o tipo de conteúdo e, se for uma lista, marque os itens.
6. Escolha vídeo ou áudio e a qualidade.
7. Escolha a pasta e comece o download.

As ferramentas ficam em `%LOCALAPPDATA%\ytdlp-tauri\tools`.

## Desenvolvimento

Requisitos: [Node.js](https://nodejs.org/) 18+, [Rust](https://www.rust-lang.org/tools/install) e o [WebView2](https://developer.microsoft.com/microsoft-edge/webview2/) (já vem no Windows 11).

```bash
npm install
npm run tauri dev
```

Para gerar o instalador (NSIS) e o MSI:

```bash
npm run tauri build
```

Os pacotes saem em `src-tauri/target/release/bundle/`.

Se o Rust estiver no toolchain GNU (`x86_64-pc-windows-gnu`), crie `src-tauri/.cargo/config.toml` apontando `linker` e `ar` para o `gcc.exe` e o `ar.exe` do MinGW. Esse arquivo é local e fica fora do Git.

Para regenerar os ícones a partir de `src-tauri/icons/icon.png`:

```powershell
powershell -ExecutionPolicy Bypass -File .\create-ico.ps1
```

## Estrutura

```
src/                 interface Vue (passos do assistente)
src-tauri/src/       comandos Rust: ferramentas, análise e download
src-tauri/icons/     ícones do aplicativo
```

## Aviso

O aplicativo só organiza o uso do yt-dlp. Respeite os termos de cada site e os direitos de quem publicou o conteúdo. Baixe o que você tem permissão para baixar.

yt-dlp e FFmpeg são projetos de terceiros e não fazem parte deste repositório.
