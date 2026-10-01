# QA-01: Plano de Testes do Projeto

| Metadado | Detalhe |
| :--- | :--- |
| **Referência da Demanda** | Issue GitLab #20 (`QA-01: Plano de testes do projeto`) |
| **Autor** | João Pedro Leite Calsavara |
| **Data de Criação** | 2026-09-30 |
| **Status** | Em Validação |
| **Milestone** | Sprint 1 |
| **Diretrizes Base** | [ADR 0001 (Estratégia de Testes)](../adr/0001-estrategia-de-testes-integracao-e-e2e.md), [ADR 0002 (Padrões de Cenários)](../adr/0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md), [AGENTS.md](../../AGENTS.md) |

---

## 1. Introdução e Objetivos

O objetivo deste Plano de Testes é definir formalmente a estratégia, os níveis de teste, os critérios de aceitação, os papéis e responsabilidades e a matriz de rastreabilidade de testes para as entregas da Sprint 1 e subsequentes do projeto.

Este plano padroniza os critérios de garantia de qualidade para o backlog de funcionalidades (US-01 a US-08), alinhando o desenvolvimento à política de qualidade e decisões arquiteturais do repositório:
- **Exclusão explícita de testes unitários isolados com mocks** de banco ou regras de negócio ([ADR 0001](../adr/0001-estrategia-de-testes-integracao-e-e2e.md)).
- **Cobertura obrigatória de cenários tripartite** (Casos Bons, Ruins e Incompletos) em cada endpoint da API ([ADR 0002](../adr/0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md)).
- **Validação estática e semântica automatizada** via SonarCloud e AI Gatekeeper Reviewer em pipeline CI/CD.

---

## 2. Escopo de Testes

### 2.1. Funcionalidades no Escopo (Sprint 1)
O escopo prioriza as histórias com classificação de prioridade Alta e Média do backlog:

1. **US-01: Login e perfis de acesso** (Média): Autenticação de credenciais, segregação de perfis (Admin vs. Cliente) e controle de sessão/logout.
2. **US-02: Gestão do catálogo de taxas** (Média / Alta): CRUD de taxas com tipagens (Fixa, Por metragem, Variável), validação de valores monetários e regras de desativação sem efeito retroativo.
3. **US-03: Criação de projeto do cliente** (Alta): Cadastro de projeto, credenciais temporárias do cliente, definição de prazos limites (envio de PDF e pagamento) e amarração de taxas.
4. **US-04: Envio do PDF do estande** (Baixa / Média): Upload de arquivo PDF, validação de extensão/tamanho (ADR 002), registro de m² e transição de status para "PDF em análise".
5. **US-05: Visualização das taxas do projeto** (Média): Consulta de taxas condicionada à aprovação do PDF, exibição de status, cálculos automáticos de taxas por metragem e valores fixos.
6. **US-06: Input de taxas e dados do cliente** (Média): Preenchimento e edição de quantidades de consumo (energia, água), transição para "Aguardando cotação" com bloqueio pós-faturamento.
7. **US-07: Cotação de taxas** (Alta): Painel administrativo para atribuição de preços em R$ a taxas pendentes de cotação e recotação com auditoria de usuário/timestamp.
8. **US-08: Aprovação e reprovação do PDF** (Média): Fluxo de decisão do Admin, exigência de justificativa em reprovação, liberação de taxas em aprovação e auditoria de decisão.

### 2.2. Funcionalidades Fora do Escopo Inicial
- Integração em produção direta com gateway Mercado Pago (coberta via sandbox e mocks de serviço externo no TEC-02/TEC-05).
- Testes de carga massiva ou estresse distribuído de infraestrutura nesta fase.

---

## 3. Níveis e Abordagem de Testes

Conforme deliberado na [ADR 0001](../adr/0001-estrategia-de-testes-integracao-e-e2e.md), a estratégia apoia-se no Troféu de Testes:

```text
               ┌────────────────────────┐
               │    Testes Manuais      │ (Conferência de critérios de aceite)
               ├────────────────────────┤
               │   Testes E2E (Cypress) │ (Jornadas completas de navegador)
               ├────────────────────────┤
               │  Testes de Integração  │ (API REST + Spring Boot + PostgreSQL)
               │ (Matriz Tripartite)    │
               └────────────────────────┘
```

### 3.1. Testes de Integração (API / Backend)
- **Base Tecnológica**: Java 21, Spring Boot Test (`@SpringBootTest`, `TestRestTemplate` / `MockMvc` integrado ao contexto completo), PostgreSQL via Docker.
- **Estrutura Obrigatória por Endpoint (ADR 0002)**:
  - **Casos Bons (Happy Path)**: Payloads íntegros, status HTTP 200/201, validação de integridade referencial no banco e DTOs de resposta conformes.
  - **Casos Ruins (Sad Path / Regras de Negócio)**: Violações de regras de negócio, dados duplicados, status HTTP 401, 403, 404, 409, 422, garantindo atomicidade transacional e ausência de escrita suja.
  - **Casos Incompletos (Boundary & Malformed Payloads)**: Campos nulos, strings vazias, valores fora de faixa, tipos incompatíveis, assegurando resposta HTTP 400 Bad Request estruturada no padrão RFC 7807 (`ProblemDetail`) sem erros HTTP 500 não tratados.

### 3.2. Testes End-to-End (E2E / Frontend)
- **Base Tecnológica**: Cypress em execução headless e interativa contra o frontend Vite/React integrado à API local.
- **Escopo de Aplicação**: Jornadas críticas completas (Login e permissão de rotas, ciclo de criação de projeto e amarração de taxas, aprovação/reprovação de PDF com feedback em tela).

### 3.3. Testes Manuais de Aceite
- **Execução**: Validação cruzada por pares (peer review) ou responsável de QA.
- **Roteiro**: Verificação ponta a ponta dos critérios de aceite descritos na issue antes de transicionar a história para aprovação.

---

## 4. Critério de "Aprovado para Done" (Definition of Done - DoD)

Uma história de usuário (US-XX) só poderá ser fechada e considerada `Done` quando cumprir cumulativamente os seguintes critérios:

1. **Critérios de Aceite Verificados**: Todos os itens da seção "Critérios de aceite" da issue foram validados manualmente em ambiente local ou staging.
2. **Testes de Integração Tripartite Aprovados**: Existência e execução com sucesso de testes de integração cobrindo os cenários bons, ruins e incompletos de cada endpoint implementado.
3. **Testes E2E Aprovados**: Testes Cypress cobrindo os fluxos de interface da funcionalidade integrados à API.
4. **Zero Regressão e Sem Mocks Internos**: Ausência de mocks de classes de serviço ou repositórios; banco de dados real testado.
5. **Quality Gate Aprovado**:
   - SonarCloud Quality Gate com status `Passed` (zero bugs e vulnerabilidades bloqueantes).
   - AI Gatekeeper Reviewer executado localmente e no CI/CD com veredito `APPROVED`.
6. **Revisão por Pares**: Merge Request para `dev` aprovado por no mínimo 2 integrantes da equipe, conforme a política de branching do projeto.

---

## 5. Papéis e Responsabilidades ("Quem Testa o Quê")

| Papel | Responsável | Atribuições de Teste |
| :--- | :--- | :--- |
| **Desenvolvedor da Funcionalidade** | Autor da branch `member/<slug>` | - Implementar suíte de testes de integração cobrindo cenários tripartite.<br>- Implementar os testes E2E correspondentes no Cypress.<br>- Garantir execução verde local do script de revisão do AI Gatekeeper antes de submeter o MR. |
| **Responsável de QA / Revisor Técnico** | Integrante alocado para QA / Revisor do MR | - Executar conferência manual dos critérios de aceite da issue.<br>- Auditar a completude dos cenários de teste da suíte de integração.<br>- Homologar a rastreabilidade entre issue e casos de teste. |
| **Automação CI/CD & AI Gatekeeper** | GitHub Actions / GitLab Bridge | - Executar compilação e suítes completas de testes automatizados.<br>- Validar conformidade arquitetural com ADRs e regras estáticas via SonarCloud.<br>- Bloquear merge caso haja falha ou violação de diretrizes. |

