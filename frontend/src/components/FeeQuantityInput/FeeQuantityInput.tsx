import { useState } from 'react';
import { submitFeeQuantity } from '../../services/api';
import { parseQuantity } from '../../services/feeQuantity';
import { FEE_STATUS_LABELS } from '../../constants/feeStatus';
import type { ProjectFee } from '../../types';
import styles from './FeeQuantityInput.module.scss';

interface Props {
  projectId: string;
  /** Linha da taxa variável, vinda da lista de taxas do projeto (US-05). */
  fee: ProjectFee;
  /** Chamado com a taxa atualizada pela API, para a lista (US-05) refletir o novo status. */
  onUpdated?: (fee: ProjectFee) => void;
  /** Esconde nome e status quando a linha que contém o input já os mostra. */
  compact?: boolean;
}

/** US-06: input de quantidade de uma taxa variável (ex.: energia em kVA) e envio para cotação. */
export function FeeQuantityInput({ projectId, fee, onUpdated, compact = false }: Props) {
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
    <form className={`${styles.row} ${compact ? styles.compact : ''}`} onSubmit={(event) => void handleSubmit(event)} noValidate>
      {!compact && (
        <div className={styles.info}>
          <strong>{current.name}</strong>
          <span className={`${styles.badge} ${styles[current.status]}`}>{FEE_STATUS_LABELS[current.status]}</span>
        </div>
      )}

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
        <button type="submit" className={styles.primaryButton} disabled={saving}>
          {saving ? 'Enviando...' : 'Enviar para Cotação'}
        </button>
      ) : (
        <span className={styles.locked}>Pagamento gerado: quantidade bloqueada</span>
      )}

      {error && <div className={styles.error} role="alert">{error}</div>}
      {success && <div className={styles.success} role="status">Quantidade enviada para cotação. A taxa voltou para "Aguardando cotação".</div>}
    </form>
  );
}
