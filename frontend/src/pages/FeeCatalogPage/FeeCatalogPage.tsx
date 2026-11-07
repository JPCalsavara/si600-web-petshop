import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { deactivateFee, getFee, listFees } from '../../services/api';
import { FeeForm } from '../../components/FeeForm/FeeForm';
import type { Fee, FeeType } from '../../types';
import styles from './FeeCatalogPage.module.scss';

const TYPE_LABELS: Record<FeeType, string> = {
  FIXA: 'Fixa',
  POR_METRAGEM: 'Por metragem',
  VARIAVEL: 'Variável',
};

const PAGE_SIZE = 10;

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

function configuration(fee: Fee) {
  if (fee.type === 'VARIAVEL') return `${fee.measurementUnit} — valor a definir`;
  if (fee.type === 'FIXA') {
    return fee.amount === null ? 'Valor indisponível' : currency.format(fee.amount);
  }
  if (fee.areaPricingMode === 'VALOR_POR_M2') {
    return fee.amountPerM2 === null
      ? 'Valor indisponível'
      : `${currency.format(fee.amountPerM2)} por m²`;
  }
  return `1 unidade a cada ${fee.areaPerUnitM2?.toLocaleString('pt-BR')} m²; ${
    fee.unitAmount === null ? 'valor indisponível' : currency.format(fee.unitAmount)
  } por unidade`;
}

export function FeeCatalogPage() {
  const [fees, setFees] = useState<Fee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [form, setForm] = useState<{ fee?: Fee } | null>(null);
  const [selected, setSelected] = useState<Fee | null>(null);
  const [pending, setPending] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const dialog = useRef<HTMLDialogElement>(null);

  const load = useCallback(async (focusName?: string) => {
    setLoading(true);
    setError(null);
    try {
      const data = await listFees();
      setFees(data);
      const targetName =
        focusName ??
        (typeof window !== 'undefined'
          ? window.sessionStorage.getItem('LAST_SAVED_FEE_NAME') ?? undefined
          : undefined);
      if (targetName) {
        const idx = data.findIndex((item) => item.name === targetName);
        if (idx >= 0) {
          setCurrentPage(Math.floor(idx / PAGE_SIZE) + 1);
        }
      }
    } catch {
      setError('Não foi possível carregar o catálogo. Verifique sua conexão e tente novamente.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (selected) dialog.current?.showModal();
  }, [selected]);

  const totalPages = Math.max(1, Math.ceil(fees.length / PAGE_SIZE));
  const safeCurrentPage = Math.min(currentPage, totalPages);

  const pagedFees = useMemo(() => {
    const start = (safeCurrentPage - 1) * PAGE_SIZE;
    return fees.slice(start, start + PAGE_SIZE);
  }, [fees, safeCurrentPage]);

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
          Nova Taxa
        </button>
      </header>

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
        ) : fees.length === 0 ? (
          <p>Nenhuma taxa cadastrada.</p>
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
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {pagedFees.map((fee) => (
                  <tr key={fee.id}>
                    <td data-label="Nome">
                      <strong>{fee.name}</strong>
                    </td>
                    <td data-label="Descrição">{fee.description || '—'}</td>
                    <td data-label="Tipo">{TYPE_LABELS[fee.type]}</td>
                    <td data-label="Configuração / valor">{configuration(fee)}</td>
                    <td data-label="Status">
                      <span className={fee.active ? styles.active : styles.inactive}>
                        {fee.active ? 'Ativa' : 'Inativa'}
                      </span>
                    </td>
                    <td data-label="Ações">
                      <div className={styles.actions}>
                        <button
                          type="button"
                          disabled={pending}
                          onClick={() => void edit(fee)}
                          aria-label={`Editar ${fee.name}`}
                        >
                          Editar
                        </button>
                        {fee.active && (
                          <button
                            type="button"
                            disabled={pending}
                            onClick={() => setSelected(fee)}
                            aria-label={`Desativar ${fee.name}`}
                          >
                            Desativar
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
                  <strong>{Math.min(safeCurrentPage * PAGE_SIZE, fees.length)}</strong> de{' '}
                  <strong>{fees.length}</strong> taxas)
                </div>
                <div className={styles.paginationControls}>
                  <button
                    type="button"
                    className={styles.pageButton}
                    onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
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
                    onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
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
