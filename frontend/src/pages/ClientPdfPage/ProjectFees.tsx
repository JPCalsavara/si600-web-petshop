import { useCallback, useEffect, useState } from 'react';
import { getProjectFees } from '../../services/api';
import { FeeQuantityInput } from '../../components/FeeQuantityInput/FeeQuantityInput';
import { FEE_TYPE_LABELS, canGeneratePayment, feeStatusLabel } from '../../constants/feeStatus';
import type { CalculatedFee, ProjectFee } from '../../types';
import styles from './ProjectFees.module.scss';

interface Props {
  projectId: string;
}

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const number = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });

function formatDate(value: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleDateString('pt-BR');
}

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
                  onClick={() => alert('A geração de boleto/PIX será implementada em breve.')}
                >
                  Gerar pagamento
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
