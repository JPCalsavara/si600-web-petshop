package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.CalculatedFeeResponse;
import br.unicamp.ft.si600.eventos.dto.FeeQuantityRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectFeeResponse;
import br.unicamp.ft.si600.eventos.dto.PendingQuoteResponse;
import br.unicamp.ft.si600.eventos.dto.QuoteRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectFeeConfigurationRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectFeeConfigurationResponse;
import br.unicamp.ft.si600.eventos.entity.AreaPricingMode;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectFeeService {
    private final ProjectRepository projects;
    private final FeeRepository feeRepository;
    private final ProjectFeeRepository projectFees;

    public ProjectFeeService(ProjectRepository projects, FeeRepository feeRepository, ProjectFeeRepository projectFees) {
        this.projects = projects;
        this.feeRepository = feeRepository;
        this.projectFees = projectFees;
    }

    @Transactional(readOnly = true)
    public List<CalculatedFeeResponse> calculateFeesForProject(UUID projectId, Actor actor) {
        Project project = loadAuthorizedProject(projectId, actor);

        if (project.getStatus() == ProjectStatus.AGUARDANDO_PDF ||
            project.getStatus() == ProjectStatus.PDF_EM_ANALISE ||
            project.getStatus() == ProjectStatus.REPROVADO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "As taxas só podem ser visualizadas após a aprovação da Planta.");
        }

        return projectFees.findAllByProjectIdOrderByFeeNameAsc(projectId).stream()
                .map(pf -> toCalculated(pf, project))
                .toList();
    }

    private CalculatedFeeResponse toCalculated(ProjectFee pf, Project project) {
        Fee fee = pf.getFee();
        ProjectFeeStatus status = pf.effectiveStatus();
        BigDecimal value;
        BigDecimal quantity;
        String unit;
        if (fee.getType() == FeeType.VARIAVEL) {
            // O Cliente só vê o valor depois que o Admin cota; enquanto aguarda recotação não há valor vigente.
            boolean quoted = status == ProjectFeeStatus.COTADA || status == ProjectFeeStatus.PAGAMENTO_GERADO;
            value = quoted ? pf.getQuotedAmount() : null;
            quantity = pf.getQuantity();
            unit = fee.getMeasurementUnit();
        } else {
            FeeCalculator.Result result = FeeCalculator.calculate(fee, project.getBoothAreaM2());
            value = result.value();
            quantity = result.quantity();
            unit = result.unit();
        }
        boolean quantityEditable = fee.getType() == FeeType.VARIAVEL && !pf.isPaymentGenerated();
        return new CalculatedFeeResponse(pf.getId(), fee.getId(), fee.getName(), fee.getDescription(),
                fee.getType(), value, quantity, unit, status.name(), quantityEditable, project.getPaymentDeadline());
    }

    private Project loadAuthorizedProject(UUID projectId, Actor actor) {
        if (actor.role() == ActorRole.CLIENT) {
            return projects.findByIdAndOwnerId(projectId, actor.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        }
        return projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
    }

    @Transactional
    public ProjectFeeResponse submitQuantity(UUID projectId, UUID projectFeeId,
                                             FeeQuantityRequest request, Actor actor) {
        requireClient(actor);
        Project project = loadOwnProject(projectId, actor);
        requireFeesReleased(project);

        ProjectFee projectFee = projectFees.findForUpdate(projectFeeId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxa do projeto não encontrada."));
        if (projectFee.getFee().getType() != FeeType.VARIAVEL) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Somente taxas variáveis aceitam quantidade informada pelo cliente.");
        }
        if (projectFee.isPaymentGenerated()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "O pagamento desta taxa já foi gerado e a quantidade não pode mais ser alterada.");
        }

        BigDecimal quantity = request.quantity().setScale(2, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0) { // ex.: 0.001 arredonda para 0.00
            throw new ApiException(HttpStatus.BAD_REQUEST, "A quantidade deve ser maior que zero.");
        }
        projectFee.submitQuantity(quantity, OffsetDateTime.now());
        return ProjectFeeResponse.from(projectFees.saveAndFlush(projectFee));
    }


    @Transactional(readOnly = true)
    public List<ProjectFeeConfigurationResponse> listProjectConfiguration(UUID projectId, Actor actor) {
        requireAdmin(actor);
        Project project = projects.findById(projectId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        requireConfigurationWindow(project);
        return projectFees.findAllByProjectIdOrderByFeeNameAsc(projectId).stream()
                .map(ProjectFeeConfigurationResponse::from).toList();
    }

    @Transactional
    public List<ProjectFeeConfigurationResponse> configureProjectFees(UUID projectId,
            ProjectFeeConfigurationRequest request, Actor actor) {
        requireAdmin(actor);
        Project project = projects.findById(projectId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        requireConfigurationWindow(project);
        List<UUID> requested = request.feeIds() == null ? List.of() : request.feeIds();
        if (requested.stream().distinct().count() != requested.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A mesma taxa não pode ser selecionada mais de uma vez.");
        }
        var current = projectFees.findAllByProjectIdOrderByFeeNameAsc(projectId);
        var currentByFeeId = current.stream().collect(java.util.stream.Collectors.toMap(pf -> pf.getFee().getId(), pf -> pf));
        var wanted = new java.util.HashSet<>(requested);
        for (ProjectFee pf : current) {
            if (!wanted.contains(pf.getFee().getId())) {
                if (pf.getQuantity() != null || pf.getQuotedAmount() != null || pf.isPaymentGenerated()
                        || pf.getStatus() == ProjectFeeStatus.COTADA
                        || pf.getStatus() == ProjectFeeStatus.PAGAMENTO_GERADO) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "Não é possível remover a taxa '" + pf.getFee().getName() + "': ela já possui quantidade, cotação ou pagamento associado.");
                }
                projectFees.delete(pf);
            }
        }
        if (!requested.isEmpty()) {
            var fees = feeRepository.findAllById(wanted);
            // Uma taxa já vinculada que foi desativada depois continua válida; só novos vínculos exigem taxa ativa.
            if (fees.size() != wanted.size()
                    || fees.stream().anyMatch(f -> !f.isActive() && !currentByFeeId.containsKey(f.getId()))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Uma ou mais taxas não existem ou estão inativas no catálogo.");
            }
            for (Fee fee : fees) {
                if (!currentByFeeId.containsKey(fee.getId())) projectFees.save(new ProjectFee(project, fee));
            }
        }
        projectFees.flush();
        return projectFees.findAllByProjectIdOrderByFeeNameAsc(projectId).stream()
                .map(ProjectFeeConfigurationResponse::from).toList();
    }

    private void requireConfigurationWindow(Project project) {
        if (project.getStatus() != ProjectStatus.APROVADO && project.getStatus() != ProjectStatus.AGUARDANDO_PAGAMENTO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "As taxas só podem ser configuradas após a aprovação do PDF do estande.");
        }
    }

    @Transactional(readOnly = true)
    public List<PendingQuoteResponse> listQuoteableFees(Actor actor) {
        requireAdmin(actor);
        // Só taxas variáveis com quantidade informada: aguardando cotação ou já cotadas (recotação permitida).
        return projectFees.findQuoteable(
                        List.of(ProjectStatus.APROVADO, ProjectStatus.AGUARDANDO_PAGAMENTO),
                        FeeType.VARIAVEL,
                        List.of(ProjectFeeStatus.AGUARDANDO_COTACAO, ProjectFeeStatus.COTADA))
                .stream()
                .map(PendingQuoteResponse::from)
                .toList();
    }

    @Transactional
    public PendingQuoteResponse submitQuote(UUID projectId, UUID projectFeeId, QuoteRequest request, Actor actor) {
        requireAdmin(actor);
        Project project = projects.findById(projectId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        requireFeesReleased(project);
        ProjectFee projectFee = projectFees.findForUpdate(projectFeeId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxa do projeto não encontrada."));
        if (projectFee.getFee().getType() != FeeType.VARIAVEL) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Taxas fixas e por metragem têm o valor calculado pelo catálogo e não são cotadas.");
        }
        if (projectFee.isPaymentGenerated()) {
            throw new ApiException(HttpStatus.CONFLICT, "A taxa não pode ser recotada após a geração do pagamento.");
        }
        if (projectFee.getQuantity() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A taxa variável precisa ter quantidade informada antes da cotação.");
        }
        if (projectFee.getStatus() != ProjectFeeStatus.AGUARDANDO_COTACAO
                && projectFee.getStatus() != ProjectFeeStatus.COTADA) {
            throw new ApiException(HttpStatus.CONFLICT, "A taxa não está disponível para cotação neste estado.");
        }
        projectFee.quote(request.amount().setScale(2, RoundingMode.HALF_UP), actor.id(), OffsetDateTime.now());
        return PendingQuoteResponse.from(projectFees.saveAndFlush(projectFee));
    }

    private void requireAdmin(Actor actor) {
        if (actor == null || actor.role() != ActorRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Você não possui permissão para esta operação.");
        }
    }

    private Project loadOwnProject(UUID projectId, Actor actor) {
        return projects.findByIdAndOwnerId(projectId, actor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
    }

    private void requireFeesReleased(Project project) {
        ProjectStatus status = project.getStatus();
        if (status != ProjectStatus.APROVADO && status != ProjectStatus.AGUARDANDO_PAGAMENTO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "As taxas só ficam disponíveis depois que o PDF do estande for aprovado.");
        }
    }

    private void requireClient(Actor actor) {
        if (actor.role() != ActorRole.CLIENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Você não possui permissão para esta operação.");
        }
    }
}
