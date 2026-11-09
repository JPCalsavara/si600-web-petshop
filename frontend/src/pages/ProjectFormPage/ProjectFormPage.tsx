import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiRequestError, createProject, listFees } from '../../services/api';
import { maskCpfCnpj, projectFormSchema } from '../../utils/masks';
import type { AreaEstande } from '../../types';
import styles from './ProjectFormPage.module.scss';

const AREAS: AreaEstande[] = ['B2C', 'Music Hub', 'Music Sport', 'Internacional'];

interface Props {
  onDone: () => void;
  onCreated?: () => void;
}

export function ProjectFormPage({ onDone, onCreated }: Props) {
  const [name, setName] = useState('');
  const [document, setDocument] = useState('');
  const [email, setEmail] = useState('');
  const [area, setArea] = useState<AreaEstande>('B2C');
  const [address, setAddress] = useState('');
  const [pdfDeadline, setPdfDeadline] = useState('');
  const [paymentDeadline, setPaymentDeadline] = useState('');
  const [feeIds, setFeeIds] = useState<string[]>([]);
  const [fees, setFees] = useState<Array<{ id: string; name: string; type: string }>>([]);
  const [error, setError] = useState<string | null>(null);
  const [credentials, setCredentials] = useState<{ login: string } | null>(null);

  useEffect(() => {
    listFees()
      .then((items) => setFees(items.filter((fee) => fee.active)))
      .catch((err) =>
        setError(err instanceof Error ? err.message : 'Não foi possível carregar as taxas.'),
      );
  }, []);

  const toggleFee = (id: string) =>
    setFeeIds((prev) => (prev.includes(id) ? prev.filter((f) => f !== id) : [...prev, id]));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const parsed = projectFormSchema.safeParse({
        name,
        document,
        email,
        address,
        area,
        pdfDeadline,
        paymentDeadline,
      });

      if (!parsed.success) {
        setError(parsed.error.issues[0]?.message ?? 'Verifique os dados informados.');
        return;
      }

      const created = await createProject({
        name,
        document,
        address,
        area,
        email,
        pdfDeadline,
        paymentDeadline,
        feeIds,
      });
      setCredentials({ login: created.contactEmail });
      onCreated?.();
    } catch (err) {
      setError(
        err instanceof ApiRequestError
          ? err.message
          : 'Não foi possível salvar o projeto.',
      );
    }
  }

  if (credentials) {
    return (
      <div className={styles.page}>
        <header className={styles.modalHeader}>
          <h1>Projeto criado</h1>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onDone}
            aria-label="Fechar"
            title="Fechar"
          >
            <span aria-hidden="true">✕</span>
          </button>
        </header>
        <section className={styles.formFields}>
          <p className={styles.success}>
            Projeto criado com sucesso. O login do cliente é o e-mail cadastrado.
          </p>
          <div className={styles.row}>
            <span>Usuário</span>
            <strong>{credentials.login}</strong>
          </div>
          <p className={styles.hint}>
            A criação de senha/autenticação não faz parte do contrato atual do backend.
          </p>
        </section>
        <div className={styles.actions}>
          <button type="button" className={styles.save} onClick={onDone}>
            Voltar à lista
          </button>
        </div>
      </div>
    );
  }

  return (
    <form className={styles.page} onSubmit={submit}>
      <header className={styles.modalHeader}>
        <h1>Novo Projeto</h1>
        <button
          type="button"
          className={styles.closeButton}
          onClick={onDone}
          aria-label="Cancelar"
          title="Cancelar e fechar"
        >
          <span aria-hidden="true">✕</span>
          <span className={styles.srOnly}>Cancelar</span>
        </button>
      </header>

      <section className={styles.formFields}>
        {error && (
          <div className={styles.alert} role="alert">
            {error}
          </div>
        )}

        <label className={styles.row}>
          <span>Nome / Razão social *</span>
          <input
            required
            placeholder="Ex.: Empresa Expositora Ltda."
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label className={styles.row}>
          <span>CPF / CNPJ *</span>
          <input
            required
            inputMode="numeric"
            maxLength={18}
            placeholder="000.000.000-00 ou 00.000.000/0000-00"
            value={document}
            onChange={(e) => setDocument(maskCpfCnpj(e.target.value))}
          />
        </label>
        <label className={styles.row}>
          <span>E-mail (login) *</span>
          <input
            required
            type="email"
            placeholder="contato@empresa.com.br"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label className={styles.row}>
          <span>Endereço *</span>
          <input
            required
            placeholder="Rua, número, cidade - UF"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
          />
        </label>
        <label className={styles.row}>
          <span>Área do estande *</span>
          <select
            value={area}
            onChange={(e) => setArea(e.target.value as AreaEstande)}
          >
            {AREAS.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.row}>
          <span>Limite envio do PDF *</span>
          <input
            required
            type="date"
            placeholder="dd/mm/aaaa"
            value={pdfDeadline}
            onChange={(e) => setPdfDeadline(e.target.value)}
          />
        </label>
        <label className={styles.row}>
          <span>Limite de pagamento *</span>
          <input
            required
            type="date"
            placeholder="dd/mm/aaaa"
            value={paymentDeadline}
            min={pdfDeadline || undefined}
            onChange={(e) => setPaymentDeadline(e.target.value)}
          />
        </label>

        <fieldset className={styles.fees}>
          <legend>Taxas do projeto</legend>
          <div className={styles.feeGrid}>
            {fees.map((fee) => (
              <label key={fee.id} className={styles.fee}>
                <input
                  type="checkbox"
                  checked={feeIds.includes(fee.id)}
                  onChange={() => toggleFee(fee.id)}
                />
                {fee.name}
                {fee.type === 'FIXA' && <em> (fixa)</em>}
              </label>
            ))}
          </div>
        </fieldset>
      </section>

      <div className={styles.actions}>
        <button type="submit" className={styles.save}>
          Salvar
        </button>
      </div>
    </form>
  );
}
