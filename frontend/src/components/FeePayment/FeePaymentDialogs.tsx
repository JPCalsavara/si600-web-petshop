import { useState } from 'react';
import { isSafeBoletoUrl } from '../../constants/feeStatus';
import { formatDateTime } from '../../utils/date';
import type { FeePayment } from '../../types';
import styles from './FeePaymentDialogs.module.scss';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

interface ConfirmProps {
  feeName: string;
  amount: number;
  submitting: boolean;
  error: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

/** US-11: resumo exibido antes de gerar o boleto, para o Cliente conferir taxa e valor. */
export function ConfirmPaymentDialog({ feeName, amount, submitting, error, onConfirm, onCancel }: ConfirmProps) {
  return (
    <div className={styles.backdrop}>
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby="confirm-payment-title">
        <h3 id="confirm-payment-title">Gerar pagamento</h3>
        <p>Confira o resumo antes de gerar o boleto.</p>
        <dl className={styles.summary}>
          <dt>Taxa</dt>
          <dd>{feeName}</dd>
          <dt>Valor</dt>
          <dd>{currency.format(amount)}</dd>
        </dl>
        <p className={styles.hint}>Depois de gerado, o valor desta taxa não poderá mais ser alterado.</p>
        {error && (
          <div className={styles.error} role="alert">
            {error}
          </div>
        )}
        <div className={styles.buttons}>
          <button type="button" className={styles.secondary} onClick={onCancel} disabled={submitting}>
            Cancelar
          </button>
          <button type="button" className={styles.primary} onClick={onConfirm} disabled={submitting}>
            {submitting ? 'Gerando...' : 'Confirmar e gerar boleto'}
          </button>
        </div>
      </div>
    </div>
  );
}

interface BoletoProps {
  feeName: string;
  payment: FeePayment | null;
  loading: boolean;
  error: string | null;
  onClose: () => void;
}

/** US-11: boleto da taxa, com link para abrir/baixar e a linha digitável para copiar. */
export function BoletoDialog({ feeName, payment, loading, error, onClose }: BoletoProps) {
  const [copied, setCopied] = useState(false);

  async function copyLine(line: string) {
    try {
      await navigator.clipboard.writeText(line);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className={styles.backdrop}>
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby="boleto-title">
        <h3 id="boleto-title">Boleto - {feeName}</h3>
        {loading && <p>Carregando boleto...</p>}
        {error && (
          <div className={styles.error} role="alert">
            {error}
          </div>
        )}
        {payment && (
          <>
            <dl className={styles.summary}>
              <dt>Valor</dt>
              <dd>{currency.format(payment.amount)}</dd>
              {payment.expiresAt && (
                <>
                  <dt>Vencimento</dt>
                  <dd>{formatDateTime(payment.expiresAt)}</dd>
                </>
              )}
            </dl>
            {isSafeBoletoUrl(payment.ticketUrl) ? (
              <a className={styles.link} href={payment.ticketUrl} target="_blank" rel="noopener noreferrer">
                Abrir ou baixar boleto
              </a>
            ) : (
              <p className={styles.hint}>O link do boleto não está disponível. Use a linha digitável.</p>
            )}
            {payment.digitableLine && (
              <div className={styles.line}>
                <span>Linha digitável</span>
                <code data-testid="digitable-line">{payment.digitableLine}</code>
                <button type="button" className={styles.secondary} onClick={() => void copyLine(payment.digitableLine!)}>
                  {copied ? 'Copiado!' : 'Copiar código'}
                </button>
              </div>
            )}
          </>
        )}
        <div className={styles.buttons}>
          <button type="button" className={styles.secondary} onClick={onClose}>
            Fechar
          </button>
        </div>
      </div>
    </div>
  );
}
