import { useEffect, useRef, useState } from 'react';
import styles from './PaymentTestPage.module.scss';

type Payment = {
  id: string;
  orderId: string | null;
  amount: number;
  status: string;
  createdAt: string;
  updatedAt: string;
  expiresAt: string | null;
};

type PaymentHistory = {
  payments: Payment[];
  page: number;
  hasNext: boolean;
};

type WebhookConfiguration = {
  enabled: boolean;
  configured: boolean;
};

const fields = [
  ['firstName', 'Nome', 'text'],
  ['lastName', 'Sobrenome', 'text'],
  ['email', 'E-mail', 'email'],
  ['document', 'CPF/CNPJ (somente números)', 'text'],
  ['zipCode', 'CEP (somente números)', 'text'],
  ['streetName', 'Rua / Logradouro', 'text'],
  ['streetNumber', 'Número', 'text'],
  ['neighborhood', 'Bairro', 'text'],
  ['city', 'Cidade', 'text'],
  ['state', 'UF', 'text'],
  ['amount', 'Valor (R$)', 'number'],
  ['description', 'Descrição da Cobrança', 'text'],
  ['expirationDays', 'Dias até o vencimento', 'number'],
] as const;

const defaultFormValues: Record<string, string> = {
  firstName: 'Expositor',
  lastName: 'Teste',
  email: 'test_user_br@testuser.com',
  document: '12345678909',
  zipCode: '13083852',
  streetName: 'Rua Albert Einstein',
  streetNumber: '400',
  neighborhood: 'Barão Geraldo',
  city: 'Campinas',
  state: 'SP',
  amount: '150.00',
  description: 'Taxa de Energia Elétrica e Montagem',
  expirationDays: '3',
};

function safeUrl(value: string | null): string | null {
  try {
    const url = new URL(value || '');
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null;
  } catch {
    return null;
  }
}

function getStatusBadgeClass(status: string): string {
  switch (status) {
    case 'PAGO':
      return styles.statusPago;
    case 'GERADO':
      return styles.statusGerado;
    case 'CRIANDO':
    case 'INCERTO':
      return styles.statusPendente;
    case 'FALHOU':
    case 'CANCELADO':
    case 'EXPIRADO':
      return styles.statusFalha;
    default:
      return styles.statusNeutro;
  }
}

