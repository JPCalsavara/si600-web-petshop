-- US-14: pedidos de recuperação de acesso ("Esqueci a senha") encaminhados ao Admin.
-- Idempotente e sem alteração de dados. Aplicar com psql antes de publicar a versão que a usa:
-- o perfil prod usa ddl-auto=validate e não cria tabelas sozinho.

CREATE TABLE IF NOT EXISTS password_reset_requests (
    id           UUID                     PRIMARY KEY,
    user_id      UUID                     NOT NULL,
    email        VARCHAR(254)             NOT NULL,
    status       VARCHAR(20)              NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at  TIMESTAMP WITH TIME ZONE,
    resolved_by  VARCHAR(254)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_status ON password_reset_requests (status, requested_at);

-- No máximo um pedido PENDENTE por usuário, garantido pelo banco (dois "Esqueci a senha" simultâneos da
-- mesma conta não criam dois pedidos). O serviço trata a violação e responde 202 normalmente.
-- Índice parcial é recurso do PostgreSQL; o H2 dos testes não o cria, e a checagem da aplicação continua valendo.
CREATE UNIQUE INDEX IF NOT EXISTS uq_password_reset_pending_user
    ON password_reset_requests (user_id) WHERE status = 'PENDENTE';
