# RFC: US-05 - Visualização de Taxas e Migração Axios

| **Status** | Implementado |
|---|---|
| **Data** | 06/10/2026 |
| **Versão** | 1.0 |

---

## Contextualização

### Entendendo o problema
O backend já possuía o catálogo de taxas (US-02), mas o cliente precisava visualizar essas taxas na interface para entender os custos aplicáveis. Além disso, as chamadas de rede no Frontend React estavam sendo feitas nativamente com `fetch`, o que gerava repetição de código para controle de erros e headers.

### Explicando a solução de forma macro
1. Refatorar a camada de rede do Frontend para utilizar o `Axios`, centralizando interceptadores e tratamento do RFC 7807 (ProblemDetail).
2. Construir os componentes React de listagem de taxas conectando ao endpoint `/api/v1/fees`.
3. Ajuste de ambiente: refatorar o docker-compose para usar multi-stage builds. (Posteriormente revisado para focar no Vite Dev Server via docker).

---

## Implementação

- Criação de uma API service com Axios (`axios.create`).
- Criação do componente `FeeList.tsx` (ou similar) que invoca o serviço `getFees()` ao montar.
- Exibição de loading states e fallback em caso de erros `500` ou rede indisponível.

### Principal Desafio
Tratar unificadamente os erros de validação da API Spring Boot (`ProblemDetail`) e renderizá-los de forma amigável no UI do React através da configuração global do interceptador do Axios.
