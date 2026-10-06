import { useCallback, useEffect, useRef, useState } from 'react';
import { deactivateFee, getFee, listFees } from '../../services/api';
import { FeeForm } from '../../components/FeeForm/FeeForm';
import type { Fee, FeeType } from '../../types';
import styles from './FeeCatalogPage.module.scss';

const TYPE_LABELS: Record<FeeType, string> = { FIXA: 'Fixa', POR_METRAGEM: 'Por metragem', VARIAVEL: 'Variável' };
const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
function configuration(fee: Fee) {
  if (fee.type === 'VARIAVEL') return `${fee.measurementUnit} — valor a definir`;
  if (fee.type === 'FIXA') return fee.amount === null ? 'Valor indisponível' : currency.format(fee.amount);
  if (fee.areaPricingMode === 'VALOR_POR_M2') return fee.amountPerM2 === null ? 'Valor indisponível' : `${currency.format(fee.amountPerM2)} por m²`;
  return `1 unidade a cada ${fee.areaPerUnitM2?.toLocaleString('pt-BR')} m²; ${fee.unitAmount === null ? 'valor indisponível' : currency.format(fee.unitAmount)} por unidade`;
}

export function FeeCatalogPage() {
  const [fees, setFees] = useState<Fee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [form, setForm] = useState<{ fee?: Fee } | null>(null);
  const [selected, setSelected] = useState<Fee | null>(null);
  const [pending, setPending] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try { setFees(await listFees()); }
    catch { setError('Não foi possível carregar o catálogo. Verifique sua conexão e tente novamente.'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => { if (selected) dialog.current?.showModal(); }, [selected]);

  async function edit(fee: Fee) {
    setPending(true); setError(null); setNotice(null);
    try { setForm({ fee: await getFee(fee.id) }); }
    catch { setError('Não foi possível carregar a taxa para edição. Tente novamente.'); }
    finally { setPending(false); }
  }

  async function deactivate() {
    if (!selected || pending) return;
    setPending(true); setError(null); setNotice(null);
    try {
      await deactivateFee(selected.id);
      setSelected(null);
      setNotice('Taxa desativada.');
      await load();
    } catch { setError('Não foi possível desativar a taxa. Tente novamente.'); setSelected(null); }
    finally { setPending(false); }
  }

  if (form) return <FeeForm fee={form.fee} onCancel={() => setForm(null)} onSaved={() => {
    setForm(null); setNotice('Taxa salva.'); void load();
  }} />;

  return <section className={styles.page}>
    <header className={styles.header}>
      <h1>Catálogo de Taxas</h1>
      <button type="button" className={styles.add} disabled={pending} onClick={() => { setNotice(null); setForm({}); }}>Nova Taxa</button>
    </header>
    {notice && <p role="status" className={styles.success}>{notice}</p>}
    {error && <div role="alert" className={styles.alert}>{error} <button type="button" disabled={pending || loading} onClick={() => void load()}>Tentar novamente</button></div>}
    <div className={styles.panel} aria-busy={loading}>
      {loading ? <p role="status">Carregando taxas...</p> : fees.length === 0 ? <p>Nenhuma taxa cadastrada.</p> : <div className={styles.tableWrap}>
        <table className={styles.table}>
          <caption>Taxas cadastradas, ativas e inativas</caption>
          <thead><tr><th>Nome</th><th>Descrição</th><th>Tipo</th><th>Configuração / valor</th><th>Status</th><th>Ações</th></tr></thead>
          <tbody>{fees.map((fee) => <tr key={fee.id}>
            <td data-label="Nome">{fee.name}</td><td data-label="Descrição">{fee.description || '—'}</td><td data-label="Tipo">{TYPE_LABELS[fee.type]}</td><td data-label="Configuração / valor">{configuration(fee)}</td>
            <td data-label="Status"><span className={fee.active ? styles.active : styles.inactive}>{fee.active ? 'Ativa' : 'Inativa'}</span></td>
            <td data-label="Ações"><div className={styles.actions}>
              <button type="button" disabled={pending} onClick={() => void edit(fee)} aria-label={`Editar ${fee.name}`}>Editar</button>
              {fee.active && <button type="button" disabled={pending} onClick={() => setSelected(fee)} aria-label={`Desativar ${fee.name}`}>Desativar</button>}
            </div></td>
          </tr>)}</tbody>
        </table>
      </div>}
    </div>
    {selected && <dialog ref={dialog} className={styles.dialog} aria-labelledby="deactivate-title" onCancel={(event) => {
      if (pending) event.preventDefault(); else setSelected(null);
    }}>
      <h2 id="deactivate-title">Desativar taxa?</h2>
      <p>Deseja desativar <strong>{selected.name}</strong>?</p>
      <p>A taxa continuará visível no catálogo como inativa.</p>
      <div className={styles.actions}>
        <button type="button" autoFocus disabled={pending} onClick={() => setSelected(null)}>Cancelar</button>
        <button type="button" disabled={pending} onClick={() => void deactivate()}>{pending ? 'Desativando...' : 'Confirmar desativação'}</button>
      </div>
    </dialog>}
  </section>;
}
