package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.FeeQuantityRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectFeeResponse;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.exception.ApiException;
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
import java.util.UUID;

/** US-06: o Cliente informa (e altera) a quantidade das taxas variáveis do próprio projeto. */
@Service
public class ProjectFeeService {
    private final ProjectRepository projects;
    private final ProjectFeeRepository projectFees;

    public ProjectFeeService(ProjectRepository projects, ProjectFeeRepository projectFees) {
        this.projects = projects;
        this.projectFees = projectFees;
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
