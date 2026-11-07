---
name: desenvolvimento-multiagente
description: Conduz tarefas de engenharia com um orquestrador e três subagents (investigator, implementer, reviewer), escolhendo modelo e esforço de cada delegação pela dificuldade real da tarefa, com marcos e aprovação para trabalho grande, revisão independente e registro das escolhas em docs/tasks. Use em tarefas Intermediárias e Complexas, ou quando o usuário pedir delegação.
---

# Desenvolvimento por orquestração

## 1. Começar
- Leia `CLAUDE.md`, a seção de desenvolvimento por orquestração e o registro em `docs/tasks/`, se existir.
- Rode `git status` e preserve as mudanças existentes.
- Confirme a configuração: `CLAUDE_CODE_EFFORT_LEVEL` e `CLAUDE_CODE_SUBAGENT_MODEL_FORCE` não devem estar definidas. Se estiverem, avise antes de seguir, porque elas anulam a escolha por delegação.
- Classifique o nível (Básica, Intermediária ou Complexa) e anuncie em uma linha.
- Se faltar informação essencial, pergunte tudo de uma vez.

## 2. Decidir a delegação
- **Básica:** faça direto. Sem subagents.
- **Intermediária:** delegue o que for independente. Investigação antes de implementação quando houver mapeamento. Revisão independente antes de fechar.
- **Complexa:** monte o plano em marcos antes de delegar qualquer coisa.

Para cada atividade, pergunte: ela é independente? Vale o custo da coordenação? Os arquivos colidem com outra tarefa em paralelo?

## 3. Escolher modelo e esforço por delegação
Para cada subagent, avalie a dificuldade real da parte delegada, não só o papel:
1. Parta da faixa indicada na seção do `CLAUDE.md` (mecânica, padrão, difícil ou arquitetural).
2. Procure sinais de dificuldade: concorrência, persistência, contrato público, invariante entre módulos, especificação ambígua, dependência não documentada, falha anterior nesta tarefa. Cada sinal presente sobe a faixa.
3. Verifique o reviewer: nunca pode rodar em modelo mais fraco que o implementer da mesma tarefa.
4. Passe `model` e `effort` na chamada de delegação. Registre modelo, esforço e o motivo no registro da tarefa.

Não escolha o modelo mais barato só para economizar em tarefa difícil. Se um subagent falhar na mesma entrega duas vezes, suba um nível no implementer e registre.

## 4. Marco 1 (Complexa): plano e aprovação
- Crie ou atualize o registro em `docs/tasks/` com objetivo, critérios de aceite, arquivos afetados, riscos e marcos.
- Proponha a faixa e o modelo previstos para os marcos seguintes.
- Use o `investigator` para o mapeamento, se o escopo exigir.
- Apresente o plano e pare. Só siga com aprovação explícita.

## 5. Delegar
Para cada subagent, escreva a tarefa com:
- Objetivo e escopo fechados.
- Arquivos ou subsistemas.
- Leitura ou edição.
- Dependências e conflitos com outras tarefas.
- Critério de aceite e verificação esperada.
- Entregáveis e condição de parada.
- As seções exatas de regras a aplicar (sem mandar ler todos os arquivos).
- Modelo e esforço escolhidos, com o motivo.

Regras de execução:
- No máximo três subagents simultâneos.
- Implementers só em arquivos disjuntos.
- Revisor diferente do implementer, e nunca em modelo mais fraco.

## 6. Integrar
- Leia cada relatório antes de aceitar a conclusão. Não confie só no resumo: confira o diff e os testes.
- Se dois subagents chegaram a conclusões que se contradizem, resolva antes de seguir e registre a decisão.
- Não reabra investigação já feita sem motivo concreto.

## 7. Verificar
- Rode `[COMANDO_TESTAR]` e `[COMANDO_BUILD]` depois da integração. Registre o resultado real. O reviewer não roda testes: esse papel é seu.
- Para tarefa Intermediária ou Complexa, o `reviewer` roda no diff completo antes do fechamento. Corrija os bloqueantes e rode de novo o que foi afetado.
- Se a revisão bloquear a mesma entrega pela segunda vez, faça o escalonamento do passo 3 e registre.
- Não reexecute testes que passaram se o código relevante não mudou.

## 8. Medir
- Registre tokens por agente quando disponível, o modelo e esforço usados, e os defeitos que a revisão encontrou.
- Essas informações alimentam a calibração da tabela de faixas. Não reduza uma faixa sem evidência consistente.

## 9. Fechar marco ou tarefa
- Atualize o registro: o que mudou, decisões com motivo, verificação com resultado, pendências e o próximo passo exato.
- Em marco de Complexa, pare e espere aprovação para o seguinte. Sugira nova conversa com uma frase de retomada, incluindo modelo e esforço recomendados.

## 10. Entregar
- Agentes que de fato rodaram, com modelo e esforço de cada um, e o que fizeram.
- Arquivos alterados.
- Verificação e resultado.
- Limitações e o que não foi testado.
- **Recomendação de agentes:** modelo e esforço adequados, se uma faixa mais barata teria bastado (com evidência), e se houve escalonamento.

## Regras
- Responda no idioma do usuário.
- Não diga que um subagent rodou sem ter rodado. Se a delegação não estiver disponível, avise.
- Não expanda o escopo aprovado para dentro da delegação.
- Não faça commit ou push sem a política do projeto autorizar. Se autorizar, faça só ao fim de uma etapa verificada.
- Sem segredos, dados pessoais ou logs grandes no registro.
