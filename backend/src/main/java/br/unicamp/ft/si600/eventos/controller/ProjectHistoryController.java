package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.ProjectHistoryEventResponse;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.ProjectHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/history")
public class ProjectHistoryController {

    private final ProjectHistoryService historyService;
    private final ActorResolver actorResolver;

    public ProjectHistoryController(ProjectHistoryService historyService, ActorResolver actorResolver) {
        this.historyService = historyService;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    public List<ProjectHistoryEventResponse> listHistory(
            @PathVariable UUID projectId,
            HttpServletRequest request) {
        return historyService.listHistory(projectId, actorResolver.resolve(request));
    }
}
