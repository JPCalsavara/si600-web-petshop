# RFC: US-04 - Envio e Análise do PDF do Estande

| **Status** | Implementado |
|---|---|
| **Data** | 06/10/2026 |
| **Versão** | 1.0 |

---

## Contextualização

### Entendendo o problema
Toda proposta de locação de estande requer a submissão de um documento em PDF (planta, proposta ou termo). O sistema precisa receber esse arquivo com segurança, armazená-lo corretamente e validar seu conteúdo estruturalmente antes de prosseguir com a cotação.

### Explicando a solução de forma macro
Implementação de um endpoint `multipart/form-data` que recebe o arquivo PDF, valida extensão e tamanho, salva temporariamente ou em storage (S3/Local) e realiza as validações de conteúdo aplicáveis para aprovar ou rejeitar a submissão.

---

## Implementação

### Rotas Propostas

| Método | Caminho | O que faz | Entrada | Saídas |
|---|---|---|---|---|
| `POST` | `/api/v1/estandes/pdf` | Recebe o PDF da proposta | Arquivo `multipart/form-data` | `200 OK` (aprovado); `400 Bad Request` (rejeitado) |

---

### Fluxos Detalhados Textuais (Cobertura Tripartite ADR 0002)

#### 1. Casos Bons (Happy Path)
1. Envio de um arquivo PDF válido com tamanho permitido.
2. Sistema salva e analisa o documento.
3. Retorna HTTP `200 OK` com metadados da aprovação.

#### 2. Casos Ruins (Sad Path / Regras de Negócio)
1. **Extensão Inválida**: Envio de um arquivo `.docx` ou executável, sistema rejeita de imediato com `422 Unprocessable Entity` ou `400`.
2. **Documento sem conteúdo exigido**: A análise falha por violação de regra de negócio (PDF não atende aos critérios), retorna rejeição detalhada sem persistir proposta final.

#### 3. Casos Incompletos (Boundary & Malformed Payloads)
1. **Sem Arquivo**: Requisição sem a parte multiforme do arquivo retorna `400 Bad Request`.
2. **Arquivo maior que o limite**: O Spring bloqueia automaticamente antes de carregar na memória, retornando Payload Too Large.
