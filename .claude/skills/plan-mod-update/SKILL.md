---
name: plan-mod-update
description: Planeja atualizações do The Dream Table com marcos, delegação e controle de custos.
---

# Planejamento de atualizações

Quando solicitado, transformar os requisitos da atualização em um plano executável.

## Procedimento

1. Ler as regras do CLAUDE.md.
2. Identificar requisitos e dependências.
3. Avaliar a complexidade e delegar investigações independentes somente quando houver benefício.
4. Consolidar relatórios compactos.
5. Dividir o trabalho em marcos verificáveis.
6. Definir testes e gates de qualidade.
7. Avaliar se alguma tarefa justifica escalonamento para um modelo ou esforço superior, sem escalonar sem autorização.
8. Registrar decisões e pendências.

Para atualizações relevantes, apresentar o escopo inicial e solicitar autorização antes de iniciar o Marco 1.

Ao concluir cada marco, apresentar resultados e solicitar aprovação explícita antes de iniciar o próximo.

A autorização de um marco cobre apenas seu escopo aprovado.

## Estratégia de execução delegada

Antes de definir a execução de uma atualização, avaliar a complexidade técnica e a viabilidade de delegação.

### Classificação de complexidade

Classificar cada tarefa ou conjunto de tarefas considerando:

- Complexidade lógica e arquitetural.
- Quantidade de arquivos e subsistemas envolvidos.
- Dependências entre implementações.
- Riscos de regressão e compatibilidade.
- Necessidade de testes e revisão.
- Possibilidade de execução independente.
- Custo de coordenação e contexto.

Usar quatro níveis: baixa, média, alta e crítica.

A complexidade não determina automaticamente a quantidade de subagentes.

### Seleção adaptativa de agentes

O agente principal deve decidir entre execução direta e delegação de acordo com o benefício esperado.

Papéis, quando disponíveis e verificados no ambiente:

- Agente principal: orquestração, integração, implementação direta e decisões.
- `investigator`: investigação delimitada de código e requisitos.
- `implementer`: implementação isolada de tarefas aprovadas.
- `reviewer`: revisão independente de alterações e riscos.
- Escalonamento: modelo ou esforço superior em casos excepcionais, sujeito à aprovação do usuário.

Os subagentes são definidos em `.claude/agents/*.md`, com os mesmos nomes e responsabilidades.

Antes de delegar, confirmar que o papel está carregado na sessão atual. Se não estiver, executar diretamente ou informar o usuário; não substituir silenciosamente por outro agente.

O modelo e o esforço de cada papel vêm da definição do agente em `.claude/agents/*.md`. Seguir as preferências de modelo do CLAUDE.md somente quando os modelos citados existirem no ambiente.

Utilizar no máximo três subagentes simultâneos, conforme os limites efetivamente configurados.

Não criar agentes por obrigação. Não repetir trabalho delegado sem necessidade.

### Pipeline de engenharia delegada

A execução deve seguir uma ordem lógica, com delegações adaptativas:

1. **Investigar:** compreender o problema e identificar os arquivos, dependências, riscos e testes relevantes.
2. **Planejar:** decompor o trabalho, definir responsabilidades e obter aprovação.
3. **Implementar:** executar diretamente ou delegar partes independentes do escopo aprovado.
4. **Integrar:** consolidar alterações e resolver incompatibilidades.
5. **Revisar:** realizar revisão independente das alterações relevantes.
6. **Corrigir:** tratar problemas confirmados e executar os testes relacionados.
7. **Validar:** verificar os critérios de aceitação e registrar os resultados.

Essas atividades devem ocorrer dentro dos três marcos aprovados.

O pipeline não exige um subagente diferente para cada atividade.

O agente principal deve escolher o menor conjunto de agentes capaz de entregar o resultado com qualidade.

Evitar ciclos indefinidos de implementação e revisão. Após cada revisão, corrigir apenas problemas concretos, documentar riscos restantes e encerrar quando os critérios de saída estiverem satisfeitos.

### Contrato de delegação

Cada tarefa delegada deve especificar:

- Objetivo.
- Escopo e arquivos relevantes.
- Permissão de leitura ou edição.
- Dependências e possíveis conflitos.
- Critérios de aceitação.
- Testes esperados.
- Formato conciso de entrega.
- Condição objetiva para encerrar a delegação.
- Evidências e referências necessárias para validar o resultado sem repetir a investigação.

Subagentes com permissão de edição não devem modificar simultaneamente os mesmos arquivos sem uma estratégia explícita de isolamento.

### Plano de delegação

Para atualizações relevantes, apresentar uma tabela com:

| Tarefa | Complexidade | Responsável | Dependências | Critério de saída |
|--------|--------------|-------------|--------------|-------------------|

A tabela deve conter somente tarefas realmente necessárias.

Identificar quais tarefas podem ocorrer em paralelo e quais precisam ocorrer sequencialmente.

Quando nenhuma delegação for necessária, registrar brevemente a justificativa.

A revisão independente não deve ser atribuída ao mesmo agente que implementou a alteração revisada, sempre que houver um revisor disponível.

Não iniciar atividades do próximo marco antes da aprovação correspondente.

### Integração com os marcos

Marco 1 — Investigação e planejamento:
- Delegar investigações independentes quando necessário.
- Consolidar descobertas em um plano único.
- Definir tarefas, dependências, responsáveis e critérios de saída.
- Solicitar aprovação antes do Marco 2.

Marco 2 — Implementação:
- Implementar incrementalmente o escopo aprovado.
- Delegar partes isoladas quando isso for vantajoso.
- Integrar resultados continuamente.
- Executar testes focados.
- Solicitar aprovação antes do Marco 3.

Marco 3 — Validação:
- Realizar revisão independente proporcional ao risco.
- Corrigir problemas confirmados.
- Executar testes de regressão e build pertinentes.
- Registrar riscos e pendências.
- Solicitar aprovação para encerramento.

### Política de eficiência

Priorizar trabalho validado com o menor custo razoável.

- Evitar leituras repetidas do repositório.
- Reutilizar descobertas e decisões persistidas.
- Evitar relatórios longos sem evidências adicionais.
- Não revisar novamente um diff inalterado sem justificativa.
- Usar subagentes somente quando proporcionarem benefício.
- Não reduzir testes ou critérios de qualidade para economizar cota de uso.

Antes de criar um subagente, verificar se a tarefa já foi resolvida, investigada ou documentada. Encerrar a delegação quando as evidências necessárias forem suficientes, sem continuar explorando caminhos não relacionados.

### Registro da execução

Ao final de cada marco, registrar:

- Agentes efetivamente utilizados.
- Tarefas delegadas e resultados.
- Decisões técnicas.
- Arquivos afetados.
- Testes executados.
- Problemas e riscos restantes.
- Próximo passo aprovado.

Não duplicar informações já registradas no ledger existente.
