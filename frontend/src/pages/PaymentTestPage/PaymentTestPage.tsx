import { useRef, useState } from 'react';
import styles from './PaymentTestPage.module.scss';

type Payment = { id: string; orderId: string | null; amount: number; status: string; createdAt: string; updatedAt: string; expiresAt: string | null };
type PaymentHistory = { payments: Payment[]; page: number; hasNext: boolean };
type WebhookConfiguration = { enabled: boolean; configured: boolean };
type Boleto = { payment: Payment; ticketUrl: string | null };
const fields = [
  ['firstName', 'Nome', 'text'], ['lastName', 'Sobrenome', 'text'], ['email', 'E-mail', 'email'],
  ['document', 'CPF/CNPJ (somente números)', 'text'], ['zipCode', 'CEP (somente números)', 'text'],
  ['streetName', 'Rua', 'text'], ['streetNumber', 'Número', 'text'], ['neighborhood', 'Bairro', 'text'],
  ['city', 'Cidade', 'text'], ['state', 'Estado (UF)', 'text'], ['amount', 'Valor', 'number'],
  ['description', 'Descrição', 'text'], ['expirationDays', 'Dias até o vencimento', 'number'],
] as const;
const initial = { ...Object.fromEntries(fields.map(([key]) => [key, ''])), email: 'test_user_br@testuser.com' };
function safeUrl(value: string | null): string | null {
  try { const url = new URL(value || ''); return url.protocol === 'https:' && !url.username && !url.password ? url.href : null; }
  catch { return null; }
}
export default function PaymentTestPage() {
  const [form, setForm] = useState<Record<string, string>>({ ...initial });
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [paymentId, setPaymentId] = useState(() => localStorage.getItem('payment-test-id') || '');
  const [payment, setPayment] = useState<Payment | null>(null);
  const [ticket, setTicket] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const [history, setHistory] = useState<PaymentHistory | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(false);
  const [webhook, setWebhook] = useState<WebhookConfiguration | null>(null);
  async function request<T>(path: string, body?: unknown): Promise<T> {
    const response = await fetch(`/api/internal/payments${path}`, {
      method: body === undefined ? 'GET' : 'POST', credentials: 'omit', cache: 'no-store',
      headers: { 'Content-Type': 'application/json', Authorization: `Basic ${btoa(unescape(encodeURIComponent(`${username}:${password}`)))}` },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const result = await response.json();
    if (!response.ok) {
      if (result.paymentId) remember(result.paymentId);
      const invalid = result.invalidFields ? ` Campos: ${Object.keys(result.invalidFields).join(', ')}.` : '';
      const detail = result.code ? `Operação não concluída (${result.code}). O histórico das tentativas foi preservado.` : result.detail || result.title || 'Não foi possível concluir a operação.';
      throw new Error(detail + invalid);
    }
    return result;
  }
  function remember(id: string) { setPaymentId(id); localStorage.setItem('payment-test-id', id); }
  async function run(action: () => Promise<void>) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true); setMessage(''); setError(false);
    try { await action(); }
    catch (cause) { setError(true); setMessage(cause instanceof Error ? cause.message : 'Falha de comunicação. Nenhuma cobrança adicional foi enviada automaticamente. Atualize o histórico para conferir a tentativa.'); }
    finally { inFlight.current = false; setBusy(false); }
  }
  async function create() {
    // Cada ação explícita recebe uma nova chave. O mesmo corpo HTTP mantém a mesma chave em uma repetição.
    const requestId = crypto.randomUUID();
    const document = form.document.replace(/\D/g, '');
    const created = await request<Payment>('/boleto', {
      requestId, chargeReference: `dev-${requestId}`, amount: Number(form.amount), description: form.description,
      expirationDays: Number(form.expirationDays), payer: {
        firstName: form.firstName, lastName: form.lastName, email: form.email,
        identificationType: document.length === 14 ? 'CNPJ' : 'CPF', identificationNumber: document,
        address: { zipCode: form.zipCode.replace(/\D/g, ''), streetName: form.streetName,
          streetNumber: form.streetNumber, neighborhood: form.neighborhood, city: form.city, state: form.state.toUpperCase() },
      },
    });
    remember(created.id); setPayment(created); setTicket(null);
    setMessage(['INCERTO', 'CRIANDO'].includes(created.status)
      ? 'Tentativa registrada com resultado pendente. Uma cobrança adicional só será criada por um novo clique em Gerar novo boleto.'
      : created.status === 'FALHOU' ? 'Tentativa recusada e preservada no histórico. Você pode gerar um novo boleto de teste.' : 'Boleto gerado.');
    setError(created.status === 'FALHOU');
    if (created.orderId) {
      try {
        const details = await request<Boleto>(`/${encodeURIComponent(created.id)}/boleto`);
        setPayment(details.payment); setTicket(safeUrl(details.ticketUrl));
      } catch {
        setMessage('Cobrança registrada, mas os detalhes não puderam ser consultados. Use Consultar status para esta tentativa.');
      }
    }
    try { await loadHistory(); }
    catch { setMessage(current => `${current} O histórico não pôde ser carregado; use Atualizar histórico.`); }
  }
  async function loadHistory(page = 0) {
    setHistory(await request<PaymentHistory>(`/history/${page}`));
  }
  function updateHistory(updated: Payment) {
    setHistory(current => current ? { ...current, payments: current.payments.map(item => item.id === updated.id ? updated : item) } : null);
  }
  async function selectPayment(id: string) {
    const local = await request<Payment>(`/${encodeURIComponent(id)}`);
    setPayment(local); remember(local.id); setTicket(null); updateHistory(local);
    return local;
  }
  async function consult(id = paymentId) {
    setTicket(null);
    const local = await selectPayment(id);
    const result = await request<Boleto>(`/${encodeURIComponent(local.id)}/boleto`);
    setPayment(result.payment); setTicket(safeUrl(result.ticketUrl)); updateHistory(result.payment);
    setMessage('Status consultado no Mercado Pago e atualizado no banco.');
  }
  async function refreshWebhook() {
    setWebhook(await request<WebhookConfiguration>('/webhook-status'));
    if (paymentId) {
      const local = await request<Payment>(`/${encodeURIComponent(paymentId)}`);
      setPayment(local); remember(local.id);
    }
    setMessage('Informações locais atualizadas. Nenhuma consulta ao provedor foi realizada.');
  }
  const link = safeUrl(ticket);
  return <main className={styles.page}>
    <h1>Teste de Pagamentos — Mercado Pago</h1>
    <p>Ambiente de desenvolvimento. As operações usam o provedor configurado no backend. Cada clique em Gerar novo boleto inicia uma cobrança de teste independente. Nenhum reenvio ou nova cobrança ocorre automaticamente. Use somente credenciais de teste no backend.</p>
    <section className={styles.panel} aria-label="Acesso de desenvolvimento">
      <label>Usuário de desenvolvimento<input autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} /></label>
      <label>Senha de desenvolvimento<input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
    </section>
    <form onSubmit={e => { e.preventDefault(); void run(create); }}>
      <fieldset disabled={busy} className={styles.panel}>
        <legend>Criar boleto</legend>
        <div className={styles.grid}>{fields.map(([key, label, type]) => <label key={key}>{label}
          <input required type={type} value={form[key]} min={key === 'expirationDays' ? 3 : key === 'amount' ? 0.01 : undefined}
            max={key === 'expirationDays' ? 30 : undefined} step={key === 'amount' ? '0.01' : key === 'expirationDays' ? '1' : undefined}
            maxLength={key === 'state' ? 2 : key === 'description' ? 255 : 254}
            onChange={e => setForm({ ...form, [key]: e.target.value })} />
        </label>)}</div>
        <div className={styles.actions}>
          <button disabled={!username || !password} type="submit">Gerar novo boleto</button>
          <button type="button" onClick={() => { setForm({ ...initial }); setPayment(null); setTicket(null); setMessage('Formulário limpo. O histórico e os registros no banco foram preservados.'); }}>Limpar formulário</button>
        </div>
      </fieldset>
    </form>
    <section className={styles.panel} aria-label="Consulta de pagamento">
      <label>ID interno do pagamento<input disabled={busy} value={paymentId} onChange={e => { setPaymentId(e.target.value); setPayment(null); setTicket(null); }} /></label>
      <div className={styles.actions}>
        <button disabled={busy || !paymentId || !username || !password} onClick={() => void run(() => consult())}>Consultar status</button>
        {link ? <a href={link} target="_blank" rel="noopener noreferrer">Ver boleto</a> : <button disabled>Ver boleto</button>}
      </div>
      {payment && <dl>
        <dt>ID interno</dt><dd>{payment.id}</dd><dt>ID da ordem no Mercado Pago</dt><dd>{payment.orderId || 'Não confirmado'}</dd>
        <dt>Valor</dt><dd>{Number(payment.amount).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</dd>
        <dt>Status atual</dt><dd>{payment.status}</dd><dt>Data de criação</dt><dd>{new Date(payment.createdAt).toLocaleString('pt-BR')}</dd>
        <dt>Data de vencimento</dt><dd>{payment.expiresAt ? new Date(payment.expiresAt).toLocaleString('pt-BR') : 'Não informada pelo provedor'}</dd>
        <dt>Link do boleto</dt><dd>{link ? <a href={link} target="_blank" rel="noopener noreferrer">{link}</a> : 'Consulte o status para obter os detalhes.'}</dd>
      </dl>}
    </section>
    <section className={styles.panel} aria-label="Histórico de boletos">
      <h2>Histórico de boletos</h2>
      <div className={styles.actions}><button disabled={busy || !username || !password} onClick={() => void run(() => loadHistory())}>Atualizar histórico</button></div>
      {!history ? <p>Atualize o histórico para consultar as tentativas persistidas.</p> : !history.payments.length ? <p>Nenhuma tentativa nesta página.</p> : <div className={styles.tableWrap}>
        <table><thead><tr><th>ID</th><th>Valor</th><th>Data</th><th>Status</th><th>Ações</th></tr></thead>
          <tbody>{history.payments.map(item => <tr key={item.id}>
            <td>{item.id}</td><td>{Number(item.amount).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</td>
            <td>{new Date(item.createdAt).toLocaleString('pt-BR')}</td><td>{item.status}</td>
            <td><div className={styles.actions}>
              <button disabled={busy} onClick={() => void run(async () => { await selectPayment(item.id); })}>Consultar</button>
              <button disabled={busy} onClick={() => void run(() => consult(item.id))}>Sincronizar</button>
            </div></td>
          </tr>)}</tbody>
        </table></div>}
      {history && <div className={styles.actions}>
        <button disabled={busy || history.page === 0} onClick={() => void run(() => loadHistory(history.page - 1))}>Anterior</button>
        <span>Página {history.page + 1}</span>
        <button disabled={busy || !history.hasNext} onClick={() => void run(() => loadHistory(history.page + 1))}>Próxima</button>
      </div>}
    </section>
    <section className={styles.panel} aria-label="Webhook">
      <h2>Webhook</h2>
      <p>Habilitado: {webhook ? (webhook.enabled ? 'Sim' : 'Não') : 'Ainda não consultado'}</p>
      <p>Último status de sincronização conhecido: {payment?.status || 'Nenhum pagamento consultado'}</p>
      <p>Última atualização persistida: {payment?.updatedAt ? new Date(payment.updatedAt).toLocaleString('pt-BR') : 'Não disponível'}</p>
      {!webhook ? <p>Informe seu acesso e atualize as informações para verificar a configuração.</p>
        : !webhook.configured && <p role="status">Atualização automática não configurada. Verifique a habilitação e o segredo no backend.</p>}
      <p>O estado exibido pode ter sido atualizado por consulta manual ou webhook. Sem polling: atualize as informações para ler mudanças recebidas em segundo plano.</p>
      <div className={styles.actions}><button disabled={busy || !username || !password} onClick={() => void run(refreshWebhook)}>Atualizar informações</button></div>
    </section>
    {message && <p role={error ? 'alert' : 'status'} className={error ? styles.error : styles.message}>{message}</p>}
  </main>;
}
