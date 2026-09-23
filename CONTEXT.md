# SI600 Web Petshop — Context & Ubiquitous Language

## Overview

O **SI600 Web Petshop** é um sistema web integrado para gerenciamento operacional e comercial de um pet shop com atendimento clínico e estético. A plataforma atende dois públicos principais:
1. **Clientes (Tutores)**: Navegam pelos serviços oferecidos, cadastram seus animais de estimação, realizam e acompanham agendamentos (banho, tosa, consultas veterinárias) e visualizam histórico.
2. **Administradores e Colaboradores (Atendentes, Tosadores, Veterinários)**: Gerenciam a agenda, controlam o fluxo de atendimento, atualizam status das ordens de serviço e administram o catálogo de serviços e clientes.

---

## Linguagem Ubíqua (Domain Vocabulary)

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

## Invariantes e Regras de Negócio Fundamentais

1. **Unicidade de Agendamento por Profissional/Recurso**: Não é permitido criar ou confirmar dois agendamentos no mesmo intervalo de tempo para o mesmo profissional ou para a mesma baia/mesa de atendimento.
2. **Pertença do Pet**: Um `Agendamento` só pode ser solicitado para um `Pet` cujo tutor corresponda ao `Cliente` autenticado (exceto em operações realizadas por administradores/atendentes).
3. **Imutabilidade e Transições de Estado**:
   - Agendamentos com status `CONCLUIDO` ou `CANCELADO` são finais e não permitem alteração de data, serviço ou valor.
   - Cancelamento só é autorizado se o status for `PENDENTE` ou `CONFIRMADO` e antes do início do horário agendado.
4. **Validação de Entrada Rigorosa**:
   - Payloads incompletos, ausência de campos obrigatórios (`clienteId`, `petId`, `servicoId`, `dataHoraInicio`) ou valores inválidos (datas no passado, preços negativos) devem ser barrados imediatamente com HTTP 400/422.

---

## Diretrizes de Engenharia e Testes

Conforme estabelecido nas Decisões Arquiteturais:
- **[ADR 0001](file:///home/jpcalsavara/projetos/andamento/si600-web-petshop/docs/adr/0001-estrategia-de-testes-integracao-e-e2e.md)**: Adotamos **exclusivamente Testes de Integração e Testes End-to-End (E2E)**. Não implementamos testes unitários isolados com mocks artificiais de banco de dados ou serviços.
- **[ADR 0002](file:///home/jpcalsavara/projetos/andamento/si600-web-petshop/docs/adr/0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md)**: Toda funcionalidade possui cobertura mandatória de **Casos Bons (Happy Path)**, **Casos Ruins (Sad Path / Regras de Negócio)** e **Casos Incompletos (Payloads Parciais / Limites de Borda)**.
- **Respostas de Erro**: A API adota o padrão estruturado RFC 7807 (*Problem Details for HTTP APIs*), garantindo diagnósticos uniformes de falhas para clientes e suítes de teste.

---

## Stack Tecnológica Oficial

Camada | Tecnologia | Detalhes & Práticas
:--- | :--- | :---
**Backend** | **Java 21 / Spring Boot** | REST API, Spring Data JPA, Bean Validation, Problem Details (RFC 7807). Testes de integração na borda HTTP contra banco real/Testcontainers.
**Frontend** | **React + TypeScript (Vite)** | SPA moderna construída com Vite, componentes modulares e comunicação REST tipada com o backend.
**Testes E2E & Componentes** | **Cypress** | Suíte de testes ponta a ponta (E2E) simulando jornadas reais de tutores e atendentes, com suporte a testes de componentes isolados.

