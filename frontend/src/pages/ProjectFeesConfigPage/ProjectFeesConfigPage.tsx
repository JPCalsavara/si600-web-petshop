import { useEffect, useState, useMemo } from 'react';
import { configureProjectFees, getProjectFeeConfiguration, listFees } from '../../services/api';
import { FEE_TYPE_LABELS, feeStatusLabel } from '../../constants/feeStatus';
import type { Fee } from '../../types';
import type { ProjectFeeConfiguration } from '../../services/api';
import styles from './ProjectFeesConfigPage.module.scss';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const PAGE_SIZE = 10;

/** Uma taxa só pode sair do projeto antes de ter quantidade, cotação ou pagamento (regra do backend). */
export function isLocked(link: ProjectFeeConfiguration): boolean {
  return link.quantity !== null || link.quotedAmount !== null
    || link.status === 'COTADA' || link.status === 'PAGAMENTO_GERADO';
}

function formatFeePrice(fee: Fee): string {
  if (fee.type === 'FIXA') {
    return currency.format(fee.amount ?? 0);
  }
  if (fee.type === 'POR_METRAGEM') {
    return `${currency.format(fee.amountPerM2 ?? 0)} / m²`;
  }
  return `Cotada por ${fee.measurementUnit ?? 'unidade'}`;
}

function formatTypeLabel(type: string): string {
  return FEE_TYPE_LABELS[type as keyof typeof FEE_TYPE_LABELS] ?? type;
}

function getFeeTypeBadgeClass(type: Fee['type']): string {
  switch (type) {
    case 'FIXA':
      return styles.feeTypeFixa;
    case 'POR_METRAGEM':
      return styles.feeTypeMetragem;
    case 'VARIAVEL':
      return styles.feeTypeVariavel;
    default:
      return '';
  }
}

interface ProjectFeesConfigPageProps {
  projectId: string;
  projectName: string;
  onBack: () => void;
}

