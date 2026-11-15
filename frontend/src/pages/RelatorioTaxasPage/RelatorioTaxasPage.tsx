import { useCallback, useEffect, useState } from 'react';
import {
  buscarProjetosParaFiltro,
  buscarRelatorioTaxas,
  formatarBRL,
  STATUS_LABELS,
  TODOS_OS_STATUSES,
} from '../../services/relatorioService';
import type {
  FiltroRelatorio,
  RelatorioLinha,
  RelatorioResumo,
  RelatorioTaxasReceita,
  StatusPagamento,
} from '../../services/relatorioService';
import type { Project } from '../../types';
import { ApiRequestError } from '../../services/api';
import styles from './RelatorioTaxasPage.module.scss';

/* --------------------------------------------------------------------------
   Badge de status
   -------------------------------------------------------------------------- */
function StatusBadge({ status }: { status: StatusPagamento }) {
  const label = STATUS_LABELS[status] ?? status;
  switch (status) {
    case 'PAGAMENTO_GERADO':
      return <span className={styles.badgeGerado}>{label}</span>;
    case 'COTADA':
      return <span className={styles.badgeCotada}>{label}</span>;
    case 'DISPONIVEL':
      return <span className={styles.badgeDisponivel}>{label}</span>;
    default:
      return <span className={styles.badgePendente}>{label}</span>;
  }
}

/* --------------------------------------------------------------------------
   Gráfico de barras horizontais — somente taxas com valor calculado > 0
   -------------------------------------------------------------------------- */
function GraficoReceita({ linhas }: { linhas: RelatorioLinha[] }) {
  // Agrega por nome de taxa (soma todos os status)
  const porTaxa = linhas.reduce<Record<string, number>>((acc, l) => {
    if (l.totalValorCalculado > 0) {
      acc[l.nomeTaxa] = (acc[l.nomeTaxa] ?? 0) + l.totalValorCalculado;
    }
    return acc;
  }, {});

  const entradas = Object.entries(porTaxa).sort((a, b) => b[1] - a[1]);
  const totalGeral = entradas.reduce((acc, [, v]) => acc + v, 0);

  if (entradas.length === 0) {
    return (
      <p className={styles.chartEmptyState}>
        Nenhuma taxa com valor calculado disponível para exibir o gráfico.
      </p>
    );
  }

  return (
    <div className={styles.chartBars}>
      {entradas.map(([nome, valor]) => {
        const pct = totalGeral > 0 ? (valor / totalGeral) * 100 : 0;
        return (
          <div key={nome} className={styles.chartBarRow}>
            <span className={styles.chartBarLabel} title={nome}>
              {nome}
            </span>
            <div className={styles.chartBarTrack}>
              <div
                className={styles.chartBarFill}
                style={{ width: `${pct}%` }}
                role="meter"
                aria-valuenow={pct}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={`${nome}: ${pct.toFixed(1)}%`}
              />
            </div>
            <span className={styles.chartBarMeta}>
              {formatarBRL(valor)} ({pct.toFixed(1)}%)
            </span>
          </div>
        );
      })}
    </div>
  );
}

/* --------------------------------------------------------------------------
   Cards de resumo
   -------------------------------------------------------------------------- */
function CardsResumo({ resumo }: { resumo: RelatorioResumo }) {
  return (
    <div className={styles.resumoGrid}>
      <div className={styles.resumoCard}>
        <div className={styles.label}>Total Geral</div>
        <div className={styles.valor}>{formatarBRL(resumo.totalGeral)}</div>
      </div>
      <div className={`${styles.resumoCard} ${styles.resumoCardGerado}`}>
        <div className={styles.label}>Pagamentos Gerados</div>
        <div className={styles.valor}>{formatarBRL(resumo.totalPagamentoGerado)}</div>
      </div>
      <div className={`${styles.resumoCard} ${styles.resumoCardDisponivel}`}>
        <div className={styles.label}>Disponível (calculado)</div>
        <div className={styles.valor}>{formatarBRL(resumo.totalDisponivel)}</div>
      </div>
      <div className={`${styles.resumoCard} ${styles.resumoCardCotada}`}>
        <div className={styles.label}>Cotadas</div>
        <div className={styles.valor}>{formatarBRL(resumo.totalCotada)}</div>
      </div>
    </div>
  );
}

/* --------------------------------------------------------------------------
   Tabela de resultados
   -------------------------------------------------------------------------- */
