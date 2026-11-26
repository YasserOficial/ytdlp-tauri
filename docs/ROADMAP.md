# Roadmap

Os planos maiores do YT-DLP GUI. Cada item tem o porquê, como fica e as fases. A unificação está em andamento; o resto ainda não começou. O motivo das escolhas já feitas está em [DECISOES.md](DECISOES.md).

| Item | Situação | Detalhes |
|---|---|---|
| Unificação dos projetos no GitHub (desktop + Android) | Em andamento (fases 1 e 2 prontas) | nesta página |
| Atualização automática do app pelo GitHub | Planejado | nesta página |
| Suporte a vários idiomas (português e inglês primeiro) | Planejado | [ROADMAP-idiomas.md](ROADMAP-idiomas.md) |

**Ordem sugerida:** primeiro a unificação, porque ela traz os builds automáticos no GitHub, e a atualização automática depende deles. Depois a atualização automática. Os idiomas por último, já cobrindo as três plataformas de uma vez.

---

## Unificação dos projetos no GitHub

**Hoje:** o app Android é um projeto separado (Kotlin, Jetpack Compose, youtubedl-android) que existe só no computador, em `OneDrive\Documentos\ytdlp-android`, sem Git e sem GitHub. Os APKs (156 MB universal, 54 MB só arm64) também estão só lá.

**Objetivo:** um repositório e uma página de download para as três plataformas. O código de cada uma continua como está.

### Como fica

```
ytdlp-tauri/
  src/  src-tauri/  ...   ← desktop, onde já está (nada se move)
  android/                ← o projeto Kotlin inteiro
  docs/
  README.md               ← uma página para Windows, Linux e Android
```

- **Releases:** cada versão traz os instaladores de Windows e Linux, os agentes e o APK do Android.
- **Versão:** o Android hoje está em `1.0.0`, e o desktop em `0.1.x`. O Android passa a seguir o número do desktop, para uma versão valer para todas as plataformas. O `versionCode` interno do Android continua só crescendo, então atualizar por cima não quebra.
- **O desktop não vai para `desktop/`:** só ficaria mais simétrico, e quebraria os caminhos de build, os scripts e o README sem ganho.

### Atenção antes de começar

1. **Assinatura do Android.** Decidido: sem chave própria. O APK é assinado com a chave de teste do computador (`~/.android/debug.keystore`). O app não guarda nada importante, então, se a chave mudar (computador formatado, build em outra máquina), basta desinstalar e instalar de novo. Uma chave própria continua possível pelas variáveis `ANDROID_KEYSTORE_*`.
2. **O que não entra no Git:** builds, `.gradle`, `local.properties`, os APKs, o `ytdlp.zip` e a pasta `.github/java-upgrade` (registros de uma ferramenta, não automação). O `.gitignore` do projeto já cobre quase tudo; o código tem só 463 KB.

### Fases

1. **Assinatura.** ✅ O release assina com a chave de teste do computador, ou com uma chave indicada por variáveis de ambiente.
2. **Entrada no repositório.** ✅ Projeto em `android/`, limpo, num commit próprio.
3. **Versão alinhada.** O Android adota o número do desktop na próxima release.
4. **README único.** Seção Android (requisitos, instalar APK fora da loja, login pelo navegador interno) e downloads das três plataformas.
5. **Releases com APK.** Universal e só arm64.
6. **Builds automáticos (GitHub Actions).** O APK é o mais simples. Cada máquina do GitHub tem uma chave de teste diferente, então para o APK não mudar de assinatura a cada versão vale guardar a chave de teste deste computador nos segredos do GitHub. Depois o `.deb` e o Windows. Isso também prepara a atualização automática, que precisa de arquivos assinados em toda release.

**Fica fora:** reescrever o Android com Tauri para ter código único. O yt-dlp no Android precisa de um Python embutido, que é o que a youtubedl-android resolve; seria praticamente refazer o app.

---

## Atualização automática do app pelo GitHub

**Hoje:** o app só atualiza o yt-dlp. O próprio app não avisa quando sai versão nova; a pessoa precisa voltar ao GitHub, baixar e instalar na mão. O agente instalado junto com o app fica na versão do app. O agente avulso (`.zip`/`.tar.gz`) também não avisa.

**Objetivo:** ao abrir, o app descobre se há versão nova no GitHub, mostra o que mudou e atualiza com um clique, com a garantia de que o arquivo veio mesmo da release oficial.

### Como fica, por plataforma

- **Windows (instalador `.exe` e `.msi`):** plugin oficial de atualização do Tauri (`tauri-plugin-updater`). A cada release vai junto um `latest.json` com a versão, as notas e o link de cada arquivo, e cada instalador ganha uma assinatura (`.sig`). O app baixa, confere a assinatura com a chave pública embutida, instala e reinicia.
- **Linux (`.deb`):** o plugin do Tauri não atualiza `.deb`. Opções, a decidir:
  - o app só avisa e abre a página da release (mais simples);
  - publicar também um **AppImage**, que o plugin atualiza sozinho.
- **Android:** o app consulta a última release pela API do GitHub e oferece baixar o APK novo. A instalação passa pela tela do próprio Android (precisa da permissão de instalar apps de outras fontes). Se a assinatura mudar entre versões, o Android recusa instalar por cima e o app precisa orientar a desinstalar antes.
- **Agente:** o instalado é atualizado junto com o app. O avulso ganha um aviso no `tools` quando há versão nova e, talvez, um comando `self-update`.

### Experiência na interface

- Checagem ao abrir, sem atrasar a tela inicial, e no máximo uma vez por dia.
- Aviso com a versão nova e as notas da release, e os botões **Atualizar agora** e **Agora não**.
- "Agora não" não pergunta de novo pela mesma versão; uma versão mais nova volta a avisar.
- Opção para desligar a checagem.
- Sem internet ou com o GitHub fora do ar, nada aparece e o app segue normal.

### Atenção antes de começar

1. **Chave de assinatura das atualizações (desktop).** O Tauri usa um par de chaves próprio: a pública fica no app, a privada assina cada release. A privada nunca vai para o repositório. **Perdida a chave privada, os apps instalados não aceitam mais nenhuma atualização** e todo mundo precisa reinstalar na mão.
2. **Instalação por máquina pede administrador.** Hoje o instalador NSIS é `perMachine`, então cada atualização vai abrir a confirmação de administrador do Windows. Passar para instalação por usuário (`currentUser`) evita isso, porque as ferramentas já ficam na pasta do usuário. Mudar o modo exige cuidado com quem já tem a versão por máquina instalada.
3. **Toda release precisa sair assinada e com o `latest.json`.** À mão é fácil esquecer; com os builds automáticos da unificação isso sai sozinho.

### Fases

1. **Chaves e configuração.** Gerar o par de chaves do updater, guardar a privada fora do repositório, configurar o plugin e o endereço do `latest.json` na última release do GitHub.
2. **Release assinada.** Ajustar o build para gerar os `.sig` e o `latest.json`, e publicar uma versão que já contém o updater. A partir dela, as seguintes chegam sozinhas.
3. **Aviso na interface.** Tela de "versão nova disponível" com notas, atualizar agora, agora não e a opção de desligar.
4. **Linux.** Decidir entre só avisar e publicar também o AppImage.
5. **Android.** Checagem pela API do GitHub e instalação do APK novo.
6. **Agente avulso.** Aviso de versão nova e, se fizer sentido, `self-update`.

**Pronto quando** uma instalação da versão com o updater receber a versão seguinte sozinha, conferida pela assinatura, no Windows e no Android, e o Linux pelo menos avisar.
