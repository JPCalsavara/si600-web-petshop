---
name: ui-builder
description: Skill de desenvolvimento de Frontend. Constrói páginas e componentes React baseados na RFC e na API do backend existente, utilizando SCSS Modules e Axios.
---

# UI Builder Skill

Esta skill é especializada na construção de telas e componentes visuais prontos para produção no ecossistema React do repositório. Ela assume a responsabilidade pelo "Frontend Flow" da orquestração.

## Quando usar

Acione esta skill quando você (Agente) precisar desenvolver a interface de usuário (UI) para uma nova funcionalidade, após o backend e os testes já terem sido implementados ou documentados.

## Regras de Construção (Guidelines)

### 1. Leitura de Escopo
- Revise a RFC da branch atual (em `docs/rfc/`) para extrair os requisitos visuais e as jornadas de usuário.
- Inspecione as classes Controller do backend ou DTOs gerados para entender o formato exato do payload JSON que o componente React irá consumir.

### 2. Gerenciamento de Estado e API
- Mantenha a simplicidade: utilize o estado local do React (`useState`, `useEffect`) para o controle de tela (loading, errors, data).
- Para comunicação HTTP, **sempre** importe a instância configurada do Axios de `src/services/api.ts`. Não utilize `fetch` nativo.
- Trate os erros globalmente lidando com os retornos padrão RFC 7807 (`ProblemDetail`) do backend.

### 3. Estilização (CSS Modules)
- Todo componente visual (`Component.tsx`) deve vir acompanhado obrigatoriamente de seu respectivo arquivo de estilo (`Component.module.scss`).
- Não utilize CSS Global ou Styled Components para a estrutura isolada da tela.
- Dentro do `.module.scss`, prefira a convenção BEM (Block, Element, Modifier) adaptada para o uso com CSS Modules, garantindo isolamento total do estilo.

### 4. Estrutura de Arquivos
- Posicione as lógicas de roteamento e telas principais em `src/pages/`.
- Posicione elementos reutilizáveis (botões, modais, cards) em `src/components/`.
- Garanta que todos os componentes são tipados rigorosamente com TypeScript (`interface Props`).

### Exemplo de Saída Esperada
O agente que executar o `ui-builder` deve entregar a pasta da feature similar a:
```text
src/pages/ClientPdfPage/
├── ClientPdfPage.tsx
├── ClientPdfPage.module.scss
├── ProjectFees.tsx
└── ProjectFees.module.scss
```
