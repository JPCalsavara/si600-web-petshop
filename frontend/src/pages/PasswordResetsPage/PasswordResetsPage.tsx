import { useEffect, useState } from 'react';
import { listPasswordResets, resolvePasswordReset } from '../../services/api';
import { formatDateTime } from '../../utils/date';
import type { PasswordResetRequestItem, TemporaryPassword } from '../../types';
import styles from './PasswordResetsPage.module.scss';

/**
 * US-14 (Admin): pedidos de recuperação de acesso. O Admin gera a senha temporária e a repassa ao usuário.
 * A senha aparece uma única vez, logo após ser gerada; ao fechar o aviso ela não pode ser recuperada.
 */
export function PasswordResetsPage() {
  const [requests, setRequests] = useState<PasswordResetRequestItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [generated, setGenerated] = useState<TemporaryPassword | null>(null);
  const [copied, setCopied] = useState(false);

  async function load() {
    try {
      setLoading(true);
      setError(null);
      setRequests(await listPasswordResets());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível carregar os pedidos.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function generate(request: PasswordResetRequestItem) {
    try {
      setBusyId(request.id);
      setError(null);
      setCopied(false);
      setGenerated(await resolvePasswordReset(request.id));
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível gerar a senha temporária.');
    } finally {
      setBusyId(null);
    }
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Usuários</h1>
          <p className={styles.subtitle}>
            Pedidos de recuperação de acesso feitos na tela de login. Ao gerar a senha temporária, os acessos
            abertos do usuário são encerrados. Repasse a senha ao usuário por um canal seguro.
          </p>
        </div>
        {!loading && (
          <span className={styles.countBadge}>
            {requests.length} {requests.length === 1 ? 'pedido pendente' : 'pedidos pendentes'}
          </span>
        )}
      </header>

      {error && <div role="alert" className={styles.alertError}>{error}</div>}

      {generated && (
        <div role="status" className={styles.generated} data-testid="temporary-password-box">
          <p className={styles.generatedText}>
            Senha temporária de <strong>{generated.email}</strong>. Ela não será exibida novamente.
          </p>
          <code className={styles.password} data-testid="temporary-password">{generated.temporaryPassword}</code>
          <div className={styles.generatedActions}>
            <button type="button" className={styles.secondary} onClick={() => void copy(generated.temporaryPassword)}>
              {copied ? 'Copiada' : 'Copiar'}
            </button>
            <button type="button" className={styles.secondary} onClick={() => setGenerated(null)}>
              Fechar aviso
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <div className={styles.emptyCard}><p>Carregando pedidos...</p></div>
      ) : requests.length === 0 ? (
        <div className={styles.emptyCard}><p>Não há pedidos de recuperação de acesso pendentes.</p></div>
      ) : (
        <ul className={styles.list}>
          {requests.map((request) => (
            <li key={request.id} className={styles.row} data-testid="password-reset-row">
              <div>
                <strong className={styles.email}>{request.email}</strong>
                <span className={styles.meta}>Solicitado em {formatDateTime(request.requestedAt)}</span>
              </div>
              <button
                type="button"
                className={styles.primary}
                disabled={busyId === request.id}
                onClick={() => void generate(request)}
              >
                {busyId === request.id ? 'Gerando...' : 'Gerar senha temporária'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
