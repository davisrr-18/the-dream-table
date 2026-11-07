# Pacote global de desenvolvimento por orquestração

**Para que serve:** conduzir trabalho de engenharia com um orquestrador (sessão principal) e três subagents (investigator, implementer e reviewer). O orquestrador escolhe, para cada delegação, o modelo e o esforço adequados à dificuldade real da parte delegada. A prioridade é qualidade; custo é critério de desempate.

**Decisões de desenho (conferidas na documentação oficial do Claude Code):**
- Permissões de git somente leitura passam para `settings.json` (`permissions.allow` e `permissions.deny`), porque o campo `tools` não aceita padrões como `Bash(git diff:*)`.
- O reviewer usa `disallowedTools` para bloquear edição, e `tools` lista só `Bash` como ferramenta de shell.
- Esforço por chamada exige Claude Code v2.1.292 ou superior. Uma variável de ambiente `CLAUDE_CODE_EFFORT_LEVEL` sobrepõe tudo, então a checklist verifica se ela está definida.
- Uma variável `CLAUDE_CODE_SUBAGENT_MODEL_FORCE` desativa a escolha de modelo por chamada. A checklist também verifica isso.

**Origem:** a estrutura veio do The Dream Table. Os números de referência (limite de três subagents simultâneos e cerca de 25% de economia) foram medidos lá, em uma tarefa específica. Em outro projeto, meça de novo antes de tratá-los como verdade.

**Nível:** depende do pedido, não do pacote. Tarefa pequena segue direto, sem subagents. Tarefa substancial usa delegação. Mudança arquitetural ou de longo prazo usa marcos com aprovação.

## Arquivos do pacote

| # | Arquivo | Tipo | Destino no projeto |
|---|---|---|---|
| 01 | `01-prompt-principal.md` | Prompt | Colar ao delegar um trabalho |
| 02 | `02-trecho-claude-md.md` | Regras | Seção de política no `CLAUDE.md` |
| 03 | `03-skill-desenvolvimento-multiagente.md` | Skill | `.claude/skills/desenvolvimento-multiagente/SKILL.md` |
| 04 | `04-agent-investigator.md` | Agent | `.claude/agents/investigator.md` |
| 05 | `05-agent-implementer.md` | Agent | `.claude/agents/implementer.md` |
| 06 | `06-agent-reviewer.md` | Agent | `.claude/agents/reviewer.md` |
| 07 | `07-modelo-registro-de-tarefa.md` | Modelo | `docs/tasks/TEMPLATE.md` |
| 08 | `08-settings-json.md` | Permissões | `.claude/settings.json` |
| 09 | `09-checklist.md` | Checklist | Consulta (não precisa instalar) |

## Placeholders a preencher (uma vez por projeto)

| Placeholder | Exemplo |
|---|---|
| `[PROJETO]` | nome do projeto |
| `[LINGUAGEM]` | Python, TypeScript, Java |
| `[COMANDO_TESTAR]` | `pytest`, `npm test`, `./gradlew test` |
| `[COMANDO_BUILD]` | `npm run build`, `./gradlew build` |
| `[MODELO_PRINCIPAL]` / `[ESFORCO_PRINCIPAL]` | o que estiver configurado na sessão principal |

Os modelos dos subagents não são placeholders: o orquestrador escolhe a cada delegação, seguindo a seção do `CLAUDE.md`.

## Ordem de instalação

1. Copie a seção do arquivo 02 para o `CLAUDE.md` e preencha os placeholders.
2. Crie a skill, os três agents e o `settings.json` em `.claude/`.
3. Crie `docs/tasks/TEMPLATE.md` com o modelo do arquivo 07.
4. Rode a checklist do arquivo 09, incluindo a seção de verificação de configuração.
5. Delegue a primeira tarefa com o prompt do arquivo 01.
