# TEC-06 — Correção de bugs e integração Frontend/Backend

## Correções realizadas

- Integração da criação de projetos do React com `POST /projects`.
- Integração da listagem administrativa com `GET /projects/admin`.
- Correção da aprovação de PDF: o frontend agora envia o campo obrigatório `approvedAreaM2` esperado por `ApproveProjectRequest`.
- A tela administrativa passou a solicitar a metragem aprovada antes da aprovação.
- Tratamento de falhas de comunicação do Axios passou a gerar uma mensagem amigável para o usuário, em vez de expor somente o erro técnico de rede.
- CORS do Spring Boot passou a ser configurável por `CORS_ALLOWED_ORIGINS`, mantendo localhost/127.0.0.1:5173 como padrão.
- Tipos do frontend foram alinhados aos campos adicionais existentes em `ProjectResponse`, incluindo `address`, `contactEmail` e `paymentDeadline`.
- A listagem administrativa passou a usar datas sem conversão de fuso para evitar deslocamento de um dia.

## Payloads enviados a mais pelo Frontend

Conforme solicitado, estes campos foram **somente listados** e não removidos:

1. `POST /projects` — `feeIds`
   - O contrato atual de `CreateProjectRequest` do backend não possui esse campo.
   - O frontend continua enviando esse campo.
   - O backend atual não o utiliza no cadastro.

2. `POST /projects/{projectId}/pdf` — parte multipart `areaM2`
   - O frontend continua enviando `areaM2`.
   - O contrato atual do endpoint Spring recebe apenas a parte multipart `file`.
   - O backend atual não utiliza `areaM2` nesse endpoint.

3. Headers `X-Authenticated-User-Id` e `X-Authenticated-User-Role`
   - Não são considerados payload de negócio.
   - São necessários pelo mecanismo de autenticação/identidade atualmente implementado no backend.

## Campos que estavam faltando e foram corrigidos

- `POST /projects/admin/{projectId}/approve`
  - Backend espera:
    ```json
    {
      "approvedAreaM2": 42.5
    }
    ```
  - O frontend anteriormente enviava corpo vazio.
  - Agora envia `approvedAreaM2`.

## Observação sobre autenticação

O backend deste projeto não implementa Bearer/JWT. A identidade atual é obtida pelos headers `X-Authenticated-User-Id` e `X-Authenticated-User-Role`. Portanto, não foi inventado um fluxo Bearer que o backend não suporta.

## Validação

A validação automática completa não pôde ser concluída neste ambiente:

- `node_modules` não estava disponível no ZIP.
- A tentativa de instalar dependências do frontend não terminou por indisponibilidade/timeout do acesso ao registry.
- O Maven Wrapper presente no ZIP está com CRLF e sem permissão executável; a tentativa de normalizá-lo também não conseguiu baixar o Maven por falta de acesso ao Maven Central.

As alterações foram revisadas estaticamente após a implementação. Nenhum resultado de teste automático é declarado como aprovado sem execução.

## Testes de regressão adicionados

Para evitar regressões nos fluxos corrigidos, foram adicionados testes sem duplicar a cobertura de integração já existente:

- `frontend/src/services/api.test.ts`: contrato do cliente HTTP, headers de identidade, payload de criação, multipart do PDF, decisões administrativas, listagens/consultas e tratamento de erros.
- `backend/src/test/java/br/unicamp/ft/si600/eventos/config/CorsIntegrationTest.java`: preflight CORS para a origem do React e os headers usados pela integração.
- Os testes de integração existentes de projetos/PDF já cobrem persistência, autenticação, upload, aprovação, reprovação, reenvio e respostas HTTP; por isso não foram duplicados.

### Campos de negócio incorporados ao contrato

Os três campos anteriormente tratados como extras agora fazem parte efetiva da integração:

- `feeIds` em `POST /projects`: aceito como lista de UUIDs, validado contra o catálogo de taxas ativas e persistido como vínculos `ProjectFee`. IDs inexistentes/inativos e duplicidades são rejeitados.
- `areaM2` em `POST /projects/{projectId}/pdf`: obrigatório no multipart junto de `file`, validado e persistido em `Project.boothAreaM2`, ficando disponível para as regras posteriores de cálculo de taxas.
- `X-Authenticated-User-Id` e `X-Authenticated-User-Role`: continuam sendo exigidos pelo `ActorFilter` em rotas protegidas e são enviados automaticamente pelo interceptor Axios do frontend.

