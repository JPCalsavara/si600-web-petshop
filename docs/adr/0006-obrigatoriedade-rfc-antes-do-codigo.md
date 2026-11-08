# ADR 0006: Documentação Arquitetural Consolidada em RFCs Gerais e Refinamento nos Cards

## Data
2026-11-08 (Atualizado)

## Status
Aceito (Accepted)

## Contexto
Inicialmente, o projeto previa a criação de um arquivo de RFC individual (`docs/rfc/US-XX-*.md`) para cada tarefa ou User Story antes do desenvolvimento. Na prática, a fragmentação em dezenas de arquivos por tarefa gerou duplicação, desatualização rápida entre o código evoluído e as RFCs antigas, e ausência de uma visão arquitetural consolidada do Backend, do Frontend e do Modelo Entidade-Relacionamento (MER) global.

Ao mesmo tempo, os refinamentos funcionais e critérios de aceite específicos de cada entrega já ficam registrados diretamente nos cards/issues do projeto, tornando redundante a criação de um arquivo `.md` no repositório para cada card.

## Decisão
Adotamos o modelo de **RFCs Gerais Consolidadas do Projeto**, eliminando a criação de RFCs individuais por tarefa/card:

1. **Três RFCs Gerais Autoritativas (`docs/rfc/`)**:
   - [`docs/rfc/rfc-backend.md`](../rfc/rfc-backend.md): Especificação técnica completa do Backend (arquitetura em camadas, seams `ObjectStorage` e `EmailSender`, agendador de prazos, catálogo completo de endpoints REST `/api/...`, regras de negócio e o **diagrama MER final em Mermaid**).
   - [`docs/rfc/rfc-frontend.md`](../rfc/rfc-frontend.md): Especificação técnica completa do Frontend (arquitetura de Deep Domain Modules, justificativa da rejeição de Atomic Design, seam unificado `ActorSession` + HTTP client, páginas, validação e estratégia de testes E2E).
   - [`docs/rfc/rfc-po-visao-cliente.md`](../rfc/rfc-po-visao-cliente.md): Documento executivo voltado ao Product Owner (PO) e ao Cliente, resumindo de forma acessível e menos técnica o funcionamento integrado do Backend, Frontend e o MER do sistema.
2. **Refinamento Direto nos Cards**: Para tarefas e User Stories individuais, o refinamento nos próprios cards/issues já basta como especificação da tarefa. As RFCs gerais são atualizadas apenas quando houver mudança estrutural na arquitetura, nos contratos gerais ou no MER.

## Consequências
- **Positivas**:
  - **Locality documental**: Desenvolvedores, PO, clientes e o AI Gatekeeper consultam três documentos canônicos sempre atualizados em vez de dezenas de arquivos fragmentados e conflitantes.
  - Eliminação de burocracia por tarefa, mantendo o foco no refinamento do card e nos testes de integração/E2E ([ADR 0001](0001-estrategia-de-testes-integracao-e-e2e.md) e [ADR 0002](0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md)).
  - Comunicação clara com o cliente e PO por meio da RFC executiva dedicada.