export function ProjectFeesConfigPage({ projectId, projectName, onBack }: ProjectFeesConfigPageProps) {
  const [catalog, setCatalog] = useState<Fee[]>([]);
  const [current, setCurrent] = useState<ProjectFeeConfiguration[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);

  function apply(fees: Fee[], links: ProjectFeeConfiguration[]) {
    const linked = new Set(links.map(link => link.feeId));
    // Taxas desativadas depois do vínculo continuam listadas para o Admin ver que ainda estão no projeto.
    setCatalog(fees.filter(fee => fee.active || linked.has(fee.id)));
    setCurrent(links);
    setSelected(links.map(link => link.feeId));
  }

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [fees, links] = await Promise.all([listFees(), getProjectFeeConfiguration(projectId)]);
      apply(fees, links);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível carregar a configuração.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [projectId]);

  async function save() {
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const links = await configureProjectFees(projectId, selected);
      setCurrent(links);
      setSelected(links.map(link => link.feeId));
      setNotice('Configuração de taxas salva com sucesso.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível salvar a configuração.');
    } finally {
      setSaving(false);
    }
  }

  const lockedFeeIds = useMemo(() => {
    return new Set(current.filter(isLocked).map(link => link.feeId));
  }, [current]);

  // Paginação de 10 taxas por página
  const totalPages = Math.max(1, Math.ceil(catalog.length / PAGE_SIZE));
  const safeCurrentPage = Math.min(currentPage, totalPages);

  const pagedCatalog = useMemo(() => {
    const start = (safeCurrentPage - 1) * PAGE_SIZE;
    return catalog.slice(start, start + PAGE_SIZE);
  }, [catalog, safeCurrentPage]);

  function toggleFee(feeId: string) {
    if (lockedFeeIds.has(feeId)) return;
    setSelected(prev =>
      prev.includes(feeId) ? prev.filter(id => id !== feeId) : [...prev, feeId]
    );
  }

  return (
    <section className={styles.page} data-testid="project-fees-config-page">
      <button type="button" className={styles.backButton} onClick={onBack}>
        ← Voltar aos projetos
      </button>

      <header className={styles.header}>
        <div className={styles.titleArea}>
          <h1 className={styles.title}>Configurar Taxas do Projeto</h1>
          <span className={styles.projectBadge}>Projeto: {projectName}</span>
          <p className={styles.subtitle}>
            Selecione as taxas do catálogo que se aplicam a este projeto. Taxas fixas e por metragem têm o valor calculado automaticamente; as variáveis são cotadas na tela de cotação após o cliente informar a quantidade.
          </p>
        </div>

        <button
          type="button"
          className={styles.saveButtonHeader}
          disabled={saving || loading}
          onClick={() => void save()}
        >
          {saving ? 'Salvando...' : 'Salvar configuração'}
        </button>
      </header>

      {error && (
        <div role="alert" className={styles.alertError}>
          {error}
        </div>
      )}

      {notice && (
        <div role="status" className={styles.alertSuccess}>
          {notice}
        </div>
      )}

      {loading ? (
        <div className={styles.loadingContainer}>
          <div className={styles.spinner} />
          <p>Carregando catálogo e vínculos de taxas...</p>
        </div>
      ) : (
        <>
          <div className={styles.sectionTitleRow}>
            <h2 className={styles.sectionTitle}>Catálogo de Taxas Disponíveis</h2>
            <span className={styles.selectionCount}>
              <strong>{selected.length}</strong> taxa{selected.length === 1 ? '' : 's'} selecionada{selected.length === 1 ? '' : 's'}
            </span>
          </div>

          {catalog.length === 0 ? (
            <p className={styles.subtitle}>Não há taxas ativas no catálogo.</p>
          ) : (
            <>
              {/* Grid com 2 colunas de taxas */}
              <div className={styles.feesGrid}>
                {pagedCatalog.map(fee => {
                  const isSelected = selected.includes(fee.id);
                  const isFeeLocked = lockedFeeIds.has(fee.id);

                  return (
                    <div
                      key={fee.id}
                      className={`${styles.feeCard} ${isSelected ? styles.feeCardSelected : ''} ${
                        isFeeLocked ? styles.cardDisabled : ''
                      }`}
                      onClick={() => toggleFee(fee.id)}
                    >
                      <div className={styles.checkboxWrap}>
                        <input
                          type="checkbox"
                          className={styles.checkboxInput}
                          checked={isSelected}
                          disabled={isFeeLocked}
                          onChange={e => {
                            e.stopPropagation();
                            toggleFee(fee.id);
                          }}
                        />
                      </div>
                      <div className={styles.cardContent}>
                        <div className={styles.cardHeaderRow}>
                          <span className={styles.feeName}>{fee.name}</span>
                          <span className={`${styles.feeTypeBadge} ${getFeeTypeBadgeClass(fee.type)}`}>
                            {FEE_TYPE_LABELS[fee.type]}
                          </span>
                        </div>
                        <div className={styles.feePricing}>{formatFeePrice(fee)}</div>
                        <p className={styles.feeDescription}>{fee.description || 'Sem descrição cadastrada'}</p>
                        {!fee.active && (
                          <span className={styles.inactiveNotice}>Taxa desativada no catálogo geral</span>
                        )}
                        {isFeeLocked && (
                          <span className={styles.lockedNotice}>
                            Taxa bloqueada: possui quantidade, cotação ou pagamento
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Paginação de 10 taxas por página */}
              {totalPages > 1 && (
                <nav className={styles.pagination} aria-label="Paginação do catálogo de taxas">
                  <span className={styles.paginationInfo}>
                    Exibindo <strong>{(safeCurrentPage - 1) * PAGE_SIZE + 1}</strong> a{' '}
                    <strong>{Math.min(safeCurrentPage * PAGE_SIZE, catalog.length)}</strong> de{' '}
                    <strong>{catalog.length}</strong> taxas
                  </span>
                  <div className={styles.paginationControls}>
                    <button
                      type="button"
                      className={styles.pageButton}
                      disabled={safeCurrentPage === 1}
                      onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                    >
                      Anterior
                    </button>
                    <span className={styles.pageCounter}>
                      Página {safeCurrentPage} de {totalPages}
                    </span>
                    <button
                      type="button"
                      className={styles.pageButton}
                      disabled={safeCurrentPage === totalPages}
                      onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                    >
                      Próxima
                    </button>
                  </div>
                </nav>
              )}
            </>
          )}

          {/* Taxas já vinculadas */}
          {current.length > 0 && (
            <div className={styles.linkedSection}>
              <h2 className={styles.sectionTitle}>
                Taxas Atualmente Vinculadas ao Projeto ({current.length})
              </h2>
              <p className={styles.subtitle}>
                Taxas que já possuem quantidade preenchida pelo cliente, cotação ou pagamento não podem ser removidas.
              </p>
              <div className={styles.linkedList}>
                {current.map(link => (
                  <div key={link.projectFeeId} className={styles.linkedItem}>
                    <div className={styles.linkedInfo}>
                      <span className={styles.linkedName}>{link.name}</span>
                      <span className={styles.linkedDetails}>
                        {link.quantity !== null && (
                          <>Quantidade: {link.quantity.toLocaleString('pt-BR')} {link.measurementUnit ?? ''} • </>
                        )}
                        {link.quotedAmount !== null && (
                          <>Cotada em: {currency.format(link.quotedAmount)} • </>
                        )}
                        Tipo: {formatTypeLabel(link.type)}
                      </span>
                    </div>
                    <span className={styles.linkedStatusBadge}>
                      {feeStatusLabel(link.status)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className={styles.bottomActionRow}>
            <button
              type="button"
              className={styles.saveButtonHeader}
              disabled={saving || loading}
              onClick={() => void save()}
            >
              {saving ? 'Salvando...' : 'Salvar configuração'}
            </button>
          </div>
        </>
      )}
    </section>
  );
}
