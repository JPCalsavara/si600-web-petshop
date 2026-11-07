import { useEffect, useState } from 'react';
import { downloadProjectCsv, getCurrentActorRole, getProjectHistory } from '../../services/api';
import { ACTOR_ROLE_LABELS, HISTORY_EVENT_LABELS } from '../../constants/projectHistory';
import type { ProjectHistoryEvent } from '../../types';
import styles from './ProjectTimeline.module.scss';

interface Props {
  projectId: string;
  projectName?: string;
}

function formatDateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString('pt-BR');
}

export function ProjectTimeline({ projectId, projectName }: Props) {
  const [events, setEvents] = useState<ProjectHistoryEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [exportingCsv, setExportingCsv] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isAdmin = (typeof window !== 'undefined' && window.localStorage.getItem('MOCK_ROLE')?.toUpperCase() === 'ADMIN')
    || getCurrentActorRole() === 'ADMIN';

  async function handleExportCsv() {
    try {
      setExportingCsv(true);
      setError(null);
      await downloadProjectCsv(projectId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível exportar o CSV do projeto.');
    } finally {
      setExportingCsv(false);
    }
  }

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError(null);
        const result = await getProjectHistory(projectId);
        if (active) {
          setEvents(result);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : 'Não foi possível carregar o histórico do projeto.');
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [projectId]);

  return (
    <section className={styles.timelineCard} aria-label="Histórico do projeto">
      <header className={styles.header}>
        <div className={styles.titleGroup}>
          <h2>Histórico do Projeto</h2>
          <p>
            {projectName
              ? `Linha do tempo cronológica de ações registradas para ${projectName}.`
              : 'Linha do tempo cronológica de ações registradas no projeto.'}
          </p>
        </div>
        <div className={styles.headerActions}>
          {isAdmin && (
            <button
              type="button"
              className={styles.exportCsvBtn}
              disabled={exportingCsv}
              onClick={() => void handleExportCsv()}
              data-testid="btn-export-csv-timeline"
            >
              {exportingCsv ? 'Exportando...' : 'Exportar em CSV'}
            </button>
          )}
          <span className={styles.readOnlyBadge}>Somente leitura</span>
        </div>
      </header>

      {error && (
        <div className={styles.alertDanger} role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <p className={styles.empty}>Carregando histórico...</p>
      ) : events.length === 0 ? (
        <p className={styles.empty}>Nenhum evento registrado para este projeto até o momento.</p>
      ) : (
        <ol className={styles.timelineList}>
          {events.map((event) => (
            <li key={event.id} className={styles.timelineItem}>
              <div className={styles.itemTop}>
                <span className={styles.eventLabel}>
                  {HISTORY_EVENT_LABELS[event.eventType] ?? event.eventType}
                </span>
                <time className={styles.timestamp} dateTime={event.createdAt}>
                  {formatDateTime(event.createdAt)}
                </time>
              </div>
              <p className={styles.description}>{event.description}</p>
              <div className={styles.meta}>
                <span>Autor: <strong>{event.actorId}</strong></span>
                <span className={styles.roleBadge}>
                  {ACTOR_ROLE_LABELS[event.actorRole] ?? event.actorRole}
                </span>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
