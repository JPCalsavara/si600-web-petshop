package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.FeeQuantityRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectFeeResponse;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.ProjectFeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/{projectFeeId}/quantity")
    public ProjectFeeResponse submitQuantity(@PathVariable UUID projectId, @PathVariable UUID projectFeeId,
                                             @Valid @RequestBody FeeQuantityRequest body,
                                             HttpServletRequest request) {
        return service.submitQuantity(projectId, projectFeeId, body, actorResolver.resolve(request));
    }
}
