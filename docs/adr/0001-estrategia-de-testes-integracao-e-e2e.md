# ADR 0001: Estratégia de Testes Automatizados — Adoção Exclusiva de Testes de Integração e E2E

## Status
Aceito (Accepted)

## Data
2026-09-23

## Contexto

No desenvolvimento do **SI600 Web Petshop**, a confiabilidade da aplicação é crítica. O sistema lida com operações transacionais fundamentais, incluindo alocação de horários, regras de concorrência de agenda, integridade relacional entre tutores (`Cliente`), animais (`Pet`), `Servico`s e atendimentos, além de permissões de acesso e validações de dados.

Historicamente, a adoção dogmática da pirâmide de testes tradicional foca a maior parte dos esforços em testes unitários isolados com uso massivo de *mocks* e *stubs* (mockando banco de dados, ORM, repositórios e middlewares). No ecossistema de aplicações web orientadas a dados e APIs, essa abordagem apresenta severas patologias:
1. **Falsos Positivos e Falsa Sensação de Segurança**: Suítes com 100% de cobertura unitária passam com sucesso, mas a aplicação falha miseravelmente em produção devido a *constraints* relacionais do banco (chaves estrangeiras, violação de unicidade), erros de serialização JSON, divergências de tipos em tempo de execução, falhas em migrations e comportamento de middlewares.
2. **Acoplamento a Detalhes de Implementação**: Testes unitários com mocks monitoram chamadas a métodos privados ou nomes de variáveis internas. Qualquer refatoração de código que mantenha o comportamento externo idêntico quebra centenas de testes unitários, desestimulando a evolução saudável da arquitetura.
3. **Testes Tautológicos**: O teste pré-programa o mock para retornar um valor arbitrário e em seguida valida se o método retornou o mesmo valor arbitrário, testando apenas a configuração do mock e não o comportamento real do software.

Diante disso, a equipe de engenharia do SI600 Web Petshop precisa de uma estratégia de testes com **alto retorno sobre investimento (ROI)**, que valide comportamentos reais observáveis nas costuras públicas (*public seams*), garantindo a robustez do software com baixo custo de manutenção perante refatorações.

---

## Decisão

Decidimos adotar uma estratégia de testes baseada no **Troféu de Testes**, focando **exclusivamente em Testes de Integração e Testes End-to-End (E2E)**, **excluindo a implementação de testes unitários isolados baseados em mocks artificiais de dependências internas**.

### 1. Testes de Integração (Camada Principal)
- **Escopo**: Validam os fluxos da aplicação a partir de suas costuras públicas (endpoints HTTP da API REST/GraphQL, middlewares, autenticação, validação de schemas e regras de negócio de domínio integradas ao banco de dados).
- **Ambiente Real de Persistência**: Os testes executam contra uma instância real de banco de dados (ex.: PostgreSQL / MySQL provisionado via Docker Compose ou ambiente de banco efêmero em memória para testes). Não é permitido mockar o banco de dados nem as camadas de repositório/ORM.
- **Isolamento de Estado**: Cada suíte ou teste garante o isolamento do banco através de transações com rollback automático, truncamento controlado de tabelas ou banco de dados isolado por worker de teste.
- **Uso Estrito de Mocks**: Mocks são expressamente proibidos para código da própria aplicação. Mocks ou *fakes* são restritos exclusivamente a serviços externos de terceiros fora do controle do repositório (ex.: webhooks de pagamento de bancos externos, APIs de envio de SMS/WhatsApp).

### 2. Testes End-to-End (E2E)
- **Escopo**: Validam a jornada do usuário de ponta a ponta, simulando a interação real entre a interface web (frontend), o backend (API) e o banco de dados.
- **Fluxos Críticos**:
  - Jornada do Cliente: Cadastro de tutor -> Registro de pets -> Seleção de serviços -> Escolha de horários disponíveis -> Confirmação de agendamento -> Consulta do status do atendimento.
  - Jornada Administrativa: Login do atendente/veterinário -> Gestão da agenda -> Recepção do pet (Check-in) -> Atualização de status da Ordem de Serviço -> Finalização do atendimento.
- **Ferramental Recomendado**: Playwright ou Cypress para automação de browser, garantindo testes headless rápidos e integrados à pipeline.

### 3. Exclusão Explícita de Testes Unitários Isolados
- É vedada a criação de arquivos de teste unitário focados em classes ou funções internas isoladas por mocks (`jest.mock(...)`, `unittest.mock`, `sinon.stub`).
- A lógica de domínio e utilitários de negócio são testados de forma direta por meio dos testes de integração que acionam os casos de uso do sistema.

---

## Consequências

### Positivas
- **Fidelidade Máxima à Produção**: As suítes de teste validam queries SQL reais, constraints de banco, transações, status codes HTTP reais e serializações exatas.
- **Liberdade Total para Refatoração**: Estruturas internas, nomes de classes, divisão de pastas e camadas de serviço podem ser refatoradas livremente; desde que o contrato HTTP e os comportamentos externos sejam mantidos, os testes continuam verdes.
- **Redução Drástica do Custo de Manutenção**: Elimina-se a carga de manter dezenas de stubs e mocks frágeis que precisavam ser atualizados a cada mudança de assinatura interna.
- **Alinhamento com o AI Gatekeeper**: O script de automação (`ai-gatekeeper-reviewer`) e as ferramentas de CI executam a suíte real de testes (`npm test` ou `pytest`), gerando logs fidedignos (`tests.log`) para validação de qualidade sem falsos positivos.

### Negativas e Mitigações
- **Tempo de Execução**: Testes de integração com banco de dados real demandam mais tempo que testes unitários puros em memória.
  - *Mitigação*: Uso de banco de dados em RAM/tmpfs durante os testes, paralelização de suítes de teste e scripts otimizados de seed e teardown.
- **Dependência de Infraestrutura**: Exige banco de dados configurado para rodar a suíte localmente ou no CI/CD.
  - *Mitigação*: Disponibilização de arquivo `docker-compose.yml` que sobe o banco de dados de testes com um único comando (`docker compose up -d db_test`).
