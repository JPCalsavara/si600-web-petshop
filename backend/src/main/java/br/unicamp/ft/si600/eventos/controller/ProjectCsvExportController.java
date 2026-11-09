package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.ProjectCsvExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/export/csv")
public class ProjectCsvExportController {

    private static final MediaType CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ProjectCsvExportService csvExportService;
    private final ActorResolver actorResolver;

    public ProjectCsvExportController(ProjectCsvExportService csvExportService, ActorResolver actorResolver) {
        this.csvExportService = csvExportService;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable UUID projectId,
            HttpServletRequest request) {
        ProjectCsvExportService.CsvExportResult result =
                csvExportService.exportProjectCsv(projectId, actorResolver.resolve(request));

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(result.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(result.content());
    }
}
