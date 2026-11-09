import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { deactivateFee, getFee, listFees } from '../../services/api';
import { FeeForm } from '../../components/FeeForm/FeeForm';
import { usePaginatedList } from '../../components/Pagination/Pagination';
import { FEE_TYPE_LABELS, formatFeePricing } from '../../constants/feeStatus';
import type { Fee, FeeType } from '../../types';
import styles from './FeeCatalogPage.module.scss';

const TYPE_LABELS: Record<FeeType, string> = FEE_TYPE_LABELS;

const FEE_TYPES: FeeType[] = ['FIXA', 'POR_METRAGEM', 'VARIAVEL'];

const PAGE_SIZE = 10;

function getTypeBadgeClass(type: FeeType): string {
  switch (type) {
    case 'FIXA':
      return styles.typeFixa;
    case 'POR_METRAGEM':
      return styles.typeMetragem;
    case 'VARIAVEL':
      return styles.typeVariavel;
  }
}

export function FeeCatalogPage() {
  const [fees, setFees] = useState<Fee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [form, setForm] = useState<{ fee?: Fee } | null>(null);
  const [selected, setSelected] = useState<Fee | null>(null);
  const [pending, setPending] = useState(false);

  // Filtros
  const [nameSearch, setNameSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<FeeType | ''>('');

  const dialog = useRef<HTMLDialogElement>(null);

  const filteredFees = useMemo(() => {
    return fees.filter((fee) => {
      if (nameSearch.trim()) {
        const q = nameSearch.toLowerCase().trim();
        const matchName = fee.name.toLowerCase().includes(q);
        const matchDesc = (fee.description ?? '').toLowerCase().includes(q);
        if (!matchName && !matchDesc) return false;
      }
      if (typeFilter && fee.type !== typeFilter) {
        return false;
      }
      return true;
    });
  }, [fees, nameSearch, typeFilter]);

  const {
    currentPage: safeCurrentPage,
    totalPages,
    pagedItems: pagedFees,
    setCurrentPage,
    prevPage,
    nextPage,
    resetPage,
  } = usePaginatedList(filteredFees, PAGE_SIZE);

  const load = useCallback(async (focusName?: string) => {
    setLoading(true);
    setError(null);
    try {
      const data = await listFees();
      setFees(data);
      if (focusName) {
        const idx = data.findIndex((item) => item.name === focusName);
        if (idx >= 0) {
          setCurrentPage(Math.floor(idx / PAGE_SIZE) + 1);
        }
      }
    } catch {
      setError('Não foi possível carregar o catálogo. Verifique sua conexão e tente novamente.');
    } finally {
      setLoading(false);
    }
  }, [setCurrentPage]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (selected) dialog.current?.showModal();
  }, [selected]);

  const hasActiveFilters = Boolean(nameSearch.trim() || typeFilter);

  const clearFilters = () => {
    setNameSearch('');
    setTypeFilter('');
    resetPage();
  };

  async function edit(fee: Fee) {
    setPending(true);
    setError(null);
    setNotice(null);
    try {
      setForm({ fee: await getFee(fee.id) });
    } catch {
      setError('Não foi possível carregar a taxa para edição. Tente novamente.');
    } finally {
      setPending(false);
    }
  }

  async function deactivate() {
    if (!selected || pending) return;
    const deactivatedName = selected.name;
    setPending(true);
    setError(null);
    setNotice(null);
    try {
      await deactivateFee(selected.id);
      setSelected(null);
      setNotice('Taxa desativada.');
      await load(deactivatedName);
    } catch {
      setError('Não foi possível desativar a taxa. Tente novamente.');
      setSelected(null);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>Catálogo de Taxas</h1>
          <p className={styles.subtitle}>
            Gerencie as taxas fixas, por metragem e variáveis utilizadas nos projetos.
          </p>
        </div>
        <button
          type="button"
          className={styles.add}
          disabled={pending}
          onClick={() => {
            setNotice(null);
            setForm({});
          }}
        >
          + Nova Taxa
        </button>
      </header>

      {/* Barra de Filtros do Catálogo de Taxas */}
      <section className={styles.filtersSection} aria-label="Filtros do catálogo de taxas">
        <div className={styles.filtersGrid}>
          <div className={styles.filterGroup}>
            <label htmlFor="filter-fee-name" className={styles.filterLabel}>
              Nome da Taxa
            </label>
            <input
              id="filter-fee-name"
              type="text"
              className={styles.filterInput}
              placeholder="Buscar por nome ou descrição da taxa..."
              value={nameSearch}
              onChange={(e) => {
                setNameSearch(e.target.value);
                setCurrentPage(1);
              }}
              data-testid="input-filter-fee-name"
            />
          </div>

          <div className={styles.filterGroup}>
            <span className={styles.filterLabel}>Filtrar por Tipo</span>
            <div className={styles.typeFilterPills}>
              <button
                type="button"
                className={`${styles.typePill} ${typeFilter === '' ? styles.typePillAllActive : ''}`}
                onClick={() => {
                  setTypeFilter('');
                  setCurrentPage(1);
                }}
                data-testid="filter-type-ALL"
              >
                Todos ({fees.length})
              </button>
              {FEE_TYPES.map((t) => {
                const isActive = typeFilter === t;
                const count = fees.filter((f) => f.type === t).length;
                return (
                  <button
                    key={t}
                    type="button"
                    className={`${styles.typePill} ${getTypeBadgeClass(t)} ${
                      isActive ? styles.typePillActive : styles.typePillInactive
                    }`}
                    onClick={() => {
                      setTypeFilter(isActive ? '' : t);
                      setCurrentPage(1);
                    }}
                    data-testid={`filter-type-${t}`}
                  >
                    {TYPE_LABELS[t]} ({count})
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        <div className={styles.filterMetaRow}>
          <div className={styles.resultsCount}>
            Exibindo <strong>{filteredFees.length}</strong> de <strong>{fees.length}</strong> taxas cadastradas
            {hasActiveFilters && ' (filtros aplicados)'}
          </div>
          {hasActiveFilters && (
            <button
              type="button"
              className={styles.clearFilterButton}
              onClick={clearFilters}
              data-testid="btn-clear-fee-filters"
            >
              ✕ Limpar filtros
            </button>
          )}
        </div>
      </section>

      {notice && (
        <p role="status" className={styles.success}>
          {notice}
        </p>
      )}

      {error && (
        <div role="alert" className={styles.alert}>
          {error}{' '}
          <button type="button" disabled={pending || loading} onClick={() => void load()}>
            Tentar novamente
          </button>
        </div>
      )}

      <div className={styles.panel} aria-busy={loading}>
        {loading ? (
          <p role="status">Carregando taxas...</p>
        ) : filteredFees.length === 0 ? (
          <div className={styles.emptyState}>
            <p>
              {hasActiveFilters
                ? 'Nenhuma taxa encontrada para os filtros selecionados.'
                : 'Nenhuma taxa cadastrada.'}
            </p>
            {hasActiveFilters && (
              <button type="button" className={styles.clearFilterButton} onClick={clearFilters}>
                Limpar filtros
              </button>
            )}
          </div>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <caption>Taxas cadastradas, ativas e inativas</caption>
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Descrição</th>
                  <th>Tipo</th>
                  <th>Configuração / valor</th>
                  <th>Status</th>
                  <th className={styles.actionsHeader}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {pagedFees.map((fee) => (
                  <tr key={fee.id}>
                    <td data-label="Nome">
                      <strong>{fee.name}</strong>
                    </td>
                    <td data-label="Descrição">{fee.description || '—'}</td>
                    <td data-label="Tipo">
                      <span className={`${styles.typeBadge} ${getTypeBadgeClass(fee.type)}`}>
                        {TYPE_LABELS[fee.type]}
                      </span>
                    </td>
                    <td data-label="Configuração / valor">{formatFeePricing(fee)}</td>
                    <td data-label="Status">
                      <span className={fee.active ? styles.active : styles.inactive}>
                        {fee.active ? 'Ativa' : 'Inativa'}
                      </span>
                    </td>
                    <td data-label="Ações" className={styles.actionsCell}>
                      <div className={styles.actions}>
                        <button
                          type="button"
                          className={styles.editIconBtn}
                          disabled={pending}
                          onClick={() => void edit(fee)}
                          aria-label={`Editar ${fee.name}`}
                          title={`Editar ${fee.name}`}
                        >
                          <svg
                            viewBox="0 0 24 24"
                            width="16"
                            height="16"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2.2"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            aria-hidden="true"
                          >
                            <path d="M12 20h9" />
                            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
                          </svg>
                          <span className={styles.srOnly}>Editar</span>
                        </button>
                        {fee.active && (
                          <button
                            type="button"
                            className={styles.deleteIconBtn}
                            disabled={pending}
                            onClick={() => setSelected(fee)}
                            aria-label={`Desativar ${fee.name}`}
                            title={`Desativar ${fee.name}`}
                          >
                            <svg
                              viewBox="0 0 24 24"
                              width="16"
                              height="16"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="2.2"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              aria-hidden="true"
                            >
                              <polyline points="3 6 5 6 21 6" />
                              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                              <line x1="10" y1="11" x2="10" y2="17" />
                              <line x1="14" y1="11" x2="14" y2="17" />
                            </svg>
                            <span className={styles.srOnly}>Desativar</span>
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {totalPages > 1 && (
              <nav
                className={styles.pagination}
                aria-label="Paginação do catálogo de taxas"
                data-testid="fees-pagination"
              >
                <div className={styles.paginationInfo}>
                  Página <strong>{safeCurrentPage}</strong> de <strong>{totalPages}</strong> (Exibindo{' '}
                  <strong>{(safeCurrentPage - 1) * PAGE_SIZE + 1}</strong> a{' '}
                  <strong>{Math.min(safeCurrentPage * PAGE_SIZE, filteredFees.length)}</strong> de{' '}
                  <strong>{filteredFees.length}</strong> taxas)
                </div>
                <div className={styles.paginationControls}>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={prevPage}
                    disabled={safeCurrentPage <= 1}
                    data-testid="btn-prev-fees"
                  >
                    ← Anterior
                  </button>
                  <span className={styles.pageCounter}>
                    {safeCurrentPage} / {totalPages}
                  </span>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={nextPage}
                    disabled={safeCurrentPage >= totalPages}
                    data-testid="btn-next-fees"
                  >
                    Próximo →
                  </button>
                </div>
              </nav>
            )}
          </div>
        )}
      </div>

      {form && (
        <div
          className={styles.modalOverlay}
          role="dialog"
          aria-modal="true"
          data-testid="fee-form-modal"
        >
          <div className={styles.modalCard}>
            <FeeForm
              fee={form.fee}
              onCancel={() => setForm(null)}
              onSaved={(savedName) => {
                setForm(null);
                setNotice('Taxa salva.');
                void load(savedName);
              }}
            />
          </div>
        </div>
      )}

      {selected && (
        <dialog
          ref={dialog}
          className={styles.dialog}
          aria-labelledby="deactivate-title"
          onCancel={(event) => {
            if (pending) event.preventDefault();
            else setSelected(null);
          }}
        >
          <div className={styles.dialogHeader}>
            <h2 id="deactivate-title">Desativar taxa?</h2>
            <button
              type="button"
              autoFocus
              disabled={pending}
              className={styles.closeDialogBtn}
              onClick={() => setSelected(null)}
              aria-label="Cancelar"
              title="Cancelar"
            >
              <span aria-hidden="true">✕</span>
              <span className={styles.srOnly}>Cancelar</span>
            </button>
          </div>
          <p>
            Deseja desativar <strong>{selected.name}</strong>?
          </p>
          <p>A taxa continuará visível no catálogo como inativa.</p>
          <div className={styles.dialogActions}>
            <button type="button" disabled={pending} onClick={() => void deactivate()}>
              {pending ? 'Desativando...' : 'Confirmar desativação'}
            </button>
          </div>
        </dialog>
      )}
    </section>
  );
}
