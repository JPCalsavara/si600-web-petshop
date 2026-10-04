package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.CreateProjectRequest;
import br.unicamp.ft.si600.eventos.dto.DecisionRequest;
import br.unicamp.ft.si600.eventos.dto.DownloadUrlResponse;
import br.unicamp.ft.si600.eventos.dto.ProjectResponse;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.ProjectPdfService;
import br.unicamp.ft.si600.eventos.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
public class ProjectPdfController {
    private final ProjectPdfService service;
    private final ProjectService projectService;
    private final ActorResolver actorResolver;

    public ProjectPdfController(ProjectPdfService service, ProjectService projectService,
                                ActorResolver actorResolver) {
        this.service = service;
        this.projectService = projectService;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.create(body, actorResolver.resolve(request)));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable UUID projectId, HttpServletRequest request) {
        return service.getClientProject(projectId, actorResolver.resolve(request));
    }

    @PostMapping(value = "/{projectId}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectResponse submitPdf(
            @PathVariable UUID projectId,
            @RequestParam("areaM2") @DecimalMin(value = "0.01", message = "A metragem deve ser maior que zero") BigDecimal areaM2,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        return service.submitPdf(projectId, areaM2, file, actorResolver.resolve(request));
    }

    @GetMapping("/{projectId}/pdf/download")
    public DownloadUrlResponse downloadPdf(@PathVariable UUID projectId, HttpServletRequest request) {
        return service.createDownloadUrl(projectId, actorResolver.resolve(request));
    }

    @GetMapping("/admin")
    public List<ProjectResponse> listAdmin(
            @RequestParam(required = false) ProjectStatus status,
            HttpServletRequest request) {
        return service.listForAdmin(status, actorResolver.resolve(request));
    }

    @PostMapping("/admin/{projectId}/approve")
    public ProjectResponse approve(@PathVariable UUID projectId, HttpServletRequest request) {
        return service.approve(projectId, actorResolver.resolve(request));
    }

    @PostMapping("/admin/{projectId}/reject")
    public ProjectResponse reject(
            @PathVariable UUID projectId,
            @Valid @RequestBody DecisionRequest decisionRequest,
            HttpServletRequest request) {
        return service.reject(projectId, decisionRequest, actorResolver.resolve(request));
    }
}
