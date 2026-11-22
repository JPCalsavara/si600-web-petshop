import { useState, type FormEvent } from 'react';
import { ApiRequestError, login, requestPasswordReset } from '../../services/api';
import styles from './LoginPage.module.scss';

interface Props {
  onLoggedIn: () => void;
}

/**
 * US-01: tela de login (e-mail e senha). Erros do backend aparecem em um alerta acima do botão.
 * US-14: "Esqueci a senha" troca o formulário por um pedido de recuperação encaminhado ao Admin.
 */
export function LoginPage({ onLoggedIn }: Props) {
  const [mode, setMode] = useState<'login' | 'forgot'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function switchMode(next: 'login' | 'forgot') {
    setMode(next);
    setError(null);
    setNotice(null);
  }

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

  async function handleForgot(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    if (!email.trim()) {
      setError('Informe o e-mail.');
      return;
    }
    setSubmitting(true);
    try {
      setNotice(await requestPasswordReset(email.trim()));
    } catch (err) {
      setError(
        err instanceof ApiRequestError ? err.message : 'Não foi possível enviar o pedido. Tente novamente.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (mode === 'forgot') {
    return (
      <main className={styles.page}>
        <form className={styles.panel} onSubmit={handleForgot} noValidate>
          <h1>Recuperar acesso</h1>
          <p className={styles.hint}>
            Informe o e-mail do seu acesso. O pedido será enviado ao administrador, que informará uma senha temporária.
          </p>

          <label htmlFor="forgot-email">E-mail</label>
          <input
            id="forgot-email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          {error && <div role="alert" className={styles.alert}>{error}</div>}
          {notice && <div role="status" className={styles.success}>{notice}</div>}

          <button type="submit" className={styles.submit} disabled={submitting}>
            {submitting ? 'Enviando...' : 'Solicitar recuperação'}
          </button>
          <button type="button" className={styles.link} onClick={() => switchMode('login')}>
            Voltar ao login
          </button>
        </form>
      </main>
    );
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
        <button type="button" className={styles.link} onClick={() => switchMode('forgot')}>
          Esqueci a senha
        </button>
      </form>
    </main>
  );
}