Assim, esses dados não são mais considerados payload excedente: são parte do contrato e da regra de negócio.
- `frontend/cypress/e2e/project-flow.cy.ts`: único E2E do fluxo de cadastro que atravessa UI → API → persistência → listagem; os demais cenários de PDF continuam cobertos por `pdf-flow.cy.ts`.


## Dev x Prod — validação TEC-06

A inspeção do pacote mostrou que anteriormente o Compose executava o backend com `SPRING_PROFILES_ACTIVE=dev` e o frontend sempre iniciava o Vite em modo desenvolvimento. Não havia `application-prod.yml`.

Foi adicionada uma separação explícita:
- `backend/src/main/resources/application-dev.yml`: armazenamento B2 desativado.
- `backend/src/main/resources/application-prod.yml`: `ddl-auto=validate`, CORS obrigatório via `CORS_ALLOWED_ORIGINS` e B2 habilitado por padrão.
- `frontend/.env.production.example`: API configurada para `/api`, assumindo reverse proxy em produção.
- Cypress aceita `CYPRESS_BASE_URL` e `CYPRESS_API_BASE_URL`, permitindo executar a mesma suíte E2E contra dev ou prod, sem duplicar casos.

Testes de perfil:
- `CorsIntegrationTest` executa explicitamente com profile `dev`.
- `ProductionProfileIntegrationTest` verifica o profile `prod` e seu preflight CORS.

Exemplos de execução dos mesmos E2E:
```bash
# dev
CYPRESS_BASE_URL=http://localhost:5173 CYPRESS_API_BASE_URL=http://localhost:8080/api npm run cypress:run

# prod (após subir frontend buildado e backend prod)
CYPRESS_BASE_URL=http://localhost:4173 CYPRESS_API_BASE_URL=http://localhost:8080/api npm run cypress:run
```

### Execução local do mesmo E2E em produção-like

O build de produção do Vite recebe `VITE_API_URL` em tempo de build. Para uma validação local sem reverse proxy:

```bash
# Backend
cd backend
SPRING_PROFILES_ACTIVE=prod CORS_ALLOWED_ORIGINS=http://localhost:4173 B2_ENABLED=false ./mvnw spring-boot:run

# Frontend, em outro terminal
cd frontend
VITE_API_URL=http://localhost:8080/api npm run build
npm run preview -- --host 0.0.0.0 --port 4173

# E2E — exatamente os mesmos specs usados em dev
CYPRESS_BASE_URL=http://localhost:4173 \
CYPRESS_API_BASE_URL=http://localhost:8080/api \
npm run cypress:run
```

Em produção real, `B2_ENABLED` deve permanecer habilitado e `CORS_ALLOWED_ORIGINS` deve conter somente as origens reais do frontend. Os headers de identidade continuam parte do contrato atual, mas a documentação existente deixa claro que, antes da produção real, eles devem ser injetados por gateway/IdP confiável.


## Validação local posterior

A execução local com Cypress 16.1.1 revelou dois ajustes necessários nos testes:

1. `Cypress.env()` foi removido no Cypress 16. O `API_BASE_URL` público foi migrado para `Cypress.expose('API_BASE_URL')`, configurado em `cypress.config.ts`. A documentação oficial do Cypress confirma essa migração para valores públicos.
2. O teste de catálogo de taxas dependia implicitamente de `VITE_USER_ROLE=ADMIN`. Isso tornava o teste frágil e diferente entre ambientes. O teste agora configura `MOCK_ROLE=ADMIN` e `MOCK_ACTOR_ID=admin-1` no próprio `cy.visit`, tornando o cenário determinístico.
3. O cenário de taxas do cliente agora envia o `feeIds` da taxa criada ao criar o projeto, preservando a regra de negócio introduzida na TEC-06.

O build de produção do frontend foi confirmado pelo ambiente local do desenvolvedor. A suíte Cypress ainda precisa ser executada novamente após estes ajustes; o ambiente de geração do artefato não possui `node_modules`, portanto não foi declarada uma execução que não ocorreu.
