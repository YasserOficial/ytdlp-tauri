# Decisões

O porquê das escolhas do YT-DLP GUI, para quem for mexer no projeto. O que mudou em cada versão está nos commits e nas [releases](https://github.com/YasserOficial/ytdlp-tauri/releases); aqui fica o motivo. Planos futuros estão no [roadmap](ROADMAP.md).

## Princípios

- **O app é para quem não é técnico.** Passos grandes e destacados, uma decisão por tela, poucos elementos, o importante bem grande. Densidade e aparência "nativa" ficam em segundo plano.
- **O visual é identidade.** Fundo escuro com os ícones caindo, vidro fosco, vermelho, emoji nos títulos. O fundo animado é parte da arte e fica atrás do conteúdo. Mexer no visual é corrigir defeito dentro desse estilo, não redesenhar.
- **A interface e o agente usam o mesmo código.** O download, a limpeza e as correções valem para os dois. O agente é o mesmo programa sem janela.

## 0.1.6 — downloads que não perdem arquivo

- **Falha parcial não apaga o que terminou.** Com `--ignore-errors`, o yt-dlp sai com código 1 se um item da lista falhar, mesmo baixando os outros. Antes, isso apagava todos os arquivos. Agora só as sobras saem: `.part`, `.ytdl` e os pedaços `titulo.fNNN.ext` que não chegaram a ser juntados. No modo áudio, só conta como pronto o `.mp3` final.
- **"Só este vídeo" usa uma URL sem `list=`.** Com `list=` na URL, o yt-dlp baixa a lista inteira.
- **Download cancelável**, por um botão e pelo processo: no Windows com `taskkill /T`, que também derruba o ffmpeg.
- **`get_formats` virou `async`.** Comando Tauri síncrono roda na thread principal e congelava a janela enquanto o yt-dlp respondia.
- **Esperas ativas trocadas por `spawn_blocking`.** O laço que conferia a cada 50 ms prendia uma thread do runtime sem necessidade.

## 0.1.7 — navegação e defeitos de tela

- **"Voltar" volta para a última decisão.** A verificação de acesso e o resumo "é uma rádio, 50 vídeos" não pedem nada da pessoa, então não são destino do Voltar. O botão diz para onde vai (Link, Escolha, Itens, Formato).
- **"Cancelar" em todo passo.** Antes não havia saída a não ser voltar passo a passo.
- **A escolha do passo 2 fica guardada no App.** Ao voltar, o componente era montado de novo e perdia "só este vídeo / baixar tudo / selecionar". "Baixar tudo" já leva todos os itens e pula a seleção.
- **Trocar o link descarta o que veio dele.** Sem isso, um link novo mostrava os itens do anterior.
- **"Cancelar download" fica na barra de baixo.** Dentro do painel ele ficava cortado na janela de 900×700. O painel rola em vez de esconder o que não cabe.
- **A estimativa de tamanho é um rodapé do painel.** Antes era uma caixa escura solta e se sobrepunha ao resumo.
- **Arquivo de cookies como opção avançada.** O caminho principal continua sendo o Firefox. O `cookies.txt` fica numa área "Avançado", para não confundir quem é iniciante.
- **Redesenho compacto descartado.** Uma versão mais densa e "nativa" foi feita e revertida: perdia justamente a clareza para iniciantes e o fundo animado. Ficaram só as correções de defeito.

## 0.1.8 — agente

- **Processos filhos não herdam a entrada.** No MCP, a entrada do agente é o canal do protocolo. No Windows, um filho que herda esse pipe trava ao iniciar enquanto o agente espera a próxima mensagem nele, e o download pelo MCP nunca saía. Isso também impede um filho de ler mensagens do protocolo.
- **Checagem de update do yt-dlp a cada 6 horas, não em todo comando.** Antes, cada comando abria o yt-dlp duas vezes só para ver a versão e consultava o GitHub duas vezes, uns 10 s no total. A checagem continua porque o yt-dlp quebra quando os sites mudam. O estado fica em `tools/update-check.json`, e a versão instalada só é lida de novo quando o arquivo do yt-dlp muda.
- **O agente atualiza o yt-dlp sozinho.** Diferente da interface, não há ninguém para clicar em "Atualizar". Ele só mexe no yt-dlp que o app instalou, nunca num do sistema, e avisa em `ytdlp_updated`. Se não conseguir trocar o arquivo, segue com a versão atual e tenta de novo depois.
- **MCP com progresso e cancelamento.** As chamadas rodam em outra thread para o servidor continuar respondendo. É um download por vez, para o cancelamento saber qual processo parar.
- **Descrições das ferramentas MCP em português, por enquanto.** O roadmap de idiomas prevê passá-las para inglês, que é o padrão do ecossistema.

## Android no repositório

- **Sem chave de assinatura própria.** O APK de release é assinado com a chave de teste do computador. O app não guarda nada importante, e reinstalar não é problema, então não vale o cuidado de guardar uma chave e uma senha para sempre. Se a chave mudar, a versão nova pede para desinstalar a antiga. Os APKs antigos já tinham sido assinados com uma chave de teste que não existe mais.

## Ambiente de build

- **Windows com toolchain GNU.** O `gcc` precisa ser de 64 bits; com um de 32 bits no PATH, o `zstd-sys` não compila.
- **Testes do Rust rodam no Linux.** No Windows, o executável de teste do crate não abre por causa da DLL do Tauri.
- **O `.deb` é compilado no Ubuntu,** para depender das bibliotecas do Ubuntu 24.04.
