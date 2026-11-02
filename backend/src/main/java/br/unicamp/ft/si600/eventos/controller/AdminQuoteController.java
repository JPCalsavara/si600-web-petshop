package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.PendingQuoteResponse;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.ProjectFeeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/admin/quotes")
public class AdminQuoteController {
    private final ProjectFeeService service;
    private final ActorResolver actorResolver;

    public AdminQuoteController(ProjectFeeService service, ActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    public List<PendingQuoteResponse> list(HttpServletRequest request) {
        return service.listQuoteableFees(actorResolver.resolve(request));
    }
}
