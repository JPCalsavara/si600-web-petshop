# RFC: US-06 - Input de Quantidade das Taxas Variáveis

| **Status** | Implementado |
|---|---|
| **Data** | 06/10/2026 |
| **Versão** | 1.0 |

---

## Contextualização

### Entendendo o problema
Certas taxas do evento não são fixas, mas variáveis baseadas na quantidade solicitada (exemplo: taxa de limpeza diária multiplicada por dias, ou aluguel de cadeiras multiplicada por unidades). A US-05 listava as taxas, mas faltava o controle para o usuário informar *quantas* unidades de cada taxa variável seriam aplicadas na proposta.

### Explicando a solução de forma macro
Na interface do cliente, ao selecionar uma taxa do tipo `VARIAVEL`, o sistema exibe um campo numérico (input de quantidade) para permitir que o usuário indique a proporção. O Frontend e o Backend foram ajustados para computar essa quantidade no cálculo total da cotação.

---

## Implementação

### Mudanças no Frontend
- O componente de visualização de taxas foi atualizado para verificar a propriedade `tipo` da taxa.
- Se `tipo === 'VARIAVEL'`, exibe um `<input type="number" min="1" />`.

### Mudanças no Backend
- A lógica de cálculo do preço do estande agora aceita um mapa/array de `taxaId` e `quantidade` para efetuar a multiplicação matemática correta.
- Se uma taxa do tipo fixa for enviada com quantidade diferente de 1 (ou se quantidade for enviada sem necessidade), o backend padroniza ou rejeita `400 Bad Request`.

### Casos Incompletos (Boundary)
1. **Quantidade menor ou igual a 0**: Endpoint de submissão do cálculo retorna `400 Bad Request` informando que taxas variáveis requerem valor `>= 1`.
