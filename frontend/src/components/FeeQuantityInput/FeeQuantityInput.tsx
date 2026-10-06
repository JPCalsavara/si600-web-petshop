import { useState } from 'react';
import { submitFeeQuantity } from '../../services/api';
import { parseQuantity } from '../../services/feeQuantity';
import type { ProjectFee, ProjectFeeStatus } from '../../types';
import styles from './FeeQuantityInput.module.scss';

interface Props {
  projectId: string;
  /** Linha da taxa variável, vinda da lista de taxas do projeto (US-05). */
  fee: ProjectFee;
  /** Chamado com a taxa atualizada pela API, para a lista (US-05) refletir o novo status. */
  onUpdated?: (fee: ProjectFee) => void;
}

const STATUS_LABEL: Record<ProjectFeeStatus, string> = {
  AGUARDANDO_QUANTIDADE: 'Aguardando quantidade',
  AGUARDANDO_COTACAO: 'Aguardando cotação',
  COTADA: 'Cotada',
  PAGAMENTO_GERADO: 'Pagamento gerado',
};

/** US-06: input de quantidade de uma taxa variável (ex.: energia em kVA) e envio para cotação. */
export function FeeQuantityInput({ projectId, fee, onUpdated }: Props) {
  const [value, setValue] = useState(fee.quantity === null ? '' : String(fee.quantity));
  const [current, setCurrent] = useState(fee);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [saving, setSaving] = useState(false);
  const unit = current.measurementUnit ?? '';

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSuccess(false);
    const parsed = parseQuantity(value, current.measurementUnit);
    if (!parsed.ok) {
      setError(parsed.error);
      return;
    }
    setError(null);
    try {
      setSaving(true);
      const updated = await submitFeeQuantity(projectId, current.id, parsed.value);
      setCurrent(updated);
      setValue(updated.quantity === null ? '' : String(updated.quantity));
      setSuccess(true);
      onUpdated?.(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível enviar a quantidade.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className={styles.row} onSubmit={(event) => void handleSubmit(event)} noValidate>
      <div className={styles.info}>
        <strong>{current.name}</strong>
        <span className={`${styles.badge} ${styles[current.status]}`}>{STATUS_LABEL[current.status]}</span>
      </div>

      <label>
        Quantidade{unit && ` (${unit})`}
        <div className={styles.inputWrap}>
          <input
            type="text"
            inputMode="decimal"
            value={value}
            disabled={!current.quantityEditable || saving}
            onChange={(event) => setValue(event.target.value)}
            placeholder="Ex.: 10"
            aria-invalid={Boolean(error)}
          />
          {unit && <span className={styles.unit}>{unit}</span>}
        </div>
      </label>

      {current.quantityEditable ? (
        <button className={styles.primaryButton} disabled={saving}>
          {saving ? 'Enviando...' : 'Enviar para Cotação'}
        </button>
      ) : (
        <span className={styles.locked}>Pagamento gerado: quantidade bloqueada</span>
      )}

      {error && <div className={styles.error} role="alert">{error}</div>}
      {success && <div className={styles.success} role="status">Quantidade enviada para cotação.</div>}
    </form>
  );
}
