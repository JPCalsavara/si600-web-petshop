package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.CalculatedFeeResponse;
import br.unicamp.ft.si600.eventos.dto.FeePaymentResponse;
import br.unicamp.ft.si600.eventos.dto.FeeQuantityRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectFeeResponse;
import br.unicamp.ft.si600.eventos.dto.PendingQuoteResponse;
import br.unicamp.ft.si600.eventos.dto.QuoteRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectFeeConfigurationRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectFeeConfigurationResponse;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.FeePaymentService;
import br.unicamp.ft.si600.eventos.service.ProjectFeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/fees")
public class ProjectFeeController {
    private final ProjectFeeService service;
    private final FeePaymentService paymentService;
    private final ActorResolver actorResolver;

    public ProjectFeeController(ProjectFeeService service, FeePaymentService paymentService,
                                ActorResolver actorResolver) {
        this.service = service;
        this.paymentService = paymentService;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    public List<CalculatedFeeResponse> listCalculatedFees(
            @PathVariable UUID projectId, HttpServletRequest request) {
        return service.calculateFeesForProject(projectId, actorResolver.resolve(request));
    }

    @GetMapping("/configuration")
    public List<ProjectFeeConfigurationResponse> listConfiguration(@PathVariable UUID projectId,
            HttpServletRequest request) {
        return service.listProjectConfiguration(projectId, actorResolver.resolve(request));
    }

    @PutMapping("/configuration")
    public List<ProjectFeeConfigurationResponse> configure(@PathVariable UUID projectId,
            @jakarta.validation.Valid @RequestBody ProjectFeeConfigurationRequest body,
            HttpServletRequest request) {
        return service.configureProjectFees(projectId, body, actorResolver.resolve(request));
    }

    @PutMapping("/{projectFeeId}/quote")
    public PendingQuoteResponse submitQuote(@PathVariable UUID projectId, @PathVariable UUID projectFeeId,
                                            @Valid @RequestBody QuoteRequest body, HttpServletRequest request) {
        return service.submitQuote(projectId, projectFeeId, body, actorResolver.resolve(request));
    }

    @PutMapping("/{projectFeeId}/quantity")
    public ProjectFeeResponse submitQuantity(@PathVariable UUID projectId, @PathVariable UUID projectFeeId,
                                             @Valid @RequestBody FeeQuantityRequest body,
                                             HttpServletRequest request) {
        return service.submitQuantity(projectId, projectFeeId, body, actorResolver.resolve(request));
    }

    /** US-11: gera o boleto da taxa no Mercado Pago e a marca como "Pagamento gerado". */
    @PostMapping("/{projectFeeId}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public FeePaymentResponse generatePayment(@PathVariable UUID projectId, @PathVariable UUID projectFeeId,
                                              HttpServletRequest request) {
        return paymentService.generate(projectId, projectFeeId, actorResolver.resolve(request));
    }

    /** US-11: boleto/código de pagamento da taxa, para visualizar ou baixar. */
    @GetMapping("/{projectFeeId}/payment")
    public FeePaymentResponse getPayment(@PathVariable UUID projectId, @PathVariable UUID projectFeeId,
                                         HttpServletRequest request) {
        return paymentService.getBoleto(projectId, projectFeeId, actorResolver.resolve(request));
    }
}
