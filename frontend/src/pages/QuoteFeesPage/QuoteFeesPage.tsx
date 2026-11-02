import { useEffect, useState } from 'react';
import { getQuoteableFees, quoteProjectFee } from '../../services/api';
import { parseQuoteAmount } from '../../services/feeQuote';
import { feeStatusLabel } from '../../constants/feeStatus';
import type { QuoteableProjectFee } from '../../services/api';

function money(value: number | null) {
  return value == null ? '—' : new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}

export function QuoteFeesPage() {
  const [fees, setFees] = useState<QuoteableProjectFee[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function load() {
    try {
      setLoading(true);
      setError(null);
      setFees(await getQuoteableFees());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível carregar as cotações.');
    } finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, []);

  async function save(fee: QuoteableProjectFee) {
    const parsed = parseQuoteAmount(values[fee.projectFeeId] ?? fee.quotedAmount?.toString() ?? '');
    if (!parsed.ok) {
      setError(parsed.error);
      return;
    }
    const amount = parsed.value;
    try {
      setBusy(old => ({ ...old, [fee.projectFeeId]: true }));
      setError(null);
      setMessage(null);
      await quoteProjectFee(fee.projectId, fee.projectFeeId, amount);
      setMessage(`Cotação de "${fee.feeName}" (${fee.projectName}) salva com sucesso.`);
      setValues(old => { const next = { ...old }; delete next[fee.projectFeeId]; return next; });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível salvar a cotação.');
    } finally {
      setBusy(old => ({ ...old, [fee.projectFeeId]: false }));
    }
  }

  if (loading) return <section><h1>Cotação de taxas</h1><p>Carregando cotações...</p></section>;
  return <section>
    <h1>Cotação de taxas</h1>
    <p>Informe o valor das taxas variáveis cuja quantidade o cliente já enviou. Enquanto o pagamento não for gerado, você pode recotar; se o cliente alterar a quantidade, a taxa volta para esta lista sem valor.</p>
    {error && <div role="alert" style={{ padding: 12, margin: '12px 0', background: '#fee2e2' }}>{error}</div>}
    {message && <div role="status" style={{ padding: 12, margin: '12px 0', background: '#dcfce7' }}>{message}</div>}
    {fees.length === 0 ? <p>Não há taxas aguardando cotação ou disponíveis para recotação.</p> :
      <div style={{ display: 'grid', gap: 16 }}>
        {fees.map(fee => <article key={fee.projectFeeId} style={{ border: '1px solid #ddd', borderRadius: 8, padding: 16 }}>
          <h2>{fee.feeName}</h2>
          <p><strong>Projeto:</strong> {fee.projectName} · <strong>Cliente:</strong> {fee.clientEmail ?? '—'}</p>
          <p><strong>Quantidade:</strong> {fee.quantity ?? '—'} {fee.measurementUnit ?? ''}</p>
          <p><strong>Status:</strong> {fee.status === 'COTADA' ? 'Cotada (pode recotar)' : feeStatusLabel(fee.status)}</p>
          <p><strong>Valor atual:</strong> {money(fee.quotedAmount)}</p>
          {fee.quotedBy && <p><strong>Última cotação:</strong> {fee.quotedBy} · {fee.quotedAt ? new Date(fee.quotedAt).toLocaleString('pt-BR') : ''}</p>}
          <label style={{ display: 'block', marginTop: 12 }}>
            Novo valor (R$)
            <input type="number" inputMode="decimal" min="0.01" step="0.01" value={values[fee.projectFeeId] ?? (fee.quotedAmount?.toString() ?? '')}
              onChange={e => setValues(old => ({ ...old, [fee.projectFeeId]: e.target.value }))}
              style={{ display: 'block', marginTop: 4, padding: 8, maxWidth: 260, width: '100%' }} />
          </label>
          <button type="button" disabled={busy[fee.projectFeeId]} onClick={() => void save(fee)} style={{ marginTop: 12 }}>
            {busy[fee.projectFeeId] ? 'Salvando...' : fee.status === 'COTADA' ? 'Salvar recotação' : 'Salvar cotação'}
          </button>
        </article>)}
      </div>}
  </section>;
}
