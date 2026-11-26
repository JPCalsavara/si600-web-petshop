package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.RelatorioTaxasReceitaResponse;
import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.RelatorioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * US-13: relatório de arrecadação por tipo de taxa.
 * Acessível exclusivamente por ADMIN.
 */
@RestController
@RequestMapping("/admin/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final ActorResolver actorResolver;

    public RelatorioController(RelatorioService relatorioService, ActorResolver actorResolver) {
        this.relatorioService = relatorioService;
        this.actorResolver = actorResolver;
    }

    /**
     * GET /api/admin/relatorios/taxas-receita
     *
     * Parâmetros opcionais:
     * - projetoId: UUID do projeto (filtra somente as taxas desse projeto)
     * - dataInicio, dataFim: intervalo ISO-8601 (YYYY-MM-DD) baseado em project.createdAt
     * - status: um ou mais valores de ProjectFeeStatus (ex: ?status=PAGAMENTO_GERADO&status=COTADA)
     */
    @GetMapping("/taxas-receita")
    public RelatorioTaxasReceitaResponse taxasReceita(
            @RequestParam(required = false) UUID projetoId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<ProjectFeeStatus> status,
            HttpServletRequest request
    ) {
        Actor actor = actorResolver.resolve(request);
        if (actor.role() != ActorRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Acesso restrito a administradores.");
        }

        return relatorioService.gerarRelatorio(projetoId, dataInicio, dataFim, status);
    }
}
