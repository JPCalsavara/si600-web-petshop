# Guia de Desenvolvimento e Padrões de Engenharia

Este guia serve como especificação oficial para os desenvolvedores e assistentes de IA que implementarão e manterão a base de código do **SI600 Web Petshop**.

---

## 1. Estrutura Padrão do Repositório

O projeto adota uma estrutura desacoplada em monorepo simplificado:

```text
si600-web-petshop/
├── backend/                       # Aplicação Spring Boot (Java 21)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/br/unicamp/ft/si600/petshop/
│   │   │   │   ├── controller/    # Endpoints REST (@RestController)
│   │   │   │   ├── service/       # Lógica de negócio e transações (@Service, @Transactional)
│   │   │   │   ├── repository/    # Repositórios Spring Data JPA (@Repository)
│   │   │   │   ├── entity/        # Entidades JPA (@Entity)
│   │   │   │   ├── dto/           # Request e Response records/DTOs
│   │   │   │   └── exception/     # Tratador global (@RestControllerAdvice com RFC 7807)
│   │   │   └── resources/
│   │   │       └── application.yml # Configurações (PostgreSQL, JPA, porta 8080)
│   │   └── test/                  # Testes de Integração (@SpringBootTest)
│   ├── pom.xml                    # Gerenciador de dependências Maven
│   └── mvnw / mvnw.cmd            # Maven Wrapper
│
├── frontend/                      # Aplicação Web React (Vite)
│   ├── src/
│   │   ├── components/            # Componentes reutilizáveis
│   │   ├── pages/                 # Páginas e rotas da aplicação
│   │   ├── services/              # Cliente HTTP (fetch / axios) para a API backend
│   │   ├── types/                 # Interfaces e tipos TypeScript
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── package.json
│   └── vite.config.ts
│
├── cypress/                       # Testes End-to-End (E2E) e de componentes
│   ├── e2e/                       # Cenários E2E simulando o navegador
│   └── cypress.config.ts
│
├── docker-compose.yml             # PostgreSQL 16 local
├── CONTEXT.md                     # Linguagem ubíqua e invariantes de negócio
├── AGENTS.md                      # Diretrizes para assistentes de IA
└── README.md                      # Instruções de setup rápido
```

---

## 2. Configuração do Banco de Dados (PostgreSQL via Docker Compose)

O arquivo `docker-compose.yml` na raiz do projeto deve prover o banco relacional:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: petshop_postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: petshop_db
      POSTGRES_USER: petshop_user
      POSTGRES_PASSWORD: petshop_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U petshop_user -d petshop_db"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

### Comandos de Operação:
* Iniciar: `docker compose up -d postgres`
* Parar: `docker compose down`
* Limpar volume: `docker compose down -v`

---

## 3. Padrões do Backend (Java 21 / Spring Boot)

### Dependências Fundamentais (`pom.xml`)
* `spring-boot-starter-web` (REST API)
* `spring-boot-starter-data-jpa` (Persistência relacional)
* `spring-boot-starter-validation` (Jakarta Validation / Bean Validation)
* `org.postgresql:postgresql` (Driver JDBC)
* `spring-boot-starter-test` (JUnit 5, AssertJ, Spring Test)

### Configuração (`backend/src/main/resources/application.yml`)
```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/petshop_db
    username: petshop_user
    password: petshop_pass
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
```

### Configuração de CORS para o Frontend
O backend deve permitir requisições originadas do Vite (`http://localhost:5173`):
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

### Tratamento Global de Erros (RFC 7807 Problem Details)
Todas as exceções devem retornar o formato padrão `ProblemDetail`:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Dados de requisição inválidos");
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        problem.setProperty("invalidFields", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Violação de regra de negócio");
        problem.setDetail(ex.getMessage());
        return ResponseEntity.unprocessableEntity().body(problem);
    }
}
```

---

## 4. Padrões do Frontend (React + Vite + TypeScript)

### Setup Recomendado
* Inicialização com Vite: `npm create vite@latest frontend -- --template react-ts`
* Porta de desenvolvimento padrão: `5173`.
* Consumo da API: Centralizar chamadas HTTP em `src/services/api.ts`:
  ```typescript
  const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

  export async function checkBackendHealth(): Promise<{ status: string; timestamp: string }> {
    const response = await fetch(`${API_BASE_URL}/health`);
    if (!response.ok) {
      throw new Error(`Falha ao conectar no backend: ${response.statusText}`);
    }
    return response.json();
  }
  ```

---

## 5. Padrões de Teste Obrigatórios (ADR 0001 e ADR 0002)

### Proibição de Mocks Internos (ADR 0001)
* **NÃO** utilize `@MockBean` para mockar `Repository` ou `EntityManager`.
* **NÃO** escreva testes unitários que não exercitem a integração real com o banco de dados.
* Utilize `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` e `TestRestTemplate` para validar endpoints na borda HTTP real.

### Estrutura Obrigatória de Cenários (ADR 0002 - Tripartite)
Cada suite de teste de um caso de uso deve obrigatoriamente conter três categorias de testes:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgendamentoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // 1. CASO BOM (Happy Path)
    @Test
    void deveCriarAgendamentoComSucessoQuandoDadosForemValidos() {
        // Envia payload válido, espera HTTP 201 Created e confirma no banco
    }

    // 2. CASO RUIM (Sad Path / Regra de Negócio)
    @Test
    void deveRecusarAgendamentoQuandoHorarioEstiverOcupado() {
        // Tenta reservar horário conflitante, espera HTTP 409 Conflict ou 422
    }

    // 3. CASO INCOMPLETO (Boundary / Malformed Payload)
    @Test
    void deveRetornarBadRequestQuandoCamposObrigatoriosEstiveremAusentes() {
        // Envia payload sem clienteId ou dataHora, espera HTTP 400 Bad Request
    }
}
```

---

## 6. Testes E2E com Cypress

* Diretório: `cypress/`
* `cypress.config.ts`:
  ```typescript
  import { defineConfig } from 'cypress';

  export default defineConfig({
    e2e: {
      baseUrl: 'http://localhost:5173',
      supportFile: false,
    },
  });
  ```
* Teste básico de conexão Front <-> Back (`cypress/e2e/health.cy.ts`):
  ```typescript
  describe('Ambiente e Conexão Front-Back', () => {
    it('deve carregar a página inicial e exibir o status do backend online', () => {
      cy.visit('/');
      cy.contains('Backend Status: Online').should('be.visible');
    });
  });
  ```

---

## 7. Fluxo de Trabalho Git para o Desenvolvedor

```bash
# 1. Atualizar e sincronizar a partir de dev
git checkout dev
git pull origin dev

# 2. Ir para a sua branch individual
git checkout member/<seu-slug>
git merge dev

# 3. Implementar o código e os testes tripartite
# ...

# 4. Validar com o AI Gatekeeper local antes do push
bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target .

# 5. Fazer o push da sua branch (vai simultaneamente para GitLab e GitHub)
git add .
git commit -m "feat(ambiente): scaffold inicial do backend e frontend com postgres"
git push origin member/<seu-slug>

# 6. Abrir Merge Request no GitLab Unicamp
# Origem: member/<seu-slug> -> Destino: dev
# Solicitar 2 aprovações dos colegas de equipe.
```