---

## 6. Ambiente e Infraestrutura de Teste

1. **Banco de Dados de Testes**: PostgreSQL 16 provisionado via `docker-compose.yml` (`localhost:5432`, base `petshop_db`, isolamento por transações ou limpeza por fixture).
2. **Servidor Backend**: Spring Boot operando em perfil `test` com propriedades alinhadas à porta do PostgreSQL local.
3. **Servidor Frontend**: Aplicação Vite rodando em `http://localhost:5173`.
4. **Executor Local**:
   ```bash
   # Execução de testes de integração backend
   ./mvnw test

   # Execução de testes E2E frontend
   npx cypress run

   # Validação de Quality Gate completo
   bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target .
   ```

---

## 7. Matriz de Rastreabilidade Simples (US-XX -> Casos de Teste)

Padrão de nomenclatura adotado: `CT-<US>-<TIPO>-<SEQ>`:
- `INT`: Teste de Integração (API / DB)
- `E2E`: Teste End-to-End (Cypress / Interface)
- `MAN`: Validação Manual de Critérios de Aceite

| História | Funcionalidade | ID do Caso de Teste | Descrição Resumida do Cenário | Tipo de Teste |
| :--- | :--- | :--- | :--- | :--- |
| **US-01** | Login e Perfis | `CT-US01-INT-01` | Autenticação com credenciais válidas e retorno de token/sessão (Caso Bom) | Integração |
| | | `CT-US01-INT-02` | Tentativa com senha incorreta ou usuário inexistente (HTTP 401) (Caso Ruim) | Integração |
| | | `CT-US01-INT-03` | Requisição com e-mail inválido ou campos nulos (HTTP 400) (Caso Incompleto) | Integração |
| | | `CT-US01-E2E-01` | Redirecionamento condicional pós-login (Admin para Painel, Cliente para Projetos) | E2E |
| | | `CT-US01-MAN-01` | Verificação visual de mensagens de erro amigáveis e fluxo de logout | Manual |
| **US-02** | Catálogo de Taxas | `CT-US02-INT-01` | Criação de taxa com dados válidos de cada tipo (Fixa, Metragem, Variável) (Caso Bom) | Integração |
| | | `CT-US02-INT-02` | Bloqueio de taxa com nome duplicado ou edição inválida (HTTP 409/422) (Caso Ruim) | Integração |
| | | `CT-US02-INT-03` | Payload com valor negativo ou campos obrigatórios ausentes (HTTP 400) (Caso Incompleto) | Integração |
| | | `CT-US02-E2E-01` | Listagem, criação e desativação de taxas na interface administrativa | E2E |
| | | `CT-US02-MAN-01` | Conferência de que desativação não afeta pagamentos previamente gerados | Manual |
| **US-03** | Criação de Projeto | `CT-US03-INT-01` | Cadastro de projeto com CPF/CNPJ único, prazos válidos e taxas associadas (Caso Bom) | Integração |
| | | `CT-US03-INT-02` | Rejeição de prazo de pagamento anterior ao de envio de PDF ou CPF/CNPJ duplicado (Caso Ruim) | Integração |
| | | `CT-US03-INT-03` | Envio sem razão social, e-mail malformado ou prazos nulos (HTTP 400) (Caso Incompleto) | Integração |
| | | `CT-US03-E2E-01` | Criação de projeto via formulário do Admin com geração de credencial temporária | E2E |
| | | `CT-US03-MAN-01` | Validação de exibição da senha temporária para repasse ao cliente | Manual |
| **US-04** | Envio do PDF | `CT-US04-INT-01` | Upload bem-sucedido de PDF com m² informado, status muda para "PDF em análise" (Caso Bom) | Integração |
| | | `CT-US04-INT-02` | Upload de arquivo com extensão não permitida (.exe/.png) ou acima do limite (Caso Ruim) | Integração |
| | | `CT-US04-INT-03` | Envio de requisição multipart sem arquivo ou sem campo de m² (HTTP 400) (Caso Incompleto) | Integração |
| | | `CT-US04-E2E-01` | Interface de upload do cliente com feedback de progresso e status atualizado | E2E |
| | | `CT-US04-MAN-01` | Download e integridade do arquivo enviado via link do painel | Manual |
| **US-05** | Visualização de Taxas | `CT-US05-INT-01` | Consulta de taxas após aprovação do PDF com valores calculados (Caso Bom) | Integração |
| | | `CT-US05-INT-02` | Acesso às taxas com PDF ainda pendente ou reprovado (bloqueio de visualização) (Caso Ruim) | Integração |
| | | `CT-US05-INT-03` | Consulta informando identificador de projeto inválido ou malformado (HTTP 400/404) (Caso Incompleto) | Integração |
| | | `CT-US05-E2E-01` | Painel do Cliente exibindo discriminação correta entre taxas fixas, por m² e variáveis | E2E |
| | | `CT-US05-MAN-01` | Conferência visual do prazo de pagamento e status de cada item | Manual |
| **US-06** | Input de Taxas | `CT-US06-INT-01` | Atualização de quantidades de energia/água com transição para "Aguardando cotação" (Caso Bom) | Integração |
| | | `CT-US06-INT-02` | Tentativa de alteração de quantidade após pagamento já gerado (HTTP 409/422) (Caso Ruim) | Integração |
| | | `CT-US06-INT-03` | Envio de quantidade nula, negativa ou zero (HTTP 400) (Caso Incompleto) | Integração |
| | | `CT-US06-E2E-01` | Fluxo do cliente informando e alterando quantidades de insumos | E2E |
| | | `CT-US06-MAN-01` | Verificação do retorno de status para "Aguardando cotação" a cada edição | Manual |
| **US-07** | Cotação de Taxas | `CT-US07-INT-01` | Atribuição de valor em R$ pelo Admin com mudança de status para "Cotada" e auditoria (Caso Bom) | Integração |
| | | `CT-US07-INT-02` | Tentativa de recotação em taxa já faturada/paga (HTTP 409) (Caso Ruim) | Integração |
| | | `CT-US07-INT-03` | Envio de valor negativo, não numérico ou sem ID da taxa (HTTP 400) (Caso Incompleto) | Integração |
| | | `CT-US07-E2E-01` | Painel de cotações pendentes com preenchimento de valor e notificação | E2E |
| | | `CT-US07-MAN-01` | Conferência dos registros de auditoria (quem cotou e timestamp) | Manual |
| **US-08** | Decisão do PDF | `CT-US08-INT-01` | Aprovação do PDF mudando status para "Aprovado" e liberando taxas (Caso Bom) | Integração |
| | | `CT-US08-INT-02` | Reprovação sem preenchimento de justificativa obrigatória (HTTP 422) (Caso Ruim) | Integração |
| | | `CT-US08-INT-03` | Decisão enviada com payload nulo ou status inválido (HTTP 400) (Caso Incompleto) | Integração |
| | | `CT-US08-E2E-01` | Fluxo completo do Admin filtrando projetos em análise e emitindo decisão | E2E |
| | | `CT-US08-MAN-01` | Conferência de que justificativa de reprovação aparece corretamente para o Cliente | Manual |

---

## 8. Próximos Passos (Transição para QA-02)

Com a aprovação deste Plano de Testes (QA-01), o detalhamento fino de cada caso de teste (massa de dados, passos de execução, asserts específicos e automação de scripts) será elaborado na demanda subsequente **QA-02: Casos de teste do projeto**.
