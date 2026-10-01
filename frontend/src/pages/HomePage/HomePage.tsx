import React, { useEffect, useState } from 'react';
import { Header } from '../../components/Header/Header';
import { StatusCard } from '../../components/StatusCard/StatusCard';
import { checkBackendHealth } from '../../services/api';
import { HealthStatus } from '../../types';
import styles from './HomePage.module.scss';

export const HomePage: React.FC = () => {
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
    <div className={styles.page}>
      <Header />
      <main>
        <StatusCard
          health={health}
          loading={loading}
          error={error}
          onRefresh={loadHealth}
        />
      </main>
      <footer className={styles.footer}>
        <p>SI600 - Turma A - Grupo B | Unicamp</p>
      </footer>
    </div>
  );
};
