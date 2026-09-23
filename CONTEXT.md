# SI600 Web Petshop — Context & Ubiquitous Language

## 1. Visão Geral do Sistema

O **SI600 Web Petshop** é um sistema web integrado para gerenciamento operacional e comercial de um pet shop com atendimento clínico e estético. A plataforma atende dois públicos principais:
1. **Clientes (Tutores)**: Navegam pelos serviços oferecidos, cadastram seus animais de estimação, realizam e acompanham agendamentos (banho, tosa, consultas veterinárias) e visualizam histórico.
2. **Administradores e Colaboradores (Atendentes, Tosadores, Veterinários)**: Gerenciam a agenda, controlam o fluxo de atendimento, atualizam status das ordens de serviço e administram o catálogo de serviços e clientes.

---

## 2. Linguagem Ubíqua (Domain Vocabulary)

Termo | Definição | Sinônimos Evitados
:--- | :--- | :---
**`Cliente`** | Pessoa física titular do cadastro, proprietária de um ou mais pets. Possui identificador único, nome, e-mail, telefone e documento. | *Usuário comum*, *Consumidor*
**`Pet`** | Animal sob tutela de um `Cliente`. Contém nome, espécie (ex: canino, felino), porte (pequeno, médio, grande), raça, idade e observações comportamentais ou restrições de saúde. | *Bicho*, *Animalzinho*
**`Servico`** | Procedimento ofertado pelo pet shop (ex: Banho e Tosa Higiênica, Tosa Bebê, Consulta Clínica Geral, Vacinação V10). Possui preço, duração estimada em minutos e restrições de porte/espécie. | *Produto*, *Procedimento*
**`Profissional`** | Membro da equipe responsável pela execução técnica do serviço (ex: médico veterinário com CRMV, tosador qualificado). | *Funcionário genérico*, *Operador*
**`Agendamento`** | Reserva formal de um horário em que um ou mais `Servico`s serão prestados a um `Pet` específico, alocando recursos e eventualmente um `Profissional`. | *Marcação*, *Ticket de horário*
**`StatusAgendamento`** | Máquina de estados do agendamento: `PENDENTE`, `CONFIRMADO`, `EM_ANDAMENTO`, `CONCLUIDO`, `CANCELADO`. | *Estado*, *Situação*
**`OrdemDeServico`** | Registro da execução real do serviço após o check-in do pet na unidade, incluindo anotações clínicas/estéticas e fechamento financeiro. | *Comanda*, *OS genérica*

---

## 3. Invariantes e Regras de Negócio Fundamentais

1. **Unicidade de Agendamento por Profissional/Recurso**: Não é permitido criar ou confirmar dois agendamentos no mesmo intervalo de tempo para o mesmo profissional ou para a mesma baia/mesa de atendimento.
2. **Pertença do Pet**: Um `Agendamento` só pode ser solicitado para um `Pet` cujo tutor corresponda ao `Cliente` autenticado (exceto em operações realizadas por administradores/atendentes).
3. **Imutabilidade e Transições de Estado**:
   - Agendamentos com status `CONCLUIDO` ou `CANCELADO` são finais e não permitem alteração de data, serviço ou valor.
   - Cancelamento só é autorizado se o status for `PENDENTE` ou `CONFIRMADO` e antes do início do horário agendado.
4. **Validação de Entrada Rigorosa**:
   - Payloads incompletos, ausência de campos obrigatórios (`clienteId`, `petId`, `servicoId`, `dataHoraInicio`) ou valores inválidos (datas no passado, preços negativos) devem ser barrados imediatamente com HTTP 400/422.

---

## 4. Stack Tecnológica Oficial & Padrões de Desenvolvimento

Camada | Tecnologia | Detalhes & Padrões
:--- | :--- | :---
**Backend** | **Java 21 / Spring Boot** | REST API sob `/api/...`, Spring Data JPA, Hibernate, Bean Validation (`@Valid`), Problem Details (RFC 7807 via `@RestControllerAdvice`). Arquitetura em camadas desacopladas (`controller`, `service`, `repository`, `entity`, `dto`, `exception`).
**Frontend** | **React 18+ (Vite) + TS** | SPA moderna construída com Vite, componentes funcionais modulares, tipagem TypeScript estrita e cliente HTTP centralizado consumindo o backend.
**Banco de Dados** | **PostgreSQL 16** | Gerenciado via `docker-compose.yml` (`localhost:5432`, base `petshop_db`, usuário `petshop_user`, senha `petshop_pass`).
**Testes E2E & Componente** | **Cypress** | Suíte de testes ponta a ponta simulando jornadas completas no navegador e validando a comunicação real com a API.
**Qualidade Estática** | **SonarCloud** | Análise estática contínua de código, cobertura, duplicações, bugs e vulnerabilidades.
**AI Gatekeeper Reviewer** | **LangGraph + Google Gemini** | Avaliador inteligente executado no CI/CD e localmente, validando diffs, conformidade com ADRs e aderência aos padrões de projeto.

---

## 5. Diretrizes Mandatórias de Teste

Conforme estabelecido nas Decisões Arquiteturais:
* **[ADR 0001](docs/adr/0001-estrategia-de-testes-integracao-e-e2e.md)**: Adotamos **exclusivamente Testes de Integração e Testes End-to-End (E2E)**. Não implementamos testes unitários isolados com mocks artificiais de banco de dados ou serviços internos.
* **[ADR 0002](docs/adr/0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md)**: Toda funcionalidade possui cobertura mandatória de:
  1. **Casos Bons (Happy Path)**: Valida fluxos corretos, HTTP 200/201, persistência de dados e respostas íntegras.
  2. **Casos Ruins (Sad Path / Regras de Negócio)**: Violações de regras, HTTP 401/403/404/409/422, garantindo rollback de transação e zero escritas espúrias no banco.
  3. **Casos Incompletos (Payloads Parciais / Limites de Borda)**: Campos ausentes, strings vazias, valores nulos, payloads malformados, garantindo HTTP 400 Bad Request com matriz de erros estruturada (RFC 7807) e zero erros 500.

---

## 6. Política Oficial de Branches e Merge Requests

* **Hierarquia de Branches**:
  * `main`: Produção estável. Protegida contra push direto.
  * `dev`: Integração contínua da equipe. Protegida contra push direto.
  * `member/<slug>`: Branch pessoal de cada integrante (`member/joao-calsavara`, `member/felipe-moreira`, `member/gabriel-santos`, `member/julyo-silva`, `member/lorenzo-pugina`, `member/samuel-souza`, `member/samuel-martins`).
* **Fluxo Obrigatório**:
  * Desenvolvimento individual sempre em `member/<slug>`.
  * Integração para `dev` realizada **exclusivamente via Merge Request**.
  * **Regra de 2 Aprovações**: Nenhum MR em `dev` ou `main` pode ser mesclado sem no mínimo **2 aprovações humanas** de outros integrantes do time.
  * Promoção de release: MR de `dev` para `main` com 2 aprovações requeridas.

---

## 7. Pipeline Padrão de CI/CD (GitHub Actions + GitLab Bridge)

* Disparado a cada `push` nas branches `main`, `dev` e `member/*` via dual push no remote `origin`.
* Executa build do Spring Boot e React, testes de integração, verificação de qualidade com SonarCloud e revisão semântica com o AI Gatekeeper Reviewer.
* O script `gitlab_reporter.py` publica o veredito técnico diretamente na timeline do Merge Request e atualiza o commit status no GitLab Unicamp.
