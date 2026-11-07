# Checklist de desenvolvimento por orquestração

Use na instalação, no início de cada tarefa, antes de integrar, ao fechar e na revisão do pacote.

## A. Instalação (uma vez por projeto)

- [ ] Seção de política colada no `CLAUDE.md`, com os placeholders preenchidos.
- [ ] `.claude/skills/desenvolvimento-multiagente/SKILL.md` existe e o nome da pasta bate com o `name`.
- [ ] Os três agents estão em `.claude/agents/` com modelo e esforço padrão.
- [ ] `.claude/settings.json` existe, com as permissões de git, e é JSON válido.
- [ ] `docs/tasks/TEMPLATE.md` existe.
- [ ] `[COMANDO_TESTAR]` e `[COMANDO_BUILD]` rodam no terminal.
- [ ] Abrir o Claude Code no projeto lista os três agentes e a skill.

## B. Verificação de configuração (uma vez, e após atualizar o Claude Code)

- [ ] A versão do Claude Code é v2.1.292 ou superior (necessária para `effort` por chamada).
- [ ] `CLAUDE_CODE_EFFORT_LEVEL` não está definida. Se estiver, o esforço por delegação é ignorado.
- [ ] `CLAUDE_CODE_SUBAGENT_MODEL_FORCE` não está definida. Se estiver, o modelo por delegação é ignorado.
- [ ] Teste de uma delegação: o relatório mostra o modelo e o esforço pedidos.
- [ ] Teste do reviewer: um `git push` de teste é recusado, e uma tentativa de editar arquivo também.
- [ ] Os aliases `sonnet`, `opus` e `haiku` apontam para os modelos esperados (confira no `/model`).

## C. Início de cada tarefa

- [ ] Nível anunciado com motivo.
- [ ] Faixa de dificuldade de cada parte delegada, com os sinais que a elevaram.
- [ ] `git status` lido e mudanças existentes preservadas.
- [ ] `docs/tasks/` consultado para não repetir investigação.
- [ ] Para Complexa: registro criado e marcos propostos, com aprovação antes do Marco 1.
- [ ] Delegação só quando há trabalho independente que vale a coordenação.

## D. Durante a delegação

- [ ] Cada subagent recebeu objetivo, escopo, responsabilidade, critério de aceite, entregáveis e as regras nomeadas.
- [ ] Modelo e esforço escolhidos, passados na chamada e registrados com motivo.
- [ ] Revisor com modelo igual ou superior ao implementer da mesma tarefa.
- [ ] No máximo três subagents simultâneos.
- [ ] Implementers em arquivos disjuntos.
- [ ] Relatórios lidos e conferidos antes de integrar. Contradições resolvidas e registradas.

## E. Antes de fechar

- [ ] `[COMANDO_BUILD]` passa.
- [ ] `[COMANDO_TESTAR]` passa, com o resultado real registrado.
- [ ] `git diff` contém só o escopo aprovado.
- [ ] Nenhum teste foi alterado para passar, ou a alteração foi justificada.
- [ ] Revisão independente rodou (Intermediária e Complexa) e os bloqueantes foram resolvidos.
- [ ] Se houve dois bloqueios na mesma entrega, houve escalonamento registrado.
- [ ] Registro atualizado: delegações com modelo e esforço, decisões, defeitos, verificação, pendências e próximo passo exato.
- [ ] Entrega com agentes que rodaram, modelos, arquivos, verificação, limitações e recomendação de agentes.

## F. Marcos (Complexa)

- [ ] Cada marco tem critério de saída, faixa prevista e aprovação registrados.
- [ ] Nenhum marco começou antes da aprovação do anterior.
- [ ] Ao fechar um marco, o registro traz o próximo passo exato e a frase de retomada.

## G. Medição e calibração (a cada 3 a 4 semanas)

- [ ] Há dados por faixa (qualidade e custo) suficientes para mudar a tabela? Se não, continue medindo.
- [ ] Alguma faixa mais barata entregou o mesmo resultado de forma consistente? Só então rebaixe.
- [ ] Algum escalonamento foi desnecessário? Ajuste os sinais de dificuldade.
- [ ] O limite de três subagents continua válido para este projeto? Se mudou, meça antes.
- [ ] Os agentes contradizem a seção de política? Corrija a que estiver desatualizada.
- [ ] O `CLAUDE.md` passou de ~100 linhas? Mova procedimento para a skill.

## Sinais de problema

| Sintoma | Causa provável | Ajuste |
|---|---|---|
| Orquestrador faz tudo sozinho | Tarefa tratada como trivial | Reclassificar e delegar o que for independente |
| Subagent amplia o escopo | Delegação vaga | Reescrever com escopo fechado e arquivos listados |
| Dois subagents editam o mesmo arquivo | Escopos não disjuntos | Sequenciar ou separar por arquivo |
| Relatório diz "testes passaram" sem comando | Verificação não executada | Exigir comando e resultado no relatório |
| Esforço escolhido não aparece no relatório | `CLAUDE_CODE_EFFORT_LEVEL` definida, ou versão antiga | Ver seção B |
| Reviewer consegue fazer `git push` | `settings.json` ausente ou sem o `deny` | Ver arquivo 08 |
| Reviewer mais fraco que o implementer | Escolha sem regra | Aplicar a regra de paridade e registrar |
| Custo alto sem ganho de qualidade | Faixa alta em tarefa padrão | Medir a faixa e revisar os sinais de dificuldade |
