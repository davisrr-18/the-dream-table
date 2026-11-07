# Prompt principal de delegação multiagente

Preencha os campos e cole. Se não souber o nível, deixe "classifique você".

````markdown
**Trabalho:** [O_QUE_QUERO, em 1 a 3 frases]
**Escopo:** [ARQUIVOS_PASTAS_OU_SUBSISTEMAS]
**Fora do escopo:** [O_QUE_NAO_MEXER]
**Nível:** [Básica / Intermediária / Complexa / classifique você]
**Pronto quando:** [CRITERIO_OBSERVAVEL, incluindo os testes que devem passar]
**Riscos que eu conheço:** [ex.: concorrência, persistência, API pública; ou "nenhum"]

Antes de começar:
1. Leia `CLAUDE.md` e a seção de desenvolvimento multiagente.
2. Verifique `git status` e preserve as mudanças existentes.
3. Consulte `docs/tasks/` antes de repetir investigações.
4. Anuncie o nível, a faixa de dificuldade de cada subagent e o motivo, em poucas linhas.

Fluxo por nível:
- **Básica** (um arquivo, objetivo claro): faça direto, sem subagents. Rode `[COMANDO_TESTAR]` e resuma o diff.
- **Intermediária** (várias partes, pede teste): use a skill `desenvolvimento-multiagente`. Escolha modelo e esforço de cada subagent pela dificuldade real da sua parte, registre a escolha e o motivo, e delegue. Reviewer ao fim, antes de fechar.
- **Complexa** (arquitetura, migração, marcos): crie o registro em `docs/tasks/`, proponha os marcos e espere aprovação. Cada marco fecha com verificação e revisão independente. Antes de cada marco, sugira uma nova conversa com a frase de retomada.

Regras de escolha de modelo:
- Qualidade vem primeiro. A faixa é ponto de partida: suba quando houver sinal de dificuldade (concorrência, persistência, contrato público, invariante entre módulos, especificação ambígua, falha anterior nesta tarefa).
- O reviewer nunca roda em modelo mais fraco que o implementer da mesma tarefa.
- Se a revisão bloquear a mesma entrega duas vezes, suba um nível no implementer e registre o motivo.
- Não delegue com o modelo mais barato só para economizar quando a tarefa é difícil.

Regras de delegação:
- Cada subagent recebe objetivo, escopo, responsabilidade (leitura ou edição), critério de aceite, entregáveis, as regras que deve aplicar (nomeadas uma a uma) e o modelo e esforço escolhidos.
- Subagents não delegam para outros subagents.
- Não invente agente nem resultado: relate apenas o que de fato rodou.

Ao terminar, entregue: agentes que rodaram, modelo e esforço de cada um, o que cada um fez, arquivos alterados, verificação com resultado, limitações e recomendação para a próxima tarefa semelhante.
````
