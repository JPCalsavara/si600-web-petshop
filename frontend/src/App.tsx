import { useEffect, useState } from 'react';
import { checkBackendHealth, HealthStatus } from './services/api';
import './App.css';

export default function App() {
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  async function loadHealth() {
    try {
      setLoading(true);
      setError(null);
      const data = await checkBackendHealth();
      setHealth(data);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Erro desconhecido ao conectar com o backend');
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadHealth();
  }, []);

  return (
    <div className="container">
      <header className="header">
        <h1>SI600 - Sistema de Eventos</h1>
        <p className="subtitle">Gestão e Acompanhamento de Pagamento de Estandes</p>
      </header>

      <main className="card">
        <h2>Status do Ambiente de Desenvolvimento (TEC-01)</h2>
        
        <div className="status-section">
          {loading && <p className="status-badge loading">Verificando conexão com o backend...</p>}

          {!loading && health && (
            <div className="status-box online">
              <span className="badge success">Backend Online</span>
              <p><strong>Status:</strong> {health.status}</p>
              <p><strong>Mensagem:</strong> {health.message}</p>
              <p><strong>Timestamp:</strong> {new Date(health.timestamp).toLocaleString('pt-BR')}</p>
            </div>
          )}

          {!loading && error && (
            <div className="status-box offline">
              <span className="badge danger">Backend Indisponível</span>
              <p className="error-text">{error}</p>
              <p className="help-text">
                Certifique-se de que o backend Spring Boot está em execução na porta 8080 (<code>./mvnw spring-boot:run</code>).
              </p>
            </div>
          )}
        </div>

        <button className="btn" onClick={loadHealth} disabled={loading}>
          {loading ? 'Consultando...' : 'Reverificar Conexão'}
        </button>
      </main>

      <footer className="footer">
        <p>SI600 - Turma A - Grupo B | Unicamp</p>
      </footer>
    </div>
  );
}
