# SI600 Web Eventos — Context & Ubiquitous Language

## 1. Visão Geral do Sistema

O **SI600 Sistema de Eventos** é um sistema web integrado para gerenciamento operacional e comercial focado no aluguel e montagem de estandes em eventos. A plataforma atende dois públicos principais:
1. **Clientes (`CLIENT` / Expositores)**: Submetem projetos arquitetônicos de seus estandes em PDF (`Planta`), informam quantidades para taxas variáveis, acompanham o histórico de auditoria e consultam as taxas calculadas referentes aos estandes aprovados.
2. **Administradores (`ADMIN`)**: Gerenciam o catálogo global de taxas do evento, cadastram projetos de expositores, configuram as taxas de cada projeto, analisam e aprovam/reprovam as plantas em PDF (confirmando a metragem oficial `boothAreaM2`), realizam a cotação financeira de taxas variáveis, monitoram gargalos no Dashboard Kanban e exportam relatórios consolidados em CSV.

---

## 2. Linguagem Ubíqua (Domain Vocabulary)

Termo | Definição | Sinônimos Evitados
:--- | :--- | :---
**`Cliente`** | Pessoa física ou jurídica (expositor) proprietária de um `Projeto` (`ownerId`) que contrata o aluguel de um estande no evento. | *Usuário comum*, *Consumidor*, *Tutor*
**`Administrador`** | Integrante da organização do evento com permissão para gerir catálogo de taxas, aprovar/reprovar plantas, cotar taxas variáveis e auditar projetos. | *Operador*, *Atendente*
**`Projeto` (`Project`)** | Agregado central das informações do estande do expositor, incluindo dados cadastrais, prazos (`pdfDeadline`, `paymentDeadline`), metadados da planta PDF, metragem (`boothAreaM2`), `ProjectStatus` e taxas vinculadas. | *Reserva*, *Inscrição*, *Agendamento*
**`Planta`** | O projeto arquitetônico do estande enviado pelo Cliente em formato PDF (armazenado no Backblaze B2 sob `pdfObjectKey`), acompanhado da área em $\text{m}^2$ (`boothAreaM2`). | *Documento genérico*, *Anexo*
**`StatusProjeto` (`ProjectStatus`)** | Enumeração que governa o ciclo de vida do Projeto e da Planta: `AGUARDANDO_PDF`, `PDF_EM_ANALISE`, `REPROVADO`, `APROVADO`, `AGUARDANDO_PAGAMENTO`, `CONCLUIDO`. | *Estado do PDF*
**`Taxa` (`Fee` / `ProjectFee`)** | Cobrança do catálogo (`Fee`) ou vinculada a um projeto (`ProjectFee`). Pode ser `FIXA`, `POR_METRAGEM` (`VALOR_POR_M2` ou `UNIDADES_POR_INTERVALO`) ou `VARIAVEL` (com entrada de quantidade pelo Cliente e cotação pelo Administrador). | *Custo*, *Serviço avulso*
**`StatusTaxaProjeto` (`ProjectFeeStatus`)** | Ciclo de vida de uma taxa vinculada ao projeto: `DISPONIVEL`, `AGUARDANDO_QUANTIDADE`, `AGUARDANDO_COTACAO`, `COTADA`, `PAGAMENTO_GERADO`. | *Status da fatura*
**`TagAlerta` (`AlertTag`)** | Etiqueta operacional calculada dinamicamente a partir dos prazos e status do projeto: `ATRASADO`, `PRAZO_PROXIMO`, `PENDENTE`, `EM_DIA`, `CONCLUIDO`. | *Badge estático*
**`EventoHistorico` (`ProjectHistoryEvent`)** | Registro imutável na trilha de auditoria do projeto (`PROJETO_CRIADO`, `PDF_ENVIADO`, `PDF_APROVADO`, `PDF_REPROVADO`, `TAXAS_CONFIGURADAS`, `QUANTIDADE_INFORMADA`, `TAXA_COTADA`). | *Log de sistema*
**`NotificacaoPrazo` (`DeadlineNotification`)** | Registro idempotente de envio de alerta de vencimento por e-mail nos marcos `T-7`, `T-3`, `T-1`, `T0` e `T+1` para `PDF_SUBMISSION` ou `PAYMENT`. | *Disparo avulso*

---

## 3. Invariantes e Regras de Negócio Fundamentais

1. **Visualização de Taxas Condicionada à Aprovação**: Um `Cliente` só pode visualizar os valores calculados das taxas referentes a um `Projeto` quando o status do projeto estiver em `APROVADO`, `AGUARDANDO_PAGAMENTO` ou `CONCLUIDO`. Antes disso, a API retorna `HTTP 409 Conflict`.
2. **Imutabilidade Condicional Após Geração de Pagamento**: Um `Projeto` não pode ser marcado como `REPROVADO` e uma `ProjectFee` não pode ser removida ou recotada se já houver registro com status `PAGAMENTO_GERADO` (`HTTP 409 Conflict`).
3. **Isolamento de Propriedade (`ownerId`)**: Um `Cliente` autenticado tem acesso exclusivo aos seus próprios `Projeto`s (`project.ownerId == actor.id`). Na linha do tempo (`ProjectHistoryEvent`), o `actorId` é mascarado para o `Cliente`, exibindo apenas o papel (`ADMIN` ou `CLIENT`).
4. **Conferência de Metragem ($\text{m}^2$)**: O `Cliente` informa a área pretendida (`areaM2 > 0`) no envio do PDF e o `Administrador` confirma a metragem oficial (`approvedAreaM2 > 0`) no ato da aprovação, base sobre a qual as taxas `POR_METRAGEM` são calculadas.
5. **Fluxo de Taxas Variáveis e Invalidamento de Cotação**: Nas taxas do tipo `VARIAVEL`, o `Cliente` informa e salva explicitamente a `quantity > 0`. Qualquer alteração de quantidade descarta eventual cotação anterior (`quotedAmount = null`) e retorna a taxa para `AGUARDANDO_COTACAO` até que o `Administrador` registre um novo valor (`COTADA`).

---

## 4. Documentação Arquitetural e RFCs Gerais

* **[RFC Geral do Backend e MER](docs/rfc/rfc-backend.md)**: Arquitetura do backend, seams, catálogo de rotas REST e diagrama MER final em Mermaid.
* **[RFC Geral do Frontend](docs/rfc/rfc-frontend.md)**: Arquitetura de módulos profundos de domínio (rejeição de Atomic Design), seam de sessão/HTTP e suíte E2E.
* **[RFC Executiva (PO & Cliente)](docs/rfc/rfc-po-visao-cliente.md)**: Resumo acessível de produto (Backend + Frontend + MER) voltado ao PO e ao cliente.
* **[Catálogo de ADRs](docs/adr/README.md)**: Decisões arquiteturais vigentes ([ADR 0001](docs/adr/0001-estrategia-de-testes-integracao-e-e2e.md), [ADR 0002](docs/adr/0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md), [ADR 0004](docs/adr/0004_armazenamento_b2_para_pdfs_de_estande.md), [ADR 0005](docs/adr/0005-padronizacao-de-codigo-com-eslint-e-prettier.md), [ADR 0006](docs/adr/0006-obrigatoriedade-rfc-antes-do-codigo.md)).