export default function PaymentTestPage() {
  const [activeTab, setActiveTab] = useState<'emitir' | 'historico' | 'webhook'>('emitir');
  const [form, setForm] = useState<Record<string, string>>({ ...defaultFormValues });
  
  // Credentials (persisted in localStorage for convenience)
  const [username, setUsername] = useState(() => localStorage.getItem('payment_dev_user') || 'dev_user');
  const [password, setPassword] = useState(() => localStorage.getItem('payment_dev_pass') || 'dev_password_123');
  
  const [paymentId, setPaymentId] = useState(() => localStorage.getItem('payment-test-id') || '');
  const [payment, setPayment] = useState<Payment | null>(null);
  const [ticket, setTicket] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const [history, setHistory] = useState<PaymentHistory | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(false);
  const [webhook, setWebhook] = useState<WebhookConfiguration | null>(null);

  useEffect(() => {
    localStorage.setItem('payment_dev_user', username);
  }, [username]);

  useEffect(() => {
    localStorage.setItem('payment_dev_pass', password);
  }, [password]);

  // Load initial history when tab changes to 'historico'
  useEffect(() => {
    if (activeTab === 'historico' && !history && username && password) {
      void run(() => loadHistory(0));
    }
    if (activeTab === 'webhook' && !webhook && username && password) {
      void run(refreshWebhook);
    }
  }, [activeTab]);

  async function request<T>(path: string, body?: unknown): Promise<T> {
    const authHeader = `Basic ${btoa(unescape(encodeURIComponent(`${username}:${password}`)))}`;
    const response = await fetch(`/api/internal/payments${path}`, {
      method: body === undefined ? 'GET' : 'POST',
      credentials: 'omit',
      cache: 'no-store',
      headers: {
        'Content-Type': 'application/json',
        Authorization: authHeader,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    const result = await response.json();
    if (!response.ok) {
      if (result.paymentId) remember(result.paymentId);
      const invalid = result.invalidFields ? ` Campos: ${Object.keys(result.invalidFields).join(', ')}.` : '';
      const detail = result.code
        ? `Operação não concluída (${result.code}). O histórico das tentativas foi preservado.`
        : result.detail || result.title || 'Não foi possível concluir a operação.';
      throw new Error(detail + invalid);
    }
    return result;
  }

  function remember(id: string) {
    setPaymentId(id);
    localStorage.setItem('payment-test-id', id);
  }

  async function run(action: () => Promise<void>) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setMessage('');
    setError(false);
    try {
      await action();
    } catch (cause) {
      setError(true);
      setMessage(
        cause instanceof Error
          ? cause.message
          : 'Falha de comunicação. Nenhuma cobrança adicional foi enviada automaticamente. Atualize o histórico para conferir a tentativa.'
      );
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  async function create() {
    const requestId = crypto.randomUUID();
    const document = form.document.replace(/\D/g, '');
    const created = await request<Payment>('/boleto', {
      requestId,
      chargeReference: `dev-${requestId}`,
      amount: Number(form.amount),
      description: form.description,
      expirationDays: Number(form.expirationDays),
      payer: {
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        identificationType: document.length === 14 ? 'CNPJ' : 'CPF',
        identificationNumber: document,
        address: {
          zipCode: form.zipCode.replace(/\D/g, ''),
          streetName: form.streetName,
          streetNumber: form.streetNumber,
          neighborhood: form.neighborhood,
          city: form.city,
          state: form.state.toUpperCase(),
        },
      },
    });

    remember(created.id);
    setPayment(created);
    setTicket(null);

    setMessage(
      ['INCERTO', 'CRIANDO'].includes(created.status)
        ? 'Tentativa registrada com resultado pendente. Uma cobrança adicional só será criada por um novo clique em Gerar novo boleto.'
        : created.status === 'FALHOU'
        ? 'Tentativa recusada e preservada no histórico.'
        : 'Boleto bancário gerado com sucesso no Mercado Pago!'
    );

    // Refresh history if already loaded
    if (history) {
      void loadHistory(0);
    }
  }

  async function consult(id?: string) {
    const target = id || paymentId;
    if (!target) return;
    const result = await request<{ payment: Payment; ticketUrl: string | null }>(`/consult/${target}`);
    remember(result.payment.id);
    setPayment(result.payment);
    setTicket(result.ticketUrl);
    setMessage(`Status atualizado para: ${result.payment.status}.`);

    if (history) {
      setHistory({
        ...history,
        payments: history.payments.map((p) => (p.id === result.payment.id ? result.payment : p)),
      });
    }
  }

  async function selectPayment(id: string) {
    remember(id);
    await consult(id);
  }

  async function loadHistory(page = 0) {
    const result = await request<PaymentHistory>(`/history?page=${page}&size=5`);
    setHistory(result);
  }

  async function refreshWebhook() {
    const result = await request<WebhookConfiguration>('/webhook/status');
    setWebhook(result);
    setMessage('Informações de webhook e sincronização atualizadas.');
  }

  function fillDevDefaults() {
    setUsername('dev_user');
    setPassword('dev_password_123');
    setForm({ ...defaultFormValues });
    setMessage('Credenciais e dados de exemplo preenchidos.');
    setError(false);
  }

  const link = safeUrl(ticket);

  // Compute stats from current history
  const totalCount = history?.payments.length || (payment ? 1 : 0);
  const paidCount = history?.payments.filter((p) => p.status === 'PAGO').length || (payment?.status === 'PAGO' ? 1 : 0);
  const pendingCount =
    history?.payments.filter((p) => ['GERADO', 'CRIANDO', 'INCERTO'].includes(p.status)).length ||
    (['GERADO', 'CRIANDO', 'INCERTO'].includes(payment?.status || '') ? 1 : 0);
  const failedCount =
    history?.payments.filter((p) => ['FALHOU', 'CANCELADO', 'EXPIRADO'].includes(p.status)).length ||
    (['FALHOU', 'CANCELADO', 'EXPIRADO'].includes(payment?.status || '') ? 1 : 0);

  return (
    <main className={styles.page} aria-label="Módulo de Testes de Pagamento">
      {/* Page Header */}
      <header className={styles.header}>
        <div className={styles.titleArea}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <h1 className={styles.title}>Módulo de Pagamentos</h1>
            <span className={styles.badgeDev}>Dev / Homologação</span>
          </div>
          <p className={styles.subtitle}>
            Ambiente técnico para emissão e simulação de boletos via Orders API do Mercado Pago, reconciliação financeira
            e validação de webhooks com verificação de assinatura HMAC SHA-256.
          </p>
        </div>
        <div className={styles.headerActions}>
          <button
            type="button"
            className={styles.btnSecondarySmall}
            onClick={fillDevDefaults}
            title="Preencher com credenciais e dados padrão de teste"
          >
            Preencher dados padrão
          </button>
        </div>
      </header>

      {/* Auth Bar */}
      <section className={styles.authBar} aria-label="Autenticação Técnica Basic Auth">
        <div className={styles.authInputs}>
          <label>
            <span>Usuário Dev:</span>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="dev_user"
              disabled={busy}
            />
          </label>
          <label>
            <span>Senha Dev:</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••••••"
              disabled={busy}
            />
          </label>
        </div>
        <div style={{ fontSize: '12px', color: '#64748b' }}>
          Autenticação Basic Auth validada em tempo constante contra <code>PAYMENT_DEV_*</code>
        </div>
      </section>

      {/* KPI Cards */}
      <section className={styles.kpiGrid} aria-label="Indicadores Rápidos">
        <div className={styles.kpiCard}>
          <span className={styles.kpiLabel}>Total na Sessão</span>
          <span className={styles.kpiValue}>{totalCount}</span>
        </div>
        <div className={styles.kpiCard}>
          <span className={styles.kpiLabel}>Confirmados (PAGO)</span>
          <span className={styles.kpiValue} style={{ color: '#15803d' }}>{paidCount}</span>
        </div>
        <div className={styles.kpiCard}>
          <span className={styles.kpiLabel}>Pendentes / Em Aberto</span>
          <span className={styles.kpiValue} style={{ color: '#0369a1' }}>{pendingCount}</span>
        </div>
        <div className={styles.kpiCard}>
          <span className={styles.kpiLabel}>Falhas / Cancelados</span>
          <span className={styles.kpiValue} style={{ color: '#b91c1c' }}>{failedCount}</span>
        </div>
      </section>

      {/* Tab Navigation */}
      <nav className={styles.tabsBar} aria-label="Abas de Pagamentos">
        <button
          type="button"
          className={`${styles.tabButton} ${activeTab === 'emitir' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('emitir')}
        >
          1. Emitir Boleto de Teste
        </button>
        <button
          type="button"
          className={`${styles.tabButton} ${activeTab === 'historico' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('historico')}
        >
          2. Histórico & Conciliação
        </button>
        <button
          type="button"
          className={`${styles.tabButton} ${activeTab === 'webhook' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('webhook')}
        >
          3. Webhooks & Diagnóstico
        </button>
      </nav>

      {/* Messages / Alerts */}
      {message && (
        <div role={error ? 'alert' : 'status'} className={error ? styles.alertError : styles.alertSuccess}>
          {message}
        </div>
      )}

      {/* Tab 1: Emitir Boleto */}
      {activeTab === 'emitir' && (
        <section className={styles.card}>
          <h2 className={styles.cardTitle}>Solicitação de Cobrança (Boleto Bancário)</h2>
          <p className={styles.cardDescription}>
            Gera uma ordem de pagamento via <code>POST /api/internal/payments/boleto</code> com idempotência garantida
            por UUID v4 e impressão digital (HMAC) dos dados da transação.
          </p>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              void run(create);
            }}
          >
            <div className={styles.formGrid}>
              {fields.map(([key, label, type]) => (
                <div key={key} className={styles.formField}>
                  <label htmlFor={`field-${key}`}>{label}</label>
                  <input
                    id={`field-${key}`}
                    disabled={busy}
                    type={type}
                    value={form[key]}
                    min={key === 'expirationDays' ? 1 : key === 'amount' ? 0.01 : undefined}
                    max={key === 'expirationDays' ? 30 : undefined}
                    step={key === 'amount' ? '0.01' : key === 'expirationDays' ? '1' : undefined}
                    maxLength={key === 'state' ? 2 : key === 'description' ? 255 : 254}
                    onChange={(e) => setForm({ ...form, [key]: e.target.value })}
                    required={['amount', 'description', 'email', 'document'].includes(key)}
                  />
                </div>
              ))}
            </div>

            <div className={styles.formActions}>
              <button className={styles.btnPrimary} disabled={busy || !username || !password} type="submit">
                {busy ? 'Processando junto ao Mercado Pago...' : 'Gerar Novo Boleto'}
              </button>
              <button
                type="button"
                className={styles.btnSecondary}
                disabled={busy}
                onClick={() => {
                  setForm({ ...defaultFormValues });
                  setPayment(null);
                  setTicket(null);
                  setMessage('Formulário restaurado com valores padrão.');
                  setError(false);
                }}
              >
                Limpar Formulário
              </button>
            </div>
          </form>

          {/* Quick Result Panel if a payment was created */}
          {payment && (
            <div style={{ marginTop: '24px', borderTop: '1px solid #e2e8f0', paddingTop: '20px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <h3 style={{ margin: 0, fontSize: '16px' }}>Resultado da Emissão:</h3>
                  <span className={`${styles.statusBadge} ${getStatusBadgeClass(payment.status)}`}>
                    {payment.status}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: '10px' }}>
                  <button
                    type="button"
                    className={styles.btnSecondarySmall}
                    disabled={busy}
                    onClick={() => void run(() => consult())}
                  >
                    Consultar Atualização
                  </button>
                  {link ? (
                    <a className={styles.btnLink} href={link} target="_blank" rel="noopener noreferrer">
                      Abrir Boleto Oficial ↗
                    </a>
                  ) : null}
                </div>
              </div>

              <dl className={styles.detailsGrid}>
                <div>
                  <dt>ID Interno</dt>
                  <dd>{payment.id}</dd>
                </div>
                <div>
                  <dt>ID Ordem Mercado Pago</dt>
                  <dd>{payment.orderId || 'Não confirmado'}</dd>
                </div>
                <div>
                  <dt>Valor</dt>
                  <dd>{Number(payment.amount).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</dd>
                </div>
                <div>
                  <dt>Data de Criação</dt>
                  <dd>{new Date(payment.createdAt).toLocaleString('pt-BR')}</dd>
                </div>
                <div>
                  <dt>Data de Vencimento</dt>
                  <dd>{payment.expiresAt ? new Date(payment.expiresAt).toLocaleString('pt-BR') : 'Não informada'}</dd>
                </div>
              </dl>
            </div>
          )}
        </section>
      )}

      {/* Tab 2: Histórico & Conciliação */}
      {activeTab === 'historico' && (
        <section className={styles.card}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
            <div>
              <h2 className={styles.cardTitle}>Histórico de Tentativas e Transações</h2>
              <p className={styles.cardDescription} style={{ margin: 0 }}>
                Visualização paginada das cobranças persistidas no banco relacional PostgreSQL.
              </p>
            </div>
            <button
              type="button"
              className={styles.btnSecondary}
              disabled={busy || !username || !password}
              onClick={() => void run(() => loadHistory(history?.page || 0))}
            >
              Atualizar Lista
            </button>
          </div>

          {/* Quick Lookup Bar */}
          <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap', marginBottom: '16px' }}>
            <input
              type="text"
              style={{
                padding: '8px 12px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '13px',
                minWidth: '320px',
              }}
              placeholder="Consultar por UUID do pagamento..."
              value={paymentId}
              onChange={(e) => setPaymentId(e.target.value)}
            />
            <button
              type="button"
              className={styles.btnPrimary}
              disabled={busy || !paymentId || !username || !password}
              onClick={() => void run(() => consult())}
            >
              Consultar Status
            </button>
          </div>

          {/* Table */}
          {!history ? (
            <p style={{ color: '#64748b' }}>Carregando histórico do servidor...</p>
          ) : !history.payments.length ? (
            <p style={{ color: '#64748b' }}>Nenhuma tentativa registrada nesta página.</p>
          ) : (
            <div className={styles.tableWrapper}>
              <table>
                <thead>
                  <tr>
                    <th>ID Interno</th>
                    <th>Valor</th>
                    <th>Data</th>
                    <th>Status</th>
                    <th>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {history.payments.map((item) => (
                    <tr key={item.id}>
                      <td style={{ fontFamily: 'monospace' }}>{item.id}</td>
                      <td style={{ fontWeight: 600 }}>
                        {Number(item.amount).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                      </td>
                      <td>{new Date(item.createdAt).toLocaleString('pt-BR')}</td>
                      <td>
                        <span className={`${styles.statusBadge} ${getStatusBadgeClass(item.status)}`}>
                          {item.status}
                        </span>
                      </td>
                      <td>
                        <div className={styles.tableActions}>
                          <button
                            type="button"
                            className={styles.tableBtn}
                            disabled={busy}
                            onClick={() => void run(() => selectPayment(item.id))}
                          >
                            Detalhes
                          </button>
                          <button
                            type="button"
                            className={styles.tableBtn}
                            disabled={busy}
                            onClick={() => void run(() => consult(item.id))}
                          >
                            Sincronizar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {history && (
            <div className={styles.paginationRow}>
              <button
                type="button"
                className={styles.btnSecondarySmall}
                disabled={busy || history.page === 0}
                onClick={() => void run(() => loadHistory(history.page - 1))}
              >
                ← Anterior
              </button>
              <span>Página {history.page + 1}</span>
              <button
                type="button"
                className={styles.btnSecondarySmall}
                disabled={busy || !history.hasNext}
                onClick={() => void run(() => loadHistory(history.page + 1))}
              >
                Próxima →
              </button>
            </div>
          )}

          {/* Detailed View if selected */}
          {payment && (
            <div style={{ marginTop: '24px', borderTop: '1px solid #e2e8f0', paddingTop: '16px' }}>
              <h3 style={{ fontSize: '15px', fontWeight: 600, marginBottom: '8px' }}>
                Detalhes da Cobrança Selecionada:
              </h3>
              <dl className={styles.detailsGrid}>
                <div>
                  <dt>ID Interno</dt>
                  <dd>{payment.id}</dd>
                </div>
                <div>
                  <dt>ID Mercado Pago</dt>
                  <dd>{payment.orderId || 'Não confirmado'}</dd>
                </div>
                <div>
                  <dt>Valor</dt>
                  <dd>{Number(payment.amount).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</dd>
                </div>
                <div>
                  <dt>Status Atual</dt>
                  <dd>
                    <span className={`${styles.statusBadge} ${getStatusBadgeClass(payment.status)}`}>
                      {payment.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>Data de Criação</dt>
                  <dd>{new Date(payment.createdAt).toLocaleString('pt-BR')}</dd>
                </div>
                <div>
                  <dt>Última Atualização</dt>
                  <dd>{new Date(payment.updatedAt).toLocaleString('pt-BR')}</dd>
                </div>
              </dl>
              {link && (
                <div style={{ marginTop: '12px' }}>
                  <a className={styles.btnLink} href={link} target="_blank" rel="noopener noreferrer">
                    Visualizar Boleto Oficial no Provedor ↗
                  </a>
                </div>
              )}
            </div>
          )}
        </section>
      )}

      {/* Tab 3: Webhooks & Diagnóstico */}
      {activeTab === 'webhook' && (
        <section className={styles.card}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
            <div>
              <h2 className={styles.cardTitle}>Status de Integração dos Webhooks</h2>
              <p className={styles.cardDescription} style={{ margin: 0 }}>
                Validação do canal de recebimento assíncrono de notificações de pagamento.
              </p>
            </div>
            <button
              type="button"
              className={styles.btnSecondary}
              disabled={busy || !username || !password}
              onClick={() => void run(refreshWebhook)}
            >
              Verificar Conexão
            </button>
          </div>

          <dl className={styles.detailsGrid}>
            <div>
              <dt>Serviço de Webhooks</dt>
              <dd>
                <span className={`${styles.statusBadge} ${webhook?.enabled ? styles.statusPago : styles.statusFalha}`}>
                  {webhook ? (webhook.enabled ? 'Habilitado' : 'Desabilitado') : 'Não consultado'}
                </span>
              </dd>
            </div>
            <div>
              <dt>Segredo HMAC Configurado</dt>
              <dd>
                <span className={`${styles.statusBadge} ${webhook?.configured ? styles.statusPago : styles.statusFalha}`}>
                  {webhook ? (webhook.configured ? 'Configurado' : 'Pendente') : 'Não consultado'}
                </span>
              </dd>
            </div>
            <div>
              <dt>Endpoint Receptor</dt>
              <dd><code>POST /api/webhooks/mercadopago</code></dd>
            </div>
            <div>
              <dt>Último Status Conhecido</dt>
              <dd>
                {payment ? (
                  <span className={`${styles.statusBadge} ${getStatusBadgeClass(payment.status)}`}>
                    {payment.status}
                  </span>
                ) : (
                  'Nenhum pagamento consultado'
                )}
              </dd>
            </div>
          </dl>

          <div style={{ marginTop: '20px', padding: '16px', borderRadius: '10px', background: 'var(--color-info-bg, #eff6ff)', border: '1px solid #bfdbfe', fontSize: '13px', lineHeight: 1.6 }}>
            <strong>Como testar notificações locais com ngrok:</strong>
            <ol style={{ margin: '8px 0 0 16px', padding: 0 }}>
              <li>Inicie o túnel com o comando: <code>bash scripts/pagamentos-dev.sh</code></li>
              <li>No painel do Mercado Pago Developers, configure o webhook apontando para <code>https://&lt;seu-ngrok&gt;/api/webhooks/mercadopago</code></li>
              <li>Habilite o evento de <strong>Order</strong> e envie uma notificação de teste pelo painel do Mercado Pago.</li>
              <li>O backend validará a assinatura HMAC e atualizará automaticamente o status no PostgreSQL.</li>
            </ol>
          </div>
        </section>
      )}
    </main>
  );
}
