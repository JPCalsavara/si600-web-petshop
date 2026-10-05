# SI600 Web Eventos — Context & Ubiquitous Language

## 1. Visão Geral do Sistema

O **SI600 Sistema de Eventos** é um sistema web integrado para gerenciamento operacional e comercial focado no aluguel de estandes em eventos. A plataforma atende dois públicos principais:
1. **Clientes (Expositores)**: Submetem projetos arquitetônicos de seus estandes, gerenciam configurações do projeto e realizam pagamentos de taxas e aluguéis referentes aos estandes aprovados.
2. **Administradores**: Gerenciam a aprovação e análise das plantas (inserindo também as metragens oficiais), controlam o catálogo de taxas do evento e supervisionam os status de faturamento de cada projeto.

---

## 2. Linguagem Ubíqua (Domain Vocabulary)

Termo | Definição | Sinônimos Evitados
:--- | :--- | :---
**`Cliente`** | Pessoa física ou jurídica (expositor) que contrata o aluguel de um estande no evento. | *Usuário comum*, *Consumidor*
**`Projeto`** | Agrupamento lógico das informações do cliente para a montagem de seu estande, incluindo a planta e as taxas associadas. | *Reserva*, *Inscrição*
**`Planta`** | O projeto arquitetônico do estande enviado pelo Cliente (comumente em formato PDF). Contém atributos como a **área em m²** (obrigatoriamente preenchida pelo Administrador após conferência) e o status atual. | *PDF do estande*, *Documento*
**`StatusPlanta`** | Enumeração que dita o ciclo de vida da Planta e, consequentemente, a exibição das taxas: `AGUARDANDO`, `EM_ANALISE`, `APROVADA`, `REPROVADA`. | *Status do Projeto*, *Estado do PDF*
**`Taxa`** | Valores cobrados do cliente relacionados ao projeto. Podem ser Fixas, Por Metragem (baseado na área m² preenchida pelo Admin) ou Variáveis (com entrada de quantidade salva pelo próprio Cliente via botão). | *Cobrança*, *Custo*
**`Pagamento`** | Geração da guia financeira (Boleto/PIX) a partir das taxas calculadas de uma Planta Aprovada. Se pagamentos já foram gerados, a Planta não pode voltar para status Reprovada. | *Fatura*, *Transação*

---

## 3. Invariantes e Regras de Negócio Fundamentais

1. **Visualização de Taxas**: Um cliente só pode visualizar e pagar as taxas referentes a um projeto se o status da `Planta` for igual a `APROVADA`.
2. **Imutabilidade Condicional da Planta**: Uma `Planta` não pode ser marcada como `REPROVADA` se o projeto já tiver qualquer registro de `Pagamento` gerado e atrelado a ele. O administrador ficará bloqueado de fazer essa transição (HTTP 409 Conflict).
3. **Pertença do Projeto**: Um cliente autenticado só tem acesso aos seus próprios `Projeto`s e respectivas plantas e taxas, garantindo o isolamento.
4. **Dependência de Metragem**: As taxas do tipo "Por Metragem" só podem ser calculadas se o atributo de área da Planta (m²) estiver preenchido de forma estrita (> 0) pelo Administrador no ato da aprovação.
5. **Ação Explícita de Taxas Variáveis**: As taxas do tipo "Variável" dependem do preenchimento da "quantidade" por parte do Cliente. Esse valor deve ser persistido via acionamento explícito de um botão "Salvar", desencadeando uma única transação no backend antes de se permitir a emissão da cobrança.

---

## 4. Stack Tecnológica Oficial & Padrões de Desenvolvimento

Camada | Tecnologia | Detalhes & Padrões
:--- | :--- | :---
**Backend** | **Java 21 / Spring Boot** | REST API sob `/api/...`, Spring Data JPA, Hibernate, Bean Validation (`@Valid`), Problem Details (RFC 7807 via `@RestControllerAdvice`). Arquitetura em camadas desacopladas (`controller`, `service`, `repository`, `entity`, `dto`, `exception`).
**Frontend** | **React 18+ (Vite) + TS** | SPA moderna construída com Vite, componentes funcionais modulares, tipagem TypeScript estrita e cliente HTTP centralizado consumindo o backend.
**Banco de Dados** | **PostgreSQL 16** | Gerenciado via `docker-compose.yml` (`localhost:5432`, base `eventos_db`, usuário `eventos_user`, senha `eventos_pass`).
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
  * `member/<slug>`: Branch pessoal de cada integrante (`member/joao-calsavara`, `member/felipe-moreira`, `member/gabriela-januario`, `member/gabriel-santos`, `member/julio-hidalgo`, `member/lorenzo-pugina`, `member/samuel-calegnan`, `member/samuel-lima`).
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
