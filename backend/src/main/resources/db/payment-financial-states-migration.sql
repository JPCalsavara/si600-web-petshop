-- Apply explicitly to existing databases before deploying financial state support.
-- Migração manual e opcional; o ambiente local não executa este arquivo.
BEGIN;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payment_state;
ALTER TABLE payments ADD CONSTRAINT ck_payment_state CHECK (status IN
    ('CRIANDO','GERADO','INCERTO','FALHOU','PAGO','CANCELADO','EXPIRADO',
     'REEMBOLSADO_PARCIAL','REEMBOLSADO','CONTESTADO'));
COMMIT;
