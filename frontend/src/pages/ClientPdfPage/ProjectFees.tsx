import { useCallback, useEffect, useState } from 'react';
import { ApiRequestError, generateFeePayment, getFeePayment, getProjectFees } from '../../services/api';
import { FeeQuantityInput } from '../../components/FeeQuantityInput/FeeQuantityInput';
import { BoletoDialog, ConfirmPaymentDialog } from '../../components/FeePayment/FeePaymentDialogs';
import { FEE_TYPE_LABELS, canGeneratePayment, feeStatusLabel } from '../../constants/feeStatus';
import { formatDate } from '../../utils/date';
import type { CalculatedFee, FeePayment, ProjectFee } from '../../types';
import styles from './ProjectFees.module.scss';

interface Props {
  projectId: string;
}

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const number = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });

/** Dados que o input de quantidade (US-06) precisa, no formato devolvido por PUT .../quantity. */
function toProjectFee(fee: CalculatedFee): ProjectFee {
  return {
    id: fee.projectFeeId,
    feeId: fee.feeId,
    name: fee.name,
    description: fee.description,
    type: fee.type,
    measurementUnit: fee.measurementUnit,
    quantity: fee.quantity,
    status: fee.status,
    quantityUpdatedAt: null,
    quantityEditable: fee.quantityEditable,
  };
}

function valueText(fee: CalculatedFee) {
  if (fee.calculatedValue !== null) return currency.format(fee.calculatedValue);
  if (fee.status === 'AGUARDANDO_QUANTIDADE') return 'Informe a quantidade';
  if (fee.status === 'AGUARDANDO_COTACAO') return 'Aguardando cotação do administrador';
  return 'Valor indisponível';
}

/** US-05/US-06: taxas do projeto aprovado, com valor calculado ou cotado e quantidade das variáveis. */
export function ProjectFees({ projectId }: Props) {
  const [fees, setFees] = useState<CalculatedFee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // US-11: confirmação da geração e visualização do boleto.
  const [confirming, setConfirming] = useState<CalculatedFee | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [viewing, setViewing] = useState<CalculatedFee | null>(null);
  const [boleto, setBoleto] = useState<FeePayment | null>(null);
  const [boletoLoading, setBoletoLoading] = useState(false);
  const [boletoError, setBoletoError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setError(null);
      setFees(await getProjectFees(projectId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar taxas.');
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  const openBoleto = useCallback(
    async (fee: CalculatedFee) => {
      setViewing(fee);
      setBoleto(null);
      setBoletoError(null);
      setBoletoLoading(true);
      try {
        setBoleto(await getFeePayment(projectId, fee.projectFeeId));
      } catch (err) {
        setBoletoError(err instanceof Error ? err.message : 'Não foi possível obter o boleto.');
      } finally {
        setBoletoLoading(false);
      }
    },
    [projectId],
  );

  async function confirmPayment() {
    if (!confirming || submitting) return;
    const fee = confirming;
    setSubmitting(true);
    setPayError(null);
    try {
      await generateFeePayment(projectId, fee.projectFeeId);
      setConfirming(null);
      await load();
      void openBoleto(fee);
    } catch (err) {
      setPayError(err instanceof Error ? err.message : 'Não foi possível gerar o pagamento.');
      // Conflito: o estado da taxa mudou (ex.: outro clique já gerou). Atualiza a lista.
      if (err instanceof ApiRequestError && err.status === 409) void load();
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <div>Carregando taxas...</div>;

  return (
    <section className={styles.container}>
      <h2 className={styles.title}>Taxas e Pagamentos</h2>
      {error && <div className={styles.alertDanger} role="alert">{error}</div>}
      {!error && fees.length === 0 && <div>Nenhuma taxa vinculada a este projeto.</div>}
      <div className={styles.feeList}>
        {fees.map((fee) => (
          <div key={fee.projectFeeId} className={styles.feeItem}>
            <div className={styles.info}>
              <h3>{fee.name}</h3>
              {fee.description && <p>{fee.description}</p>}
              <p>Tipo: {FEE_TYPE_LABELS[fee.type]}</p>
              <p>Status: {feeStatusLabel(fee.status)} | Prazo de pagamento: {formatDate(fee.paymentDeadline)}</p>
              {fee.type !== 'VARIAVEL' && fee.quantity !== null && (
                <p>Cobrança: {number.format(fee.quantity)} {fee.measurementUnit}</p>
              )}
            </div>

            <div className={styles.actions}>
              {fee.type === 'VARIAVEL' && (
                <FeeQuantityInput
                  key={`${fee.projectFeeId}-${fee.status}-${fee.quantity ?? ''}`}
                  compact
                  projectId={projectId}
                  fee={toProjectFee(fee)}
                  onUpdated={() => void load()}
                />
              )}
              <span className={styles.value}>{valueText(fee)}</span>
              {canGeneratePayment(fee.status, fee.calculatedValue) && (
                <button
                  type="button"
                  className={styles.payButton}
                  onClick={() => {
                    setPayError(null);
                    setConfirming(fee);
                  }}
                >
                  Gerar pagamento
                </button>
              )}
              {fee.status === 'PAGAMENTO_GERADO' && (
                <>
                  <button
                    type="button"
                    className={styles.payButton}
                    disabled
                    title="O pagamento desta taxa já foi gerado."
                  >
                    Gerar pagamento
                  </button>
                  <button type="button" className={styles.secondaryButton} onClick={() => void openBoleto(fee)}>
                    Ver boleto
                  </button>
                </>
              )}
            </div>
          </div>
        ))}
      </div>
      {confirming && confirming.calculatedValue !== null && (
        <ConfirmPaymentDialog
          feeName={confirming.name}
          amount={confirming.calculatedValue}
          submitting={submitting}
          error={payError}
          onConfirm={() => void confirmPayment()}
          onCancel={() => setConfirming(null)}
        />
      )}
      {viewing && (
        <BoletoDialog
          feeName={viewing.name}
          payment={boleto}
          loading={boletoLoading}
          error={boletoError}
          onClose={() => setViewing(null)}
        />
      )}
    </section>
  );
}
