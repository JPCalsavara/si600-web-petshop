import React from 'react';
import { HealthStatus } from '../../types';
import styles from './StatusCard.module.scss';

interface StatusCardProps {
  health: HealthStatus | null;
  loading: boolean;
  error: string | null;
  onRefresh: () => void;
}

export const StatusCard: React.FC<StatusCardProps> = ({
  health,
  loading,
  error,
  onRefresh,
}) => {
  return (
    <section className={styles.card}>
      <h2 className={styles.cardTitle}>Status do Ambiente de Desenvolvimento (TEC-01)</h2>

      {loading && (
        <div className={`${styles.statusBox} ${styles.loading}`}>
          <p>Verificando conexão com o backend...</p>
        </div>
      )}

      {!loading && health && (
        <div className={`${styles.statusBox} ${styles.online}`}>
          <span className={`${styles.badge} ${styles.success}`}>Backend Online</span>
          <p className={styles.infoRow}><strong>Status:</strong> {health.status}</p>
          <p className={styles.infoRow}><strong>Mensagem:</strong> {health.message}</p>
          <p className={styles.infoRow}>
            <strong>Timestamp:</strong> {new Date(health.timestamp).toLocaleString('pt-BR')}
          </p>
        </div>
      )}

      {!loading && error && (
        <div className={`${styles.statusBox} ${styles.offline}`}>
          <span className={`${styles.badge} ${styles.danger}`}>Backend Indisponível</span>
          <p className={styles.errorText}>{error}</p>
          <p className={styles.helpText}>
            Certifique-se de que o backend Spring Boot está em execução na porta 8080 (<code>./mvnw spring-boot:run</code>).
          </p>
        </div>
      )}

      <button className={styles.button} onClick={onRefresh} disabled={loading}>
        {loading ? 'Consultando...' : 'Reverificar Conexão'}
      </button>
    </section>
  );
};
