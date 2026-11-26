-- BUG-07: endereço estruturado do cliente.
-- Idempotente e sem alteração de dados. Aplicar com psql antes de publicar a versão que o usa.
-- Projetos antigos mantêm o texto livre em projects.address e ficam com as colunas novas nulas.

ALTER TABLE projects ADD COLUMN IF NOT EXISTS address_zip_code VARCHAR(8);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS address_street VARCHAR(200);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS address_number VARCHAR(30);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS address_complement VARCHAR(100);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS address_neighborhood VARCHAR(100);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS address_city VARCHAR(100);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS address_state VARCHAR(2);

-- O texto livre passa a receber o endereço completo montado a partir dos campos acima.
ALTER TABLE projects ALTER COLUMN address TYPE VARCHAR(600);
