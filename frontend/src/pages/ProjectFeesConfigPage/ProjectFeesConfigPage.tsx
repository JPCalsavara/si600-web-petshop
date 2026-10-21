import { useEffect, useState } from 'react';
import { configureProjectFees, getProjectFeeConfiguration, listFees } from '../../services/api';
import { FEE_TYPE_LABELS, feeStatusLabel } from '../../constants/feeStatus';
import type { Fee } from '../../types';
import type { ProjectFeeConfiguration } from '../../services/api';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** Uma taxa só pode sair do projeto antes de ter quantidade, cotação ou pagamento (regra do backend). */
export function isLocked(link: ProjectFeeConfiguration): boolean {
  return link.quantity !== null || link.quotedAmount !== null
    || link.status === 'COTADA' || link.status === 'PAGAMENTO_GERADO';
}

function typeLabel(fee: Fee) {
  return fee.type === 'VARIAVEL'
    ? `${FEE_TYPE_LABELS.VARIAVEL} (${fee.measurementUnit ?? 'unidade não definida'})`
    : FEE_TYPE_LABELS[fee.type];
}

export function ProjectFeesConfigPage({ projectId, projectName, onBack }: { projectId: string; projectName: string; onBack: () => void }) {
  const [catalog, setCatalog] = useState<Fee[]>([]);
  const [current, setCurrent] = useState<ProjectFeeConfiguration[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function apply(fees: Fee[], links: ProjectFeeConfiguration[]) {
    const linked = new Set(links.map(link => link.feeId));
    // Taxas desativadas depois do vínculo continuam listadas para o Admin ver que ainda estão no projeto.
    setCatalog(fees.filter(fee => fee.active || linked.has(fee.id)));
    setCurrent(links);
    setSelected(links.map(link => link.feeId));
  }

  async function load() {
    setLoading(true); setError(null);
    try {
      const [fees, links] = await Promise.all([listFees(), getProjectFeeConfiguration(projectId)]);
      apply(fees, links);
    } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível carregar a configuração.'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, [projectId]);

  async function save() {
    setSaving(true); setError(null); setNotice(null);
    try {
      const links = await configureProjectFees(projectId, selected);
      setCurrent(links); setSelected(links.map(link => link.feeId));
      setNotice('Configuração de taxas salva.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível salvar a configuração.'); }
    finally { setSaving(false); }
  }

  const lockedFeeIds = new Set(current.filter(isLocked).map(link => link.feeId));

  return <section style={{ padding: 24 }}>
    <button type="button" onClick={onBack}>← Voltar aos projetos</button>
    <h1>Configurar taxas do projeto</h1><p><strong>{projectName}</strong></p>
    <p>Selecione as taxas do catálogo que se aplicam a este projeto. Taxas fixas e por metragem têm o valor calculado automaticamente; as variáveis são cotadas na tela "Cotação de taxas" depois que o cliente informar a quantidade. Valores de demonstração não representam tarifas oficiais.</p>
    {error && <p role="alert" style={{ color: '#b91c1c' }}>{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {loading ? <p>Carregando catálogo e vínculos...</p> : <>
      {catalog.length === 0 ? <p>Não há taxas ativas no catálogo.</p> : <fieldset style={{ display: 'grid', gap: 12, border: '1px solid #ddd', padding: 16 }}>
        <legend>Taxas disponíveis</legend>
        {catalog.map(fee => <label key={fee.id} style={{ display: 'flex', gap: 10, alignItems: 'start' }}>
          <input type="checkbox" checked={selected.includes(fee.id)} disabled={lockedFeeIds.has(fee.id)}
            onChange={event => setSelected(old => event.target.checked ? [...old, fee.id] : old.filter(id => id !== fee.id))} />
          <span><strong>{fee.name}</strong>{!fee.active && ' (desativada no catálogo)'} — {typeLabel(fee)}<br />{fee.description || 'Sem descrição'}</span>
        </label>)}
      </fieldset>}
      {current.length > 0 && <><h2>Taxas já vinculadas</h2>
        <ul>{current.map(link => <li key={link.projectFeeId}>
          {link.name} — {feeStatusLabel(link.status)}
          {link.quantity !== null ? ` — quantidade: ${link.quantity.toLocaleString('pt-BR')} ${link.measurementUnit ?? ''}` : ''}
          {link.quotedAmount !== null ? ` — cotada em ${currency.format(link.quotedAmount)}` : ''}
        </li>)}</ul>
        <p>Taxas com quantidade, cotação ou pagamento não podem ser removidas.</p></>}
      <button type="button" disabled={saving || loading} onClick={() => void save()}>{saving ? 'Salvando...' : 'Salvar configuração'}</button>
    </>}
  </section>;
}
