# ADR 0005: Padronização de Código no Frontend com ESLint e Prettier

## Status
Aceito

## Contexto
O projeto exige integração de código de múltiplos membros da equipe, exigindo aprovação por pelo menos 2 pessoas em Merge Requests (MRs). Sem uma ferramenta rígida de formatação, surgem atritos durante revisões de código devido a inconsistências de estilo e espaçamento. Além disso, a falta de análise estática local pode permitir a entrada de anti-patterns e erros simples que passariam despercebidos, apenas sendo detectados tardiamente pelo SonarCloud no pipeline de CI/CD.

## Decisão
Adotamos o **ESLint** aliado ao **Prettier** como ferramentas oficiais de *Code Style* e formatação para a camada Frontend do projeto (React/Vite com TypeScript).

* **ESLint**: Focado em qualidade de código, garantindo a captura de bugs, tipagem correta, violações de escopo e *anti-patterns* (regras de linter).
* **Prettier**: Focado em estilo de código (tabs, aspas simples vs duplas, largura máxima de linha). O Prettier é integrado de forma que suas regras de estilo sobressaiam em relação às de formatação visual do ESLint, evitando conflitos (usualmente via `eslint-config-prettier`).

A conformidade com essas ferramentas passa a ser um requisito no fluxo de desenvolvimento, devendo ser configurada nos IDEs dos membros para formatação automática ao salvar (`Format on Save`).

## Consequências
* **Positivas**:
  * Uniformidade rigorosa no código TypeScript/React do Frontend, independentemente do autor.
  * Redução no atrito e no tempo de Code Reviews, transferindo a discussão estética para as ferramentas e focando o tempo humano em lógica e arquitetura de negócio.
  * Melhoria contínua na qualidade pela detecção antecipada (ex: variáveis não utilizadas ou hooks com dependências mal declaradas).
* **Negativas**:
  * O pipeline de CI (via Gatekeeper) rejeitará *commits* se houver quebra das regras configuradas.
  * Curva mínima de aprendizado inicial para configuração do ambiente de desenvolvimento por todos da equipe.
