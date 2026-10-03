# ADR 0003: Estratégia de Armazenamento de PDFs (Boletos e Comprovantes)

## Status
Proposto (Proposed)

## Data
2026-09-28

## Contexto

No desenvolvimento do sistema de gestão de eventos, uma funcionalidade crítica é o cálculo de taxas de estandes e o acompanhamento individualizado de pagamentos via Mercado Pago. O ciclo de vida desses pagamentos (com estados de 'não pago', 'pendente' e 'pago') gera a necessidade de lidar com documentos em formato PDF, como os próprios boletos gerados e possíveis comprovantes de transação.

Precisamos definir uma estratégia arquitetural clara e escalável para o armazenamento, upload e download destes arquivos PDF. Armazenar arquivos estáticos incorretamente pode gerar problemas graves de escalabilidade, custos excessivos de infraestrutura ou perda de dados.

Foram avaliadas três abordagens principais para o armazenamento dos PDFs:

1.  **Disco Local do Servidor/Container:**
    *   *Prós:* Implementação extremamente rápida e simples no curto prazo.
    *   *Contras:* Não escala horizontalmente. Se a aplicação rodar em múltiplos *containers* (ex: Kubernetes/Docker), o arquivo salvo em um não estará disponível nos outros. Além disso, se o container for reiniciado, os dados podem ser perdidos (arquitetura *stateful* indesejada).
2.  **Banco de Dados (Colunas BLOB/ByteA):**
    *   *Prós:* Consistência transacional garantida (o PDF e o registro de pagamento são salvos juntos) e backup unificado.
    *   *Contras:* Aumenta drasticamente o tamanho e o custo do banco de dados relacional. Degrada a performance de consultas e encarece o processo de backup e *restore*.
3.  **Storage Externo / Object Storage (ex: Supabase Storage, AWS S3):**
    *   *Prós:* Alta disponibilidade, escalabilidade infinita, baixo custo para armazenamento de arquivos e CDN nativa para entrega rápida. Desonera o banco de dados principal.
    *   *Contras:* Adiciona uma dependência de rede (latência) e exige o gerenciamento de credenciais e permissões (URLs assinadas) para arquivos sensíveis.

---

## Decisão

Optamos por utilizar **Storage Externo via Supabase Storage** para o armazenamento de todos os PDFs do sistema, alavancando a infraestrutura já adotada para a persistência e gestão de estados da aplicação.

Para garantir a segurança, performance e o bom uso dos recursos, estabelecemos as seguintes diretrizes técnicas:

### 1. Limite de Tamanho de Arquivo
*   Fica estabelecido um **limite rígido (*hard limit*) de 10 MB** por arquivo PDF.
*   A validação desse limite deve ocorrer em duas etapas: primeiramente no *frontend* (para *feedback* rápido ao usuário) e, obrigatoriamente, no *backend* (API) antes do envio ao *bucket* do Supabase.

### 2. Fluxo de Upload
*   O *upload* não deve ser feito diretamente do *frontend* para o Supabase de forma pública.
*   O cliente envia o arquivo para a nossa API. A API valida a autenticação do usuário, o tipo do arquivo (MIME type restrito a `application/pdf`) e o limite de tamanho (10 MB).
*   Após a validação, a API realiza o *upload* para um *bucket* privado no Supabase Storage e salva apenas o caminho/identificador (ex: `caminho/do/arquivo.pdf`) no banco de dados relacional, vinculado ao registro do pagamento ou do estande.

### 3. Fluxo de Download e Acesso
*   Os *buckets* de PDFs serão **privados**, visto que boletos e comprovantes contêm dados sensíveis do cliente.
*   O *download* será feito por meio de **URLs Assinadas (*Signed URLs*)**.
*   Quando o cliente solicitar a visualização ou *download* do PDF, o *frontend* fará uma requisição à API. A API validará se o usuário tem permissão para acessar aquele documento específico e, em caso positivo, solicitará ao Supabase Storage uma URL assinada temporária (ex: com expiração de 15 minutos) e a retornará ao cliente.

---

## Consequências

### Positivas
*   **Performance e Escalabilidade:** O banco de dados relacional continuará leve e rápido, processando apenas os metadados e os estados financeiros.
*   **Alinhamento de Stack:** Como o Supabase já está sendo utilizado para persistência, usar o Supabase Storage evita a adição de novos fornecedores de nuvem (como AWS ou GCP) à arquitetura.
*   **Segurança:** A restrição de MIME types e limite de 10 MB protege contra ataques de negação de serviço (DoS) via arquivos gigantes. O uso de *Signed URLs* garante que apenas os usuários autorizados consigam ver seus próprios boletos.

### Negativas e Mitigações
*   **Complexidade de Acesso:** O acesso aos arquivos requer uma etapa extra para geração do *link* temporário.
    *   *Mitigação:* Criar um *middleware* ou função utilitária padrão no *backend* dedicada exclusivamente à geração dessas URLs assinadas de forma simplificada para o resto da equipe.
*   **Sobrecarga da API no Upload:** O arquivo passa pela API antes de ir para o Storage.
    *   *Mitigação:* Para arquivos de até 10 MB, essa sobrecarga é aceitável na maioria das plataformas Node.js/Python padrão. Caso se torne um gargalo no futuro, pode-se avaliar a migração para *Pre-signed Upload URLs*.