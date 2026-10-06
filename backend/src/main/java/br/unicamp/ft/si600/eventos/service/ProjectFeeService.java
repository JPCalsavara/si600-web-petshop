package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.CalculatedFeeResponse;
import br.unicamp.ft.si600.eventos.entity.AreaPricingMode;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectFeeService {
    private final ProjectRepository projectRepository;
    private final FeeRepository feeRepository;

    public ProjectFeeService(ProjectRepository projectRepository, FeeRepository feeRepository) {
        this.projectRepository = projectRepository;
        this.feeRepository = feeRepository;
    }

    @Transactional(readOnly = true)
    public List<CalculatedFeeResponse> calculateFeesForProject(UUID projectId, Actor actor) {
        Project project = loadAuthorizedProject(projectId, actor);

        if (project.getStatus() == ProjectStatus.AGUARDANDO_PDF ||
            project.getStatus() == ProjectStatus.PDF_EM_ANALISE ||
            project.getStatus() == ProjectStatus.REPROVADO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "As taxas só podem ser visualizadas após a aprovação da Planta.");
        }

        BigDecimal areaM2 = project.getBoothAreaM2();
        if (areaM2 == null) {
            areaM2 = BigDecimal.ZERO;
        }

        List<Fee> activeFees = feeRepository.findAllByActiveTrue();

        BigDecimal finalAreaM2 = areaM2;
        return activeFees.stream()
                .map(fee -> calculateFee(fee, finalAreaM2, project))
                .toList();
    }

    private CalculatedFeeResponse calculateFee(Fee fee, BigDecimal areaM2, Project project) {
        BigDecimal calculatedValue = null;

        if (fee.getType() == FeeType.FIXA) {
            calculatedValue = fee.getAmount();
        } else if (fee.getType() == FeeType.POR_METRAGEM) {
            if (fee.getAreaPricingMode() == AreaPricingMode.VALOR_POR_M2) {
                calculatedValue = areaM2.multiply(fee.getAmountPerM2());
            } else if (fee.getAreaPricingMode() == AreaPricingMode.UNIDADES_POR_INTERVALO) {
                BigDecimal units = areaM2.divide(fee.getAreaPerUnitM2(), 0, RoundingMode.CEILING);
                calculatedValue = units.multiply(fee.getUnitAmount());
            }
        } else if (fee.getType() == FeeType.VARIAVEL) {
            // For now, variable fees do not have a calculated value until input
            calculatedValue = null;
        }

        return new CalculatedFeeResponse(
                fee.getId(),
                fee.getName(),
                fee.getType(),
                calculatedValue,
                fee.getMeasurementUnit(),
                "PENDENTE", // Dummy status until Payment implementation
                project.getPaymentDeadline()
        );
    }

    private Project loadAuthorizedProject(UUID projectId, Actor actor) {
        if (actor.role() == ActorRole.CLIENT) {
            return projectRepository.findByIdAndOwnerId(projectId, actor.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
    }
}
