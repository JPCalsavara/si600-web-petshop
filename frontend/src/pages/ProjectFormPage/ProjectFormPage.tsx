import { useState } from 'react';
import type { FormEvent } from 'react';
import {
  FEE_CATALOG,
  ProjectValidationError,
  createClientProject,
  updateClientProject,
} from '../../services/projectRegistry';
import type { AreaEstande, ClientProject } from '../../types';
import styles from './ProjectFormPage.module.scss';

const AREAS: AreaEstande[] = ['B2C', 'Music Hub', 'Music Sport', 'Internacional'];

interface Props {
  /** Quando informado, a tela edita prazos e taxas deste projeto. */
  project?: ClientProject;
  onDone: () => void;
}

export function ProjectFormPage({ project, onDone }: Props) {
  const editing = Boolean(project);
  const [name, setName] = useState(project?.name ?? '');
  const [document, setDocument] = useState(project?.document ?? '');
  const [email, setEmail] = useState(project?.email ?? '');
  const [area, setArea] = useState<AreaEstande>(project?.area ?? 'B2C');
  const [address, setAddress] = useState(project?.address ?? '');
  const [pdfDeadline, setPdfDeadline] = useState(project?.pdfDeadline ?? '');
  const [paymentDeadline, setPaymentDeadline] = useState(project?.paymentDeadline ?? '');
  const [feeIds, setFeeIds] = useState<string[]>(project?.feeIds ?? []);
  const [error, setError] = useState<string | null>(null);
  const [credentials, setCredentials] = useState<{ login: string; password: string } | null>(null);

  const toggleFee = (id: string) =>
    setFeeIds((prev) => (prev.includes(id) ? prev.filter((f) => f !== id) : [...prev, id]));

  function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      if (project) {
        updateClientProject(project.id, { pdfDeadline, paymentDeadline, feeIds });
        onDone();
        return;
      }
      const { project: created, temporaryPassword } = createClientProject({
        name, document, address, area, email, pdfDeadline, paymentDeadline, feeIds,
      });
      setCredentials({ login: created.email, password: temporaryPassword });
    } catch (err) {
      setError(err instanceof ProjectValidationError ? err.message : 'Não foi possível salvar o projeto.');
    }
  }

  // Tela "Novo Usuário" do Figma: mostra usuário e senha para o Admin repassar.
  if (credentials) {
    return (
      <div className={styles.page}>
        <h1>Projeto criado</h1>
        <section className={styles.panel}>
          <p className={styles.success}>Acesso do cliente criado com sucesso. Repasse os dados abaixo.</p>
          <div className={styles.row}><span>Usuário</span><strong>{credentials.login}</strong></div>
          <div className={styles.row}><span>Senha temporária</span><strong><code>{credentials.password}</code></strong></div>
          <p className={styles.hint}>Esta senha não será exibida novamente.</p>
        </section>
        <div className={styles.actions}>
          <button type="button" className={styles.save} onClick={onDone}>Voltar à lista</button>
        </div>
      </div>
    );
  }

  return (
    <form className={styles.page} onSubmit={submit}>
      <h1>{editing ? 'Editar Projeto' : 'Novo Projeto'}</h1>

      <section className={styles.panel}>
        {error && <div className={styles.alert} role="alert">{error}</div>}

        <label className={styles.row}>
          <span>Nome / Razão social *</span>
          <input required disabled={editing} value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className={styles.row}>
          <span>CPF / CNPJ *</span>
          <input required disabled={editing} value={document} onChange={(e) => setDocument(e.target.value)} />
        </label>
        <label className={styles.row}>
          <span>E-mail (login) *</span>
          <input required disabled={editing} type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className={styles.row}>
          <span>Endereço *</span>
          <input required disabled={editing} value={address} onChange={(e) => setAddress(e.target.value)} />
        </label>
        <label className={styles.row}>
          <span>Área do estande *</span>
          <select disabled={editing} value={area} onChange={(e) => setArea(e.target.value as AreaEstande)}>
            {AREAS.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </label>
        <label className={styles.row}>
          <span>Limite envio do PDF *</span>
          <input required type="date" value={pdfDeadline} onChange={(e) => setPdfDeadline(e.target.value)} />
        </label>
        <label className={styles.row}>
          <span>Limite de pagamento *</span>
          <input required type="date" value={paymentDeadline} min={pdfDeadline || undefined} onChange={(e) => setPaymentDeadline(e.target.value)} />
        </label>

        <fieldset className={styles.fees}>
          <legend>Taxas do projeto</legend>
          <div className={styles.feeGrid}>
            {FEE_CATALOG.map((fee) => (
              <label key={fee.id} className={styles.fee}>
                <input type="checkbox" checked={feeIds.includes(fee.id)} onChange={() => toggleFee(fee.id)} />
                {fee.name}{fee.fixed && <em> (fixa)</em>}
              </label>
            ))}
          </div>
        </fieldset>
      </section>

      <div className={styles.actions}>
        <button type="button" className={styles.cancel} onClick={onDone}>Cancelar</button>
        <button type="submit" className={styles.save}>Salvar</button>
      </div>
    </form>
  );
}
