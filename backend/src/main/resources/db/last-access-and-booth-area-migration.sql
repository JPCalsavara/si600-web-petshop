-- BUG-01 / BUG-04: acerto de dados existentes. Não altera o esquema.
-- Aplicar manualmente (ex.: psql -f) ao publicar a versão que registra o acesso no login e que
-- passa a exigir a metragem na criação do projeto.

-- BUG-01 (retroativo): clientes que já fizeram login antes da correção continuam com last_access_at nulo e
-- aparecem como "Nunca Acessou" no dashboard do Admin. O único registro de login que existe é a sessão
-- (auth_sessions), que expira 8 horas após o login (AuthService.SESSION_TTL) e é apagada pelo
-- SessionCleanupScheduler depois de expirar. Por isso só dá para recuperar o acesso de quem ainda tem
-- sessão guardada; quem já teve a sessão apagada volta a ser marcado no próximo login.
-- Idempotente: só preenche projetos com last_access_at nulo e usa o login mais antigo que ainda existe.
BEGIN;
UPDATE projects p
SET last_access_at = s.first_login
FROM (
    SELECT u.email, MIN(a.expires_at) - INTERVAL '8 hours' AS first_login
    FROM auth_sessions a
    JOIN user_accounts u ON u.id = a.user_id
    WHERE u.role = 'CLIENT'
    GROUP BY u.email
) s
WHERE p.owner_id = s.email
  AND p.last_access_at IS NULL;
COMMIT;

-- BUG-04: a metragem agora é definida pelo Admin na criação e a aprovação usa o valor gravado no projeto.
-- Projetos criados antes dessa mudança em AGUARDANDO_PDF não têm metragem (o cliente a informava ao enviar o
-- PDF) e a aprovação deles responde 422. A metragem não pode ser deduzida: o Admin precisa informar o valor.
-- 1) Liste os projetos afetados:
--      SELECT id, company_name, status FROM projects
--      WHERE booth_area_m2 IS NULL AND status = 'AGUARDANDO_PDF';
-- 2) Para cada um, grave a metragem combinada com o expositor (exemplo):
--      UPDATE projects SET booth_area_m2 = 42.50 WHERE id = '<uuid do projeto>';
-- Projetos em PDF_EM_ANALISE, APROVADO ou REPROVADO já têm a metragem que o cliente informou ao enviar o PDF.
