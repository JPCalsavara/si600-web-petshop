import { useEffect, useState } from 'react';
import { getAdminDashboard } from '../../services/api';
import type { AdminDashboardData, InactiveExhibitor, Project, PendingApproval } from '../../types';
import { formatDate } from '../../utils/date';
import styles from './AdminDashboardPage.module.scss';

interface Props {
  onOpenPdfReview?: (projectId?: string) => void;
  onNavigateToProjects?: () => void;
}

export function AdminDashboardPage({ onOpenPdfReview, onNavigateToProjects }: Props) {
  const [data, setData] = useState<AdminDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'kanban' | 'table'>('kanban');

  async function loadDashboard() {
    try {
      setLoading(true);
      setError(null);
      const result = await getAdminDashboard();
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível carregar as informações do dashboard.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadDashboard();
  }, []);

  // 1. Grupo: Nunca acessou / Inativos
  const neverAccessedItems: InactiveExhibitor[] = data?.inactiveExhibitors || [];

  // Mapeamento de aprovações pendentes por ID de projeto
  const pendingApprovalsList: PendingApproval[] = data?.pendingApprovals || [];
  const approvalIds = new Set(pendingApprovalsList.map((app) => app.projectId));

  // 2. Grupo: Pendentes (prazos com atraso/alerta ou plantas em análise)
  const pendingDeadlineProjects: Project[] = (data?.pendingDeadlines || []).filter(
    (p) => (p.alertTag !== 'EM_DIA' && Boolean(p.alertTag)) || p.status === 'PDF_EM_ANALISE' || approvalIds.has(p.id)
  );

  // Aprovações que porventura não estejam na lista de prazos
  const additionalApprovals = pendingApprovalsList.filter(
    (app) => !pendingDeadlineProjects.some((p) => p.id === app.projectId)
  );

  const totalPendingCount = pendingDeadlineProjects.length + additionalApprovals.length;

  // 3. Grupo: Em dia (sem alertas críticos e fora de análise)
  const onTrackProjects: Project[] = (data?.pendingDeadlines || []).filter(
    (p) => (p.alertTag === 'EM_DIA' || !p.alertTag) && p.status !== 'PDF_EM_ANALISE' && !approvalIds.has(p.id)
  );

  return (
    <div className={styles.dashboard} data-testid="admin-dashboard-page">
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Dashboard Operacional</h1>
          <p className={styles.subtitle}>
            Visão consolidada de expositores, pendências operacionais e aprovações de plantas.
          </p>
        </div>

        <div className={styles.headerControls}>
          <div className={styles.viewToggleGroup} role="group" aria-label="Modo de visualização">
            <button
              type="button"
              className={`${styles.toggleButton} ${viewMode === 'kanban' ? styles.activeToggle : ''}`}
              onClick={() => setViewMode('kanban')}
              data-testid="btn-view-kanban"
            >
              Kanban
            </button>
            <button
              type="button"
              className={`${styles.toggleButton} ${viewMode === 'table' ? styles.activeToggle : ''}`}
              onClick={() => setViewMode('table')}
              data-testid="btn-view-table"
            >
              Tabelas
            </button>
          </div>

          <button
            type="button"
            className={styles.refreshButton}
            onClick={() => void loadDashboard()}
            disabled={loading}
            data-testid="btn-refresh-dashboard"
          >
            {loading ? 'Atualizando...' : 'Atualizar'}
          </button>
        </div>
      </header>

      {error && (
        <div className={styles.alertError} role="alert" data-testid="dashboard-error">
          <span>{error}</span>
        </div>
      )}

      {loading && !data && (
        <div className={styles.loadingContainer} data-testid="dashboard-loading">
          <div className={styles.spinner}></div>
          <p>Consolidando métricas operacionais...</p>
        </div>
      )}

      {data && viewMode === 'kanban' && (
        <div className={styles.kanbanBoard} data-testid="kanban-board">
          {/* Coluna 1: Nunca Acessou */}
          <div
            className={`${styles.kanbanColumn} ${styles.colNeverAccessed}`}
            data-testid="inactive-exhibitors-panel"
          >
            <div className={styles.kanbanColumnHeader}>
              <div className={styles.columnTitleArea}>
                <span className={`${styles.columnIndicator} ${styles.indicatorRed}`}></span>
                <h2>Nunca Acessou</h2>
              </div>
              <span className={styles.columnCountBadge} data-testid="count-never-accessed">
                {neverAccessedItems.length}
              </span>
            </div>
            <p className={styles.columnDescription}>
              Expositores cadastrados sem acesso recente ou que nunca entraram no portal.
            </p>

            <div className={styles.kanbanCardsList}>
              {neverAccessedItems.length === 0 ? (
                <div className={styles.emptyCard}>Nenhum expositor inativo registrado.</div>
              ) : (
                neverAccessedItems.map((item) => (
                  <article
                    key={item.projectId}
                    className={styles.kanbanCard}
                    data-testid={`row-inactive-${item.projectId}`}
                  >
                    <div className={styles.cardHeader}>
                      <strong className={styles.cardCompanyName}>{item.companyName}</strong>
                      {item.neverAccessed ? (
                        <span className={styles.badgeNeverAccessed} data-testid="badge-never-accessed">
                          Nunca acessou
                        </span>
                      ) : (
                        <span className={styles.badgeDaysAgo} data-testid="badge-days-ago">
                          {item.daysSinceLastAccess === 0 ? 'Hoje' : `${item.daysSinceLastAccess} dia(s) atrás`}
                        </span>
                      )}
                    </div>
                    <div className={styles.cardBody}>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Contato:</span>
                        <span className={styles.metaValue}>{item.contactEmail}</span>
                      </div>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Responsável:</span>
                        <span className={styles.metaValue}>{item.representativeName}</span>
                      </div>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Criado em:</span>
                        <span className={styles.metaValue}>{formatDate(item.createdAt)}</span>
                      </div>
                    </div>
                  </article>
                ))
              )}
            </div>
          </div>

          {/* Coluna 2: Pendentes */}
          <div
            className={`${styles.kanbanColumn} ${styles.colPending}`}
            data-testid="pending-deadlines-panel"
          >
            <div className={styles.kanbanColumnHeader}>
              <div className={styles.columnTitleArea}>
                <span className={`${styles.columnIndicator} ${styles.indicatorAmber}`}></span>
                <h2>Pendentes</h2>
              </div>
              <span
                className={`${styles.columnCountBadge} ${totalPendingCount > 0 ? styles.countWarning : ''}`}
                data-testid="count-pending"
              >
                {totalPendingCount}
              </span>
            </div>
            <p className={styles.columnDescription}>
              Plantas aguardando aprovação técnica e projetos com prazos de PDF ou pagamento pendentes.
            </p>

            <div className={styles.kanbanCardsList}>
              {totalPendingCount === 0 ? (
                <div className={styles.emptyCard}>Nenhum projeto com pendências no momento.</div>
              ) : (
                <>
                  {/* Cards de Aprovações Pendentes */}
                  <div data-testid="pending-approvals-panel" className={styles.approvalsGroup}>
                    {pendingApprovalsList.map((app) => (
                      <article
                        key={`approval-${app.projectId}`}
                        className={`${styles.kanbanCard} ${styles.cardApprovalHighlight}`}
                        data-testid={`row-approval-${app.projectId}`}
                      >
                        <div className={styles.cardHeader}>
                          <strong className={styles.cardCompanyName}>{app.companyName}</strong>
                          <span className={`${styles.alertBadge} ${styles.alertPendente}`} data-testid="badge-approval-status">
                            Planta em Análise
                          </span>
                        </div>
                        <div className={styles.cardBody}>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Arquivo:</span>
                            <span className={styles.metaValue}>{app.pdfOriginalFilename || 'planta.pdf'}</span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Área:</span>
                            <span className={styles.metaValue}>{app.boothAreaM2 ? `${app.boothAreaM2} m²` : '-'}</span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Enviado em:</span>
                            <span className={styles.metaValue}>{formatDate(app.pdfUploadedAt)}</span>
                          </div>
                        </div>
                        <div className={styles.cardFooter}>
                          <button
                            type="button"
                            className={styles.actionShortcut}
                            onClick={() => onOpenPdfReview?.(app.projectId)}
                            data-testid={`shortcut-review-pdf-${app.projectId}`}
                            id={`shortcut-review-pdf-${app.projectId}`}
                          >
                            Analisar Planta →
                          </button>
                        </div>
                      </article>
                    ))}
                  </div>

                  {/* Cards de Pendências de Prazo */}
                  {pendingDeadlineProjects
                    .filter((p) => !approvalIds.has(p.id))
                    .map((p) => (
                      <article
                        key={`deadline-${p.id}`}
                        className={styles.kanbanCard}
                        data-testid={`row-deadline-${p.id}`}
                      >
                        <div className={styles.cardHeader}>
                          <strong className={styles.cardCompanyName}>{p.companyName}</strong>
                          <span
                            className={`${styles.alertBadge} ${
                              p.alertTag === 'ATRASADO'
                                ? styles.alertAtrasado
                                : p.alertTag === 'PRAZO_PROXIMO'
                                ? styles.alertPrazoProximo
                                : p.alertTag === 'PENDENTE'
                                ? styles.alertPendente
                                : styles.alertEmDia
                            }`}
                            data-testid={`badge-alert-${p.alertTag || 'PENDENTE'}`}
                          >
                            {p.alertTag === 'ATRASADO'
                              ? 'Atrasado'
                              : p.alertTag === 'PRAZO_PROXIMO'
                              ? 'Prazo Próximo'
                              : p.alertTag === 'PENDENTE'
                              ? 'Pendente'
                              : 'Em Dia'}
                          </span>
                        </div>
                        <div className={styles.cardBody}>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Responsável:</span>
                            <span className={styles.metaValue}>{p.representativeName || '-'}</span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Prazo PDF:</span>
                            <span className={styles.metaValue}>{formatDate(p.pdfDeadline)}</span>
                          </div>
                          <div className={styles.metaRow}>
                            <span className={styles.metaLabel}>Prazo Pagamento:</span>
                            <span className={styles.metaValue}>{formatDate(p.paymentDeadline)}</span>
                          </div>
                        </div>
                      </article>
                    ))}
                </>
              )}
            </div>
          </div>

          {/* Coluna 3: Em Dia */}
          <div
            className={`${styles.kanbanColumn} ${styles.colOnTrack}`}
            data-testid="on-track-panel"
          >
            <div className={styles.kanbanColumnHeader}>
              <div className={styles.columnTitleArea}>
                <span className={`${styles.columnIndicator} ${styles.indicatorGreen}`}></span>
                <h2>Em Dia</h2>
              </div>
              <span className={styles.columnCountBadge} data-testid="count-on-track">
                {onTrackProjects.length}
              </span>
            </div>
            <p className={styles.columnDescription}>
              Projetos com obrigações regulares, plantas aprovadas ou sem pendências imediatas.
            </p>

            <div className={styles.kanbanCardsList}>
              {onTrackProjects.length === 0 ? (
                <div className={styles.emptyCard}>Nenhum projeto regularizado registrado.</div>
              ) : (
                onTrackProjects.map((p) => (
                  <article
                    key={`ontrack-${p.id}`}
                    className={styles.kanbanCard}
                    data-testid={`row-ontrack-${p.id}`}
                  >
                    <div className={styles.cardHeader}>
                      <strong className={styles.cardCompanyName}>{p.companyName}</strong>
                      <span className={`${styles.alertBadge} ${styles.alertEmDia}`} data-testid="badge-alert-EM_DIA">
                        Em Dia
                      </span>
                    </div>
                    <div className={styles.cardBody}>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Responsável:</span>
                        <span className={styles.metaValue}>{p.representativeName || '-'}</span>
                      </div>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Status:</span>
                        <span className={styles.metaValue}>{p.status}</span>
                      </div>
                      <div className={styles.metaRow}>
                        <span className={styles.metaLabel}>Área Aprovada:</span>
                        <span className={styles.metaValue}>{p.boothAreaM2 ? `${p.boothAreaM2} m²` : '-'}</span>
                      </div>
                    </div>
                  </article>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {data && viewMode === 'table' && (
        <div className={styles.grid}>
          {/* Painel 1: Expositores Inativos */}
          <section className={styles.panel} data-testid="inactive-exhibitors-panel-table">
            <div className={styles.panelHeader}>
              <div className={styles.panelTitleGroup}>
                <h2>Expositores Inativos</h2>
              </div>
              <span className={styles.counterBadge} data-testid="count-inactives-table">
                {data.inactiveExhibitors.length}
              </span>
            </div>
            <p className={styles.panelDescription}>
              Monitoramento de engajamento dos expositores e sinalização de quem nunca acessou a plataforma.
            </p>

            <div className={styles.tableWrap}>
              {data.inactiveExhibitors.length === 0 ? (
                <div className={styles.empty}>Nenhum expositor inativo registrado.</div>
              ) : (
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Empresa</th>
                      <th>Criado Em</th>
                      <th>Último Acesso</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.inactiveExhibitors.map((item) => (
                      <tr key={item.projectId} data-testid={`table-row-inactive-${item.projectId}`}>
                        <td>
                          <strong>{item.companyName}</strong>
                          <div className={styles.subtext}>{item.contactEmail}</div>
                        </td>
                        <td>{formatDate(item.createdAt)}</td>
                        <td>
                          {item.neverAccessed ? (
                            <span className={styles.badgeNeverAccessed}>Nunca acessou</span>
                          ) : (
                            <span className={styles.badgeDaysAgo}>
                              {item.daysSinceLastAccess === 0
                                ? 'Hoje'
                                : `${item.daysSinceLastAccess} dia(s) atrás`}
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </section>

          {/* Painel 2: Projetos com Pendências e Prazos */}
          <section className={styles.panel} data-testid="pending-deadlines-panel-table">
            <div className={styles.panelHeader}>
              <div className={styles.panelTitleGroup}>
                <h2>Pendências e Prazos</h2>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {onNavigateToProjects && (
                  <button
                    type="button"
                    className={styles.linkButton}
                    onClick={onNavigateToProjects}
                    data-testid="btn-view-all-projects-table"
                  >
                    Ver todos os projetos →
                  </button>
                )}
                <span className={styles.counterBadge}>
                  {data.pendingDeadlines.length}
                </span>
              </div>
            </div>
            <p className={styles.panelDescription}>
              Alertas operacionais calculados com base no status do estande e datas limites estipuladas.
            </p>

            <div className={styles.tableWrap}>
              {data.pendingDeadlines.length === 0 ? (
                <div className={styles.empty}>Nenhum projeto com pendências de prazo.</div>
              ) : (
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Empresa</th>
                      <th>Prazo PDF</th>
                      <th>Prazo Pagamento</th>
                      <th>Status de Alerta</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.pendingDeadlines.map((p) => (
                      <tr key={p.id}>
                        <td>
                          <strong>{p.companyName}</strong>
                          <div className={styles.subtext}>{p.representativeName}</div>
                        </td>
                        <td>{formatDate(p.pdfDeadline)}</td>
                        <td>{formatDate(p.paymentDeadline)}</td>
                        <td>
                          <span
                            className={`${styles.alertBadge} ${
                              p.alertTag === 'ATRASADO'
                                ? styles.alertAtrasado
                                : p.alertTag === 'PRAZO_PROXIMO'
                                ? styles.alertPrazoProximo
                                : p.alertTag === 'PENDENTE'
                                ? styles.alertPendente
                                : styles.alertEmDia
                            }`}
                          >
                            {p.alertTag === 'ATRASADO'
                              ? 'Atrasado'
                              : p.alertTag === 'PRAZO_PROXIMO'
                              ? 'Prazo Próximo'
                              : p.alertTag === 'PENDENTE'
                              ? 'Pendente'
                              : 'Em Dia'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </section>

          {/* Painel 3: Aprovações Pendentes */}
          <section className={styles.panel} data-testid="pending-approvals-panel-table">
            <div className={styles.panelHeader}>
              <div className={styles.panelTitleGroup}>
                <h2>Aprovações Pendentes</h2>
              </div>
              <span
                className={`${styles.counterBadge} ${
                  data.pendingApprovals.length > 0 ? styles.counterBadgeWarning : ''
                }`}
              >
                {data.pendingApprovals.length}
              </span>
            </div>
            <p className={styles.panelDescription}>
              Plantas submetidas pelos expositores aguardando análise técnica e aprovação pelo Administrador.
            </p>

            <div className={styles.tableWrap}>
              {data.pendingApprovals.length === 0 ? (
                <div className={styles.empty}>Nenhuma planta pendente de análise no momento.</div>
              ) : (
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Empresa</th>
                      <th>Arquivo</th>
                      <th>Área (m²)</th>
                      <th>Enviado Em</th>
                      <th>Ação</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.pendingApprovals.map((app) => (
                      <tr key={app.projectId}>
                        <td>
                          <strong>{app.companyName}</strong>
                        </td>
                        <td>{app.pdfOriginalFilename || 'planta.pdf'}</td>
                        <td>{app.boothAreaM2 ? `${app.boothAreaM2} m²` : '-'}</td>
                        <td>{formatDate(app.pdfUploadedAt)}</td>
                        <td>
                          <button
                            type="button"
                            className={styles.actionShortcut}
                            onClick={() => onOpenPdfReview?.(app.projectId)}
                          >
                            Analisar Planta →
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
