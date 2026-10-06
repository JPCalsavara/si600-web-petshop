package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.CalculatedFeeResponse;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.ProjectFeeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/fees")
public class ProjectFeeController {
    private final ProjectFeeService service;
    private final ActorResolver actorResolver;

    public ProjectFeeController(ProjectFeeService service, ActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    public List<CalculatedFeeResponse> listCalculatedFees(
            @PathVariable UUID projectId, HttpServletRequest request) {
        return service.calculateFeesForProject(projectId, actorResolver.resolve(request));
    }
}
