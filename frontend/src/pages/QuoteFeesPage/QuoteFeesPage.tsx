import { useEffect, useState } from 'react';
import { getQuoteableFees, quoteProjectFee } from '../../services/api';
import { parseQuoteAmount } from '../../services/feeQuote';
import { feeStatusLabel } from '../../constants/feeStatus';
import type { QuoteableProjectFee } from '../../services/api';
import styles from './QuoteFeesPage.module.scss';

function money(value: number | null) {
  return value == null
    ? '—'
    : new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
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
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function save(fee: QuoteableProjectFee) {
    const parsed = parseQuoteAmount(values[fee.projectFeeId] ?? fee.quotedAmount?.toString() ?? '');
    if (!parsed.ok) {
      setError(parsed.error);
      return;
    }
    const amount = parsed.value;
    try {
      setBusy((old) => ({ ...old, [fee.projectFeeId]: true }));
      setError(null);
      setMessage(null);
      await quoteProjectFee(fee.projectId, fee.projectFeeId, amount);
      setMessage(`Cotação de "${fee.feeName}" (${fee.projectName}) salva com sucesso.`);
      setValues((old) => {
        const next = { ...old };
        delete next[fee.projectFeeId];
        return next;
      });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível salvar a cotação.');
    } finally {
      setBusy((old) => ({ ...old, [fee.projectFeeId]: false }));
    }
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Cotação de taxas</h1>
          <p className={styles.subtitle}>
            Informe o valor das taxas variáveis cuja quantidade o cliente já enviou. Enquanto o pagamento não for gerado, você pode recotar; se o cliente alterar a quantidade, a taxa volta para esta lista sem valor.
          </p>
        </div>
        {!loading && (
          <span className={styles.countBadge}>
            {fees.length} {fees.length === 1 ? 'taxa para cotação' : 'taxas para cotação'}
          </span>
        )}
      </header>

      {error && (
        <div role="alert" className={styles.alertError}>
          {error}
        </div>
      )}
      {message && (
        <div role="status" className={styles.alertSuccess}>
          {message}
        </div>
      )}

      {loading ? (
        <div className={styles.loadingCard}>
          <p>Carregando cotações...</p>
        </div>
      ) : fees.length === 0 ? (
        <div className={styles.emptyCard}>
          <p>Não há taxas aguardando cotação ou disponíveis para recotação.</p>
        </div>
      ) : (
        <div className={styles.grid}>
          {fees.map((fee) => (
            <article key={fee.projectFeeId} className={styles.card}>
              <div className={styles.cardHeader}>
                <h2 className={styles.feeTitle}>{fee.feeName}</h2>
                <span
                  className={`${styles.statusBadge} ${
                    fee.status === 'COTADA' ? styles.statusQuoted : styles.statusPending
                  }`}
                >
                  {fee.status === 'COTADA' ? 'Cotada (pode recotar)' : feeStatusLabel(fee.status)}
                </span>
              </div>

              <div className={styles.metaList}>
                <p className={styles.metaRow}>
                  <span>
                    <strong>Projeto:</strong> {fee.projectName}
                  </span>
                  <span>
                    <strong>Cliente:</strong> {fee.clientEmail ?? '—'}
                  </span>
                </p>
                <p className={styles.metaRow}>
                  <span>
                    <strong>Quantidade:</strong> {fee.quantity ?? '—'} {fee.measurementUnit ?? ''}
                  </span>
                  <span>
                    <strong>Valor atual:</strong>{' '}
                    <span className={styles.currentAmount}>{money(fee.quotedAmount)}</span>
                  </span>
                </p>
                {fee.quotedBy && (
                  <p className={styles.metaRow}>
                    <span>
                      <strong>Última cotação:</strong> {fee.quotedBy}
                    </span>
                    <span>
                      {fee.quotedAt ? new Date(fee.quotedAt).toLocaleString('pt-BR') : ''}
                    </span>
                  </p>
                )}
              </div>

              <div className={styles.formFooter}>
                <label className={styles.inputLabel}>
                  Novo valor (R$)
                  <input
                    type="number"
                    inputMode="decimal"
                    min="0.01"
                    step="0.01"
                    className={styles.input}
                    placeholder="0,00"
                    value={values[fee.projectFeeId] ?? (fee.quotedAmount?.toString() ?? '')}
                    onChange={(e) =>
                      setValues((old) => ({ ...old, [fee.projectFeeId]: e.target.value }))
                    }
                  />
                </label>
                <button
                  type="button"
                  className={styles.saveButton}
                  disabled={busy[fee.projectFeeId]}
                  onClick={() => void save(fee)}
                >
                  {busy[fee.projectFeeId]
                    ? 'Salvando...'
                    : fee.status === 'COTADA'
                      ? 'Salvar recotação'
                      : 'Salvar cotação'}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
