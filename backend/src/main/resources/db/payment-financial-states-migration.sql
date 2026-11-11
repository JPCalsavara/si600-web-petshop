-- OBRIGATÓRIA para bancos que já possuem a tabela payments criada antes dos estados financeiros
-- (PAGO/CANCELADO/EXPIRADO existiam; REEMBOLSADO_PARCIAL, REEMBOLSADO e CONTESTADO não).
-- O Hibernate (ddl-auto=update) não altera CHECK existente, e a tabela criada por ele traz o CHECK
-- "payments_status_check", enquanto payment-schema.sql usa "ck_payment_state". Por isso esta migração
-- remove todo CHECK da tabela que referencie a coluna status, qualquer que seja o nome, e recria
-- ck_payment_state com todos os estados. É idempotente e não altera dados.
-- Aplicar manualmente (ex.: psql -f) antes de subir a versão que sincroniza esses estados.
BEGIN;
DO $$
DECLARE
    existing record;
BEGIN
    FOR existing IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'payments'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%(status)::text%'
    LOOP
        EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', existing.conname);
    END LOOP;
END $$;
ALTER TABLE payments ADD CONSTRAINT ck_payment_state CHECK (status IN
    ('CRIANDO','GERADO','INCERTO','FALHOU','PAGO','CANCELADO','EXPIRADO',
     'REEMBOLSADO_PARCIAL','REEMBOLSADO','CONTESTADO'));
COMMIT;
