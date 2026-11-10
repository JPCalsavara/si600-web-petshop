import { useState, type FormEvent } from 'react';
import { ApiRequestError, login } from '../../services/api';
import styles from './LoginPage.module.scss';

interface Props {
  onLoggedIn: () => void;
}

/** US-01: tela de login (e-mail e senha). Erros do backend aparecem em um alerta acima do botão. */
export function LoginPage({ onLoggedIn }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!email.trim() || !password) {
      setError('Informe o e-mail e a senha.');
      return;
    }
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      onLoggedIn();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Não foi possível entrar. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <form className={styles.panel} onSubmit={handleSubmit} noValidate>
        <h1>Gestão de Eventos</h1>
        <p className={styles.hint}>Entre com o e-mail e a senha do seu acesso.</p>

        <label htmlFor="login-email">E-mail</label>
        <input
          id="login-email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <label htmlFor="login-password">Senha</label>
        <input
          id="login-password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {error && <div role="alert" className={styles.alert}>{error}</div>}

        <button type="submit" className={styles.submit} disabled={submitting}>
          {submitting ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
    </main>
  );
}
