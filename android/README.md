# YT-DLP GUI Android

Uma interface gráfica moderna para yt-dlp no Android, com design glassmorphism e experiência wizard.

## 📱 Features

- ✅ Interface wizard intuitiva (passo a passo)
- ✅ Design glassmorphism moderno
- ✅ Download de vídeos e áudios
- ✅ Suporte a playlists e rádios do YouTube
- ✅ Seleção de qualidade (1080p, 720p, 480p, etc.)
- ✅ Login via WebView (captura cookies automaticamente)
- ✅ Suporte a conteúdo privado/restrito por idade
- ✅ Progresso de download em tempo real

## 🛠️ Tecnologias

- **Kotlin** - Linguagem principal
- **Jetpack Compose** - UI moderna declarativa
- **youtubedl-android** - yt-dlp + FFmpeg embarcados
- **Material 3** - Design system
- **Coroutines** - Operações assíncronas

## 📦 Dependências Principais

```kotlin
// youtubedl-android (yt-dlp + ffmpeg)
implementation("com.github.yausername.youtubedl-android:library:0.15.0")
implementation("com.github.yausername.youtubedl-android:ffmpeg:0.15.0")

// Jetpack Compose
implementation("androidx.compose.ui:ui:1.5.0")
implementation("androidx.compose.material3:material3:1.1.0")
```

## 🏗️ Estrutura do Projeto

```
app/
├── src/main/
│   ├── java/com/ytdlp/gui/
│   │   ├── MainActivity.kt
│   │   ├── YtDlpApplication.kt
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   │   └── Theme.kt
│   │   │   ├── screens/
│   │   │   │   ├── SetupScreen.kt
│   │   │   │   ├── UrlScreen.kt
│   │   │   │   ├── AuthScreen.kt
│   │   │   │   ├── ContentTypeScreen.kt
│   │   │   │   ├── SelectionScreen.kt
│   │   │   │   ├── FormatScreen.kt
│   │   │   │   ├── DownloadScreen.kt
│   │   │   │   └── ProgressScreen.kt
│   │   │   └── components/
│   │   │       ├── GlassCard.kt
│   │   │       ├── StepIndicator.kt
│   │   │       └── VideoItem.kt
│   │   ├── viewmodel/
│   │   │   └── MainViewModel.kt
│   │   └── util/
│   │       ├── YtDlpHelper.kt
│   │       └── CookieManager.kt
│   └── res/
│       └── ...
└── build.gradle.kts
```

## 🚀 Como Compilar

1. Abra o projeto no Android Studio
2. Sync Gradle
3. Run no dispositivo/emulador

## 📝 Notas

- Este app NÃO será publicado na Play Store (violação de políticas)
- Distribua via GitHub Releases ou F-Droid
- Requer Android 7.0+ (API 24)
