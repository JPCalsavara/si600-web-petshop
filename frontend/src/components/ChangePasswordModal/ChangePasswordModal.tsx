import { useEffect, useState, type FormEvent } from 'react';
import { ApiRequestError, changePassword } from '../../services/api';
import { validateNewPassword } from '../../utils/password';
import styles from './ChangePasswordModal.module.scss';

interface Props {
  onClose: () => void;
}

/** US-14: troca de senha do usuário logado (Admin ou Cliente), aberta pelo perfil no menu lateral. */
export function ChangePasswordModal({ onClose }: Props) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!currentPassword) {
      setError('Informe a senha atual.');
      return;
    }
    const problem = validateNewPassword(newPassword, confirmation);
    if (problem) {
      setError(problem);
      return;
    }
    if (newPassword === currentPassword) {
      setError('A nova senha deve ser diferente da atual.');
      return;
    }
    setSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
      setDone(true);
    } catch (err) {
      setError(
        err instanceof ApiRequestError
          ? (err.invalidFields.newPassword ?? err.message)
          : 'Não foi possível alterar a senha. Tente novamente.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={styles.backdrop} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={styles.panel} role="dialog" aria-modal="true" aria-labelledby="change-password-title">
        <h2 id="change-password-title" className={styles.title}>Alterar senha</h2>

        {done ? (
          <>
            <div role="status" className={styles.success}>
              Senha alterada com sucesso. Os outros acessos desta conta foram encerrados.
            </div>
            <div className={styles.actions}>
              <button type="button" className={styles.primary} onClick={onClose}>Fechar</button>
            </div>
          </>
        ) : (
          <form onSubmit={handleSubmit} noValidate className={styles.form}>
            <p className={styles.hint}>
              Use pelo menos 8 caracteres, com letras e números, diferente da senha atual.
            </p>

            <label htmlFor="current-password">Senha atual</label>
            <input
              id="current-password"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />

            <label htmlFor="new-password">Nova senha</label>
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />

            <label htmlFor="confirm-password">Confirmar nova senha</label>
            <input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
            />

            {error && <div role="alert" className={styles.alert}>{error}</div>}

            <div className={styles.actions}>
              <button type="button" className={styles.secondary} onClick={onClose} disabled={submitting}>
                Cancelar
              </button>
              <button type="submit" className={styles.primary} disabled={submitting}>
                {submitting ? 'Salvando...' : 'Salvar nova senha'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
