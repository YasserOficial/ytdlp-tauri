# Roadmap: suporte a vários idiomas

Parte do [roadmap geral](ROADMAP.md).

**Objetivo:** cada idioma é **um único arquivo**. Para adicionar espanhol, por exemplo, basta copiar `en.json` para `es.json` e traduzir. O app e o agente encontram o arquivo novo sozinhos na próxima compilação, sem mexer em código.

**Versão alvo:** 0.2.0, com português e inglês.

## Ponto de partida

Hoje todo o texto está escrito direto no código, em português:

- **Interface:** cerca de 200 textos espalhados nas 9 telas (títulos, botões, dicas, mensagens de erro).
- **Rust:** uns 90 textos que chegam até a pessoa, como mensagens de erro, textos de progresso ("Mesclando…", "Download cancelado") e a ajuda e as descrições do agente.
- **Fora do código:** o README, o instalador e o texto das releases.

No total são uns **300 textos**.

## Como vai funcionar

```
locales/
  pt-BR.json   ← idioma base e reserva
  en.json
  es.json      ← um idioma novo é só mais um arquivo aqui
```

```json
{
  "_language": "English",
  "step1.title": "📎 Step 1: Paste the link",
  "step3.items": { "one": "{count} item", "other": "{count} items" },
  "error.download_failed": "The download failed:\n{details}"
}
```

- **Uma pasta para os dois lados.** A interface lê os arquivos pelo Vite. O Rust embute os mesmos arquivos no executável na hora de compilar, então o agente funciona sem nenhum arquivo extra ao lado.
- **Idioma novo entra sozinho.** Um script na compilação lê a pasta `locales/`, e por isso não existe lista de idiomas para manter.
- **Uma função `t()` em cada lado, com as mesmas regras:** variáveis como `{count}` e plural simples (`one`/`other`). A ideia é uma função pequena própria em vez de uma biblioteca como a vue-i18n, porque o Rust precisa ler exatamente o mesmo formato.
- **Reserva:** se faltar um texto no idioma escolhido, aparece o português. Se faltar no português também, aparece a chave, o que deixa o erro visível.
- **Qual idioma usar:** o do sistema, ou um escolhido na interface que fica salvo. O agente aceita `--lang en` e a variável `YTDLP_GUI_LANG`. Sem nenhum dos dois, também segue o sistema.
- **Android também:** depois da unificação dos projetos (ver [ROADMAP.md](ROADMAP.md)), o app Android lê os mesmos `locales/*.json`, convertidos para o formato de textos do Android na hora de compilar. Um arquivo traduz as três plataformas.
- **Fica em inglês, como vem:** a mensagem bruta do yt-dlp que aparece em "Mensagem do yt-dlp". Ela vem do yt-dlp e não dá para traduzir.

## Fases

### Fase 1: base, sem mudança visível

- Criar `locales/pt-BR.json`, a `t()` da interface e a `t()` do Rust.
- Criar o script de compilação que encontra os idiomas.
- Criar um verificador (`npm run i18n:check`) que lista o que falta ou sobra em cada idioma comparado ao português.

**Pronto quando** o app estiver idêntico ao atual.

### Fase 2: tirar os textos do código

- Passar os ~200 textos das telas e os ~90 do Rust para chaves no `pt-BR.json`.
- Transformar em chave com variáveis os textos montados por partes, como "Todos os X arquivos foram baixados".

**Pronto quando** nenhuma tela tiver texto escrito direto no código e todas as telas continuarem iguais, conferido com capturas de tela de antes e depois.

### Fase 3: inglês na interface

- Criar o `en.json` completo.
- Detectar o idioma do sistema e pôr um seletor de idioma visível (proposta: canto do cabeçalho e tela inicial).
- O Rust passa a responder no idioma escolhido.
- Revisar o layout, porque alguns textos em inglês ficam maiores ou menores.

**Pronto quando** o app inteiro funcionar em inglês, inclusive os erros e o progresso.

### Fase 4: agente

- Ajuda e mensagens seguindo `--lang` ou o idioma do sistema.
- Descrições das ferramentas MCP **sempre em inglês**, que é o padrão do ecossistema e o que os modelos de linguagem esperam.

### Fase 5: fora do app

- `README.en.md`, com um link no topo de cada README para o outro.
- Instalador do Windows em português e inglês.
- Descrição do pacote `.deb` em inglês.
- Notas das releases nos dois idiomas.

### Fase 6: manutenção

- Guia curto "como adicionar um idioma" no README.
- O verificador roda na compilação e avisa quando faltar tradução, para nenhum idioma ficar para trás quando entrar um texto novo.

## Riscos e decisões em aberto

- **Textos montados por partes:** o português às vezes junta frases de um jeito que não funciona em outra língua. Esses viram chaves inteiras com variáveis.
- **Emoji nos títulos:** ficam dentro da tradução, então cada idioma pode manter ou trocar.
- **Idiomas da direita para a esquerda (árabe, hebraico):** ficam fora deste plano e exigiriam ajustes de layout.
- **Seletor de idioma:** a posição ainda está em aberto; a proposta é o cabeçalho, mais a tela inicial.

## Andamento

- [ ] Fase 1: base
- [ ] Fase 2: textos fora do código
- [ ] Fase 3: inglês na interface
- [ ] Fase 4: agente
- [ ] Fase 5: fora do app
- [ ] Fase 6: manutenção
