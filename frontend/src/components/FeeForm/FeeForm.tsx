import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiRequestError, createFee, updateFee } from '../../services/api';
import type { AreaPricingMode, Fee, FeeRequest, FeeType } from '../../types';
import styles from './FeeForm.module.scss';

interface Props { fee?: Fee; onSaved: () => void; onCancel: () => void; }
type NumericField = 'amount' | 'amountPerM2' | 'areaPerUnitM2' | 'unitAmount';

export function FeeForm({ fee, onSaved, onCancel }: Props) {
  const [name, setName] = useState(fee?.name ?? '');
  const [description, setDescription] = useState(fee?.description ?? '');
  const [type, setType] = useState<FeeType>(fee?.type ?? 'FIXA');
  const [mode, setMode] = useState<AreaPricingMode>(fee?.areaPricingMode ?? 'VALOR_POR_M2');
  const [unit, setUnit] = useState(fee?.measurementUnit ?? '');
  const [numbers, setNumbers] = useState<Record<NumericField, string>>({
    amount: fee?.amount?.toString() ?? '', amountPerM2: fee?.amountPerM2?.toString() ?? '',
    areaPerUnitM2: fee?.areaPerUnitM2?.toString() ?? '', unitAmount: fee?.unitAmount?.toString() ?? '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function resetConfiguration() {
    setNumbers({ amount: '', amountPerM2: '', areaPerUnitM2: '', unitAmount: '' });
    setUnit('');
    setErrors({});
    setMessage(null);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (saving) return;
    const invalid: Record<string, string> = {};
    const normalizedName = name.replace(/^[\s\x00-\x20]+|[\s\x00-\x20]+$/gu, '');
    const normalizedUnit = unit.replace(/^[\s\x00-\x20]+|[\s\x00-\x20]+$/gu, '');
    if (!normalizedName) invalid.name = 'Informe o nome da taxa.';
    const positive = (field: NumericField) => {
      const value = Number(numbers[field]);
      if (!numbers[field].trim() || !Number.isFinite(value) || value <= 0) invalid[field] = 'Informe um valor maior que zero.';
      return value;
    };
    const base = { name: normalizedName, description };
    let body: FeeRequest;
    if (type === 'FIXA') body = { ...base, type, amount: positive('amount') };
    else if (type === 'VARIAVEL') {
      if (!normalizedUnit) invalid.measurementUnit = 'Informe a unidade de medida.';
      body = { ...base, type, measurementUnit: normalizedUnit };
    } else if (mode === 'VALOR_POR_M2') body = { ...base, type, areaPricingMode: mode, amountPerM2: positive('amountPerM2') };
    else body = { ...base, type, areaPricingMode: mode, areaPerUnitM2: positive('areaPerUnitM2'), unitAmount: positive('unitAmount') };
    setErrors(invalid);
    setMessage(null);
    if (Object.keys(invalid).length) return;
    try {
      setSaving(true);
      if (fee) await updateFee(fee.id, body);
      else await createFee(body);
      onSaved();
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setErrors(error.invalidFields);
        setMessage(Object.keys(error.invalidFields).length ? 'Revise os campos indicados.' : 'Não foi possível salvar a taxa. Tente novamente.');
      } else setMessage('Não foi possível salvar a taxa. Verifique sua conexão e tente novamente.');
    } finally {
      setSaving(false);
    }
  }

  function numeric(field: NumericField, label: string) {
    return <label className={styles.field}>
      <span>{label} *</span>
      <input name={field} type="number" inputMode="decimal" min="0.01" step="0.01" required value={numbers[field]}
        aria-invalid={Boolean(errors[field])} aria-describedby={errors[field] ? `${field}-error` : undefined}
        onChange={(event) => setNumbers({ ...numbers, [field]: event.target.value })} />
      {errors[field] && <span id={`${field}-error`} className={styles.fieldError}>{errors[field]}</span>}
    </label>;
  }

  return <form onSubmit={submit} noValidate className={styles.form}>
    <h1>{fee ? 'Editar Taxa' : 'Nova Taxa'}</h1>
    {fee && <p>Status: {fee.active ? 'Ativa' : 'Inativa'}</p>}
    {message && <div role="alert" className={styles.alert}>{message}</div>}
    <fieldset disabled={saving} className={styles.panel}>
      <legend className={styles.legend}>Dados da taxa</legend>
      <label className={styles.field}>
        <span>Nome *</span>
        <input name="name" required maxLength={200} value={name} onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(errors.name)} aria-describedby={errors.name ? 'name-error' : undefined} />
        {errors.name && <span id="name-error" className={styles.fieldError}>{errors.name}</span>}
      </label>
      <label className={styles.field}>
        <span>Descrição</span>
        <textarea name="description" maxLength={2000} rows={3} value={description} onChange={(event) => setDescription(event.target.value)}
          aria-invalid={Boolean(errors.description)} aria-describedby={errors.description ? 'description-error' : undefined} />
        {errors.description && <span id="description-error" className={styles.fieldError}>{errors.description}</span>}
      </label>
      <label className={styles.field}>
        <span>Tipo *</span>
        <select name="type" value={type} onChange={(event) => { setType(event.target.value as FeeType); resetConfiguration(); }}>
          <option value="FIXA">Fixa</option><option value="POR_METRAGEM">Por metragem</option><option value="VARIAVEL">Variável</option>
        </select>
        {errors.type && <span className={styles.fieldError}>{errors.type}</span>}
      </label>
      {type === 'FIXA' && numeric('amount', 'Valor em R$')}
      {type === 'POR_METRAGEM' && <>
        <label className={styles.field}>
          <span>Modalidade *</span>
          <select name="areaPricingMode" value={mode} onChange={(event) => { setMode(event.target.value as AreaPricingMode); resetConfiguration(); }}>
            <option value="VALOR_POR_M2">Valor por m²</option><option value="UNIDADES_POR_INTERVALO">Unidades por intervalo de metragem</option>
          </select>
          {errors.areaPricingMode && <span className={styles.fieldError}>{errors.areaPricingMode}</span>}
        </label>
        {mode === 'VALOR_POR_M2' ? numeric('amountPerM2', 'Valor por m² em R$') : <>
          {numeric('areaPerUnitM2', 'Quantidade de m² por unidade')}
          {numeric('unitAmount', 'Valor unitário em R$')}
        </>}
      </>}
      {type === 'VARIAVEL' && <label className={styles.field}>
        <span>Unidade de medida *</span>
        <input name="measurementUnit" required maxLength={100} placeholder="Ex.: kVA" value={unit} onChange={(event) => setUnit(event.target.value)}
          aria-invalid={Boolean(errors.measurementUnit)} aria-describedby={errors.measurementUnit ? 'unit-error' : undefined} />
        {errors.measurementUnit && <span id="unit-error" className={styles.fieldError}>{errors.measurementUnit}</span>}
      </label>}
    </fieldset>
    <div className={styles.actions}>
      <button type="button" disabled={saving} className={styles.cancel} onClick={onCancel}>Cancelar</button>
      <button type="submit" disabled={saving} className={styles.save}>{saving ? 'Salvando...' : 'Salvar'}</button>
    </div>
  </form>;
}
