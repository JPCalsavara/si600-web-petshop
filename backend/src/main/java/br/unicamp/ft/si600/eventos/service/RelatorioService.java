package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.RelatorioLinhaResponse;
import br.unicamp.ft.si600.eventos.dto.RelatorioResumoResponse;
import br.unicamp.ft.si600.eventos.dto.RelatorioTaxasReceitaResponse;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * US-13: agrega as taxas de projetos por nome da taxa (Fee.name) e por status efetivo (ProjectFeeStatus),
 * calculando os valores usando a mesma lógica do FeeCalculator, sem armazenar valores intermediários.
 *
 * Filtros opcionais: projetoId, dataInicio e dataFim (baseados em project.createdAt).
 * Filtro de status: lista de ProjectFeeStatus; se vazio retorna todos.
 *
 * Totais reais (resumo.totalGeral) contam apenas {@link #STATUS_CONTABILIZADOS}; as linhas dos demais
 * status são informativas (valor calculado ou cotado, ainda sem pagamento).
 */
@Service
public class RelatorioService {

    /**
     * Status que entram nos totais reais: só o que já tem pagamento gerado ou pago (regra da issue #27).
     * Os demais status aparecem no relatório, mas apenas como informação.
     *
     * TODO(US-12, issue #26): incluir ProjectFeeStatus.PAGO aqui quando a US-12 criar esse status.
     */
    private static final Set<ProjectFeeStatus> STATUS_CONTABILIZADOS =
            EnumSet.of(ProjectFeeStatus.PAGAMENTO_GERADO);

    private final ProjectFeeRepository projectFeeRepository;
    private final ProjectRepository projectRepository;

    public RelatorioService(ProjectFeeRepository projectFeeRepository,
                             ProjectRepository projectRepository) {
        this.projectFeeRepository = projectFeeRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public RelatorioTaxasReceitaResponse gerarRelatorio(
            UUID projetoId,
            LocalDate dataInicio,
            LocalDate dataFim,
            List<ProjectFeeStatus> statuses
    ) {
        // Validação: datas devem ser fornecidas em par
        if ((dataInicio == null) != (dataFim == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Informe dataInicio e dataFim juntos ou deixe ambos em branco.");
        }
        if (dataInicio != null && dataInicio.isAfter(dataFim)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "dataInicio deve ser anterior ou igual a dataFim.");
        }

        // Validação: projeto existe se fornecido
        if (projetoId != null) {
            projectRepository.findById(projetoId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Projeto não encontrado: " + projetoId));
        }

        // Carrega todas as ProjectFee com project e fee já em memória
        List<ProjectFee> todas = projectFeeRepository.findAllWithProjectAndFee();

        // Aplica filtros
        List<ProjectFee> filtradas = todas.stream()
                .filter(pf -> projetoId == null || pf.getProject().getId().equals(projetoId))
                .filter(pf -> {
                    if (dataInicio == null) return true;
                    if (pf.getProject().getCreatedAt() == null) return false;
                    LocalDate criacao = pf.getProject().getCreatedAt().toLocalDate();
                    return !criacao.isBefore(dataInicio) && !criacao.isAfter(dataFim);
                })
                .filter(pf -> {
                    if (statuses == null || statuses.isEmpty()) return true;
                    return statuses.contains(pf.effectiveStatus());
                })
                .toList();

        // Agrupa por (nomeTaxa, statusEfetivo)
        Map<String, Map<ProjectFeeStatus, List<ProjectFee>>> agrupado = new TreeMap<>();
        for (ProjectFee pf : filtradas) {
            String nome = pf.getFee().getName();
            ProjectFeeStatus status = pf.effectiveStatus();
            agrupado
                    .computeIfAbsent(nome, k -> new LinkedHashMap<>())
                    .computeIfAbsent(status, k -> new ArrayList<>())
                    .add(pf);
        }

        // Monta linhas
        List<RelatorioLinhaResponse> linhas = new ArrayList<>();
        for (Map.Entry<String, Map<ProjectFeeStatus, List<ProjectFee>>> entry : agrupado.entrySet()) {
            String nomeTaxa = entry.getKey();
            for (Map.Entry<ProjectFeeStatus, List<ProjectFee>> statusEntry : entry.getValue().entrySet()) {
                ProjectFeeStatus status = statusEntry.getKey();
                List<ProjectFee> grupo = statusEntry.getValue();

                long quantidade = grupo.size();
                BigDecimal totalValor = grupo.stream()
                        .map(pf -> calcularValor(pf))
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);

                linhas.add(new RelatorioLinhaResponse(nomeTaxa, status, quantidade, totalValor));
            }
        }

        // Calcula resumo
        // totalGeral soma apenas os status contabilizados; DISPONIVEL e COTADA são valores ainda não cobrados.
        BigDecimal totalGeral = linhas.stream()
                .filter(l -> STATUS_CONTABILIZADOS.contains(l.statusPagamento()))
                .map(RelatorioLinhaResponse::totalValorCalculado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGerado = linhas.stream()
                .filter(l -> l.statusPagamento() == ProjectFeeStatus.PAGAMENTO_GERADO)
                .map(RelatorioLinhaResponse::totalValorCalculado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDisponivel = linhas.stream()
                .filter(l -> l.statusPagamento() == ProjectFeeStatus.DISPONIVEL)
                .map(RelatorioLinhaResponse::totalValorCalculado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCotada = linhas.stream()
                .filter(l -> l.statusPagamento() == ProjectFeeStatus.COTADA)
                .map(RelatorioLinhaResponse::totalValorCalculado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        RelatorioResumoResponse resumo = new RelatorioResumoResponse(
                totalGeral, totalGerado, totalDisponivel, totalCotada
        );

        return new RelatorioTaxasReceitaResponse(linhas, resumo);
    }

    /**
     * Calcula o valor monetário de uma ProjectFee usando a mesma lógica do FeeCalculator.
     * Taxas variáveis usam quotedAmount se disponível, senão null (não contam no total).
     */
    private BigDecimal calcularValor(ProjectFee pf) {
        if (pf.getFee().getType() == FeeType.VARIAVEL) {
            return pf.getQuotedAmount();
        }
        FeeCalculator.Result result = FeeCalculator.calculate(
                pf.getFee(),
                pf.getProject().getBoothAreaM2()
        );
        return result.value();
    }
}