function TabelaRelatorio({ linhas }: { linhas: RelatorioLinha[] }) {
  const totalQtd = linhas.reduce((acc, l) => acc + l.quantidadeTaxas, 0);
  const totalValor = linhas.reduce((acc, l) => acc + l.totalValorCalculado, 0);

  return (
    <div className={styles.tableWrap}>
      <table className={styles.table}>
        <caption>Taxas agrupadas por nome e status</caption>
        <thead>
          <tr>
            <th>Taxa</th>
            <th>Status</th>
            <th className={styles.alignRight}>Quantidade</th>
            <th className={styles.alignRight}>Total calculado (R$)</th>
          </tr>
        </thead>
        <tbody>
          {linhas.map((linha, idx) => (
            <tr key={`${linha.nomeTaxa}-${linha.statusPagamento}-${idx}`}>
              <td data-label="Taxa">
                <strong>{linha.nomeTaxa}</strong>
              </td>
              <td data-label="Status">
                <StatusBadge status={linha.statusPagamento} />
              </td>
              <td data-label="Quantidade" className={styles.alignRight}>
                {linha.quantidadeTaxas}
              </td>
              <td data-label="Total calculado (R$)" className={styles.alignRight}>
                {linha.totalValorCalculado > 0
                  ? formatarBRL(linha.totalValorCalculado)
                  : '—'}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className={styles.summaryRow}>
            <td colSpan={2} data-label="Total">
              <strong>Total consolidado</strong>
            </td>
            <td data-label="Quantidade" className={styles.alignRight}>
              <strong>{totalQtd}</strong>
            </td>
            <td data-label="Total calculado (R$)" className={styles.alignRight}>
              <strong>{formatarBRL(totalValor)}</strong>
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  );
}

/* --------------------------------------------------------------------------
   Página principal
   -------------------------------------------------------------------------- */
export function RelatorioTaxasPage() {
  const [projetos, setProjetos] = useState<Pick<Project, 'id' | 'companyName'>[]>([]);
  const [projetosErro, setProjetosErro] = useState<string | null>(null);

  const [projetoIdInput, setProjetoIdInput] = useState('');
  const [dataInicioInput, setDataInicioInput] = useState('');
  const [dataFimInput, setDataFimInput] = useState('');
  const [statusSelecionados, setStatusSelecionados] = useState<StatusPagamento[]>([]);
  const [dateRangeError, setDateRangeError] = useState<string | null>(null);

  const [relatorio, setRelatorio] = useState<RelatorioTaxasReceita | null>(null);
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [consultado, setConsultado] = useState(false);
  const [exibirGrafico, setExibirGrafico] = useState(false);

  // Carrega projetos ao montar
  useEffect(() => {
    buscarProjetosParaFiltro()
      .then(setProjetos)
      .catch(() =>
        setProjetosErro(
          'Não foi possível carregar os projetos. Recarregue a página para tentar novamente.',
        ),
      );
  }, []);

  // Validação client-side de datas
  useEffect(() => {
    if (dataInicioInput && dataFimInput && dataInicioInput > dataFimInput) {
      setDateRangeError('A data final deve ser igual ou posterior à data inicial.');
    } else {
      setDateRangeError(null);
    }
  }, [dataInicioInput, dataFimInput]);

  function toggleStatus(s: StatusPagamento) {
    setStatusSelecionados((prev) =>
      prev.includes(s) ? prev.filter((x) => x !== s) : [...prev, s],
    );
  }

  const podeSomitar = !loading && !dateRangeError && !projetosErro;

  const consultar = useCallback(async () => {
    if (!podeSomitar) return;

    const filtro: FiltroRelatorio = {};
    if (projetoIdInput) filtro.projetoId = projetoIdInput;
    if (dataInicioInput) filtro.dataInicio = dataInicioInput;
    if (dataFimInput) filtro.dataFim = dataFimInput;
    if (statusSelecionados.length > 0) filtro.status = statusSelecionados;

    setLoading(true);
    setErro(null);
    setFieldErrors({});
    setRelatorio(null);

    try {
      const resultado = await buscarRelatorioTaxas(filtro);
      setRelatorio(resultado);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        if (Object.keys(error.invalidFields).length > 0) {
          setFieldErrors(error.invalidFields);
        } else {
          setErro(error.message);
        }
      } else {
        setErro('Não foi possível carregar o relatório. Tente novamente.');
      }
    } finally {
      setLoading(false);
      setConsultado(true);
    }
  }, [podeSomitar, projetoIdInput, dataInicioInput, dataFimInput, statusSelecionados]);

  function limparFiltros() {
    setProjetoIdInput('');
    setDataInicioInput('');
    setDataFimInput('');
    setStatusSelecionados([]);
    setDateRangeError(null);
    setFieldErrors({});
    setErro(null);
    setRelatorio(null);
    setConsultado(false);
    setExibirGrafico(false);
  }

  const temFiltro =
    Boolean(projetoIdInput || dataInicioInput || dataFimInput) ||
    statusSelecionados.length > 0;
  const linhas = relatorio?.linhas ?? [];

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>Relatório de Taxas e Receita</h1>
          <p className={styles.subtitle}>
            Visualize as taxas agrupadas por tipo e status, com os valores calculados a partir
            dos dados reais do banco.
          </p>
        </div>
      </header>

      {/* Filtros */}
      <section className={styles.filtersSection} aria-label="Filtros do relatório">
        <div className={styles.filtersGrid}>
          {/* Projeto */}
          <div className={styles.filterGroup}>
            <label htmlFor="filtro-projeto" className={styles.filterLabel}>
              Projeto
            </label>
            <select
              id="filtro-projeto"
              className={styles.filterSelect}
              value={projetoIdInput}
              disabled={loading || Boolean(projetosErro)}
              onChange={(e) => setProjetoIdInput(e.target.value)}
              data-testid="select-projeto"
            >
              <option value="">Todos os projetos</option>
              {projetos.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.companyName}
                </option>
              ))}
            </select>
            {fieldErrors['projetoId'] && (
              <span className={styles.fieldError} role="alert">
                {fieldErrors['projetoId']}
              </span>
            )}
          </div>

          {/* Data inicial */}
          <div className={styles.filterGroup}>
            <label htmlFor="filtro-data-inicio" className={styles.filterLabel}>
              Data inicial (criação do projeto)
            </label>
            <input
              id="filtro-data-inicio"
              type="date"
              className={styles.filterInput}
              value={dataInicioInput}
              disabled={loading}
              onChange={(e) => setDataInicioInput(e.target.value)}
              data-testid="input-data-inicio"
            />
            {fieldErrors['dataInicio'] && (
              <span className={styles.fieldError} role="alert">
                {fieldErrors['dataInicio']}
              </span>
            )}
          </div>

          {/* Data final */}
          <div className={styles.filterGroup}>
            <label htmlFor="filtro-data-fim" className={styles.filterLabel}>
              Data final (criação do projeto)
            </label>
            <input
              id="filtro-data-fim"
              type="date"
              className={styles.filterInput}
              value={dataFimInput}
              disabled={loading}
              onChange={(e) => setDataFimInput(e.target.value)}
              data-testid="input-data-fim"
            />
            {(dateRangeError || fieldErrors['dataFim']) && (
              <span className={styles.fieldError} role="alert">
                {dateRangeError ?? fieldErrors['dataFim']}
              </span>
            )}
          </div>
        </div>

        {/* Filtro de status */}
        <div className={styles.statusFilterGroup}>
          <span className={styles.filterLabel}>Filtrar por status</span>
          <div className={styles.statusPills}>
            {TODOS_OS_STATUSES.map((s) => (
              <button
                key={s}
                type="button"
                className={`${styles.statusPill} ${statusSelecionados.includes(s) ? styles.statusPillActive : ''}`}
                onClick={() => toggleStatus(s)}
                disabled={loading}
                data-testid={`pill-status-${s}`}
              >
                {STATUS_LABELS[s]}
              </button>
            ))}
          </div>
          {statusSelecionados.length === 0 && (
            <span className={styles.statusHint}>
              Nenhum filtro de status selecionado — exibe todos os status.
            </span>
          )}
        </div>

        {projetosErro && (
          <div role="alert" className={styles.alert} style={{ marginTop: 12 }}>
            {projetosErro}
          </div>
        )}

        <div className={styles.filtersFooter}>
          {temFiltro && (
            <button
              type="button"
              className={styles.btnLimpar}
              disabled={loading}
              onClick={limparFiltros}
              data-testid="btn-limpar-filtros"
            >
              Limpar filtros
            </button>
          )}
          <button
            type="button"
            className={styles.btnConsultar}
            disabled={!podeSomitar}
            onClick={() => void consultar()}
            data-testid="btn-consultar"
          >
            {loading ? 'Consultando...' : 'Consultar'}
          </button>
        </div>
      </section>

      {/* Erro da consulta */}
      {erro && (
        <div role="alert" className={styles.alert}>
          {erro}
        </div>
      )}

      {/* Carregando */}
      {loading && (
        <div className={styles.panel}>
          <div className={styles.loadingWrap} role="status">
            Carregando relatório...
          </div>
        </div>
      )}

      {/* Resultado */}
      {!loading && relatorio && (
        <>
          <CardsResumo resumo={relatorio.resumo} />

          {/* Toggle do gráfico */}
          <label className={styles.chartToggle}>
            <input
              type="checkbox"
              checked={exibirGrafico}
              onChange={(e) => setExibirGrafico(e.target.checked)}
              data-testid="toggle-grafico"
            />
            Exibir gráfico de distribuição por tipo de taxa
          </label>

          {exibirGrafico && (
            <section className={styles.chartSection} aria-label="Gráfico de distribuição">
              <h3>Distribuição de valor calculado por tipo de taxa</h3>
              <GraficoReceita linhas={linhas} />
            </section>
          )}

          <div className={styles.panel}>
            {linhas.length === 0 ? (
              <div className={styles.emptyState} role="status">
                <p>
                  Nenhuma taxa encontrada para os filtros selecionados.
                  {statusSelecionados.length > 0 && (
                    <>
                      {' '}
                      Tente remover o filtro de status ou selecionar um status diferente (ex:{' '}
                      <strong>Disponível</strong> ou <strong>Pagamento gerado</strong>).
                    </>
                  )}
                </p>
              </div>
            ) : (
              <TabelaRelatorio linhas={linhas} />
            )}
          </div>
        </>
      )}

      {/* Estado inicial */}
      {!loading && !relatorio && !erro && !consultado && (
        <div className={styles.panel}>
          <div className={styles.emptyState}>
            <p>
              Aplique os filtros desejados e clique em <strong>Consultar</strong> para gerar o
              relatório.
            </p>
          </div>
        </div>
      )}
    </section>
  );
}
