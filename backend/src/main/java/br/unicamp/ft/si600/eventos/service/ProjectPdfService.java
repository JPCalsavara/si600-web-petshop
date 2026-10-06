package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.DecisionRequest;
import br.unicamp.ft.si600.eventos.dto.DownloadUrlResponse;
import br.unicamp.ft.si600.eventos.dto.ProjectResponse;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import br.unicamp.ft.si600.eventos.storage.ObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectPdfService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectPdfService.class);
    public static final long MAX_PDF_SIZE = 10L * 1024 * 1024;
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final int MAX_FILENAME_LENGTH = 255;
    // booth_area_m2 é NUMERIC(12,2): no máximo 10 dígitos inteiros.
    private static final BigDecimal MAX_AREA_M2 = new BigDecimal("9999999999.99");

    private final ProjectRepository repository;
    private final ObjectStorage storage;
    private final PdfDecisionNotifier notifier;

    public ProjectPdfService(ProjectRepository repository, ObjectStorage storage, PdfDecisionNotifier notifier) {
        this.repository = repository;
        this.storage = storage;
        this.notifier = notifier;
    }

    @Transactional
    public ProjectResponse submitPdf(UUID projectId, MultipartFile file, Actor actor) {
        requireRole(actor, ActorRole.CLIENT);
        validatePdf(file);

        Project project = repository.findByIdAndOwnerId(projectId, actor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));

        if (project.getStatus() != ProjectStatus.AGUARDANDO_PDF &&
                project.getStatus() != ProjectStatus.REPROVADO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O projeto não está disponível para envio ou reenvio do PDF.");
        }

        String oldKey = project.getPdfObjectKey();
        String key = "projects/" + project.getId() + "/pdf/" + UUID.randomUUID() + ".pdf";

        try {
            storage.upload(key, file.getInputStream(), file.getSize(), PDF_CONTENT_TYPE);
        } catch (IOException ex) {
            log.error("Falha ao enviar PDF do projeto {} ao object storage (key={})", projectId, key, ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Não foi possível armazenar o PDF.");
        }

        try {
            project.submitPdf(key, sanitizeFilename(file.getOriginalFilename()),
                    PDF_CONTENT_TYPE, file.getSize(), OffsetDateTime.now());
            repository.saveAndFlush(project);
        } catch (RuntimeException ex) {
            deleteQuietly(key); // o registro não aponta para o novo objeto: evita órfão
            throw ex;
        }
        registerCommitCleanup(key, oldKey);

        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getClientProject(UUID projectId, Actor actor) {
        requireRole(actor, ActorRole.CLIENT);
        Project project = repository.findByIdAndOwnerId(projectId, actor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listForAdmin(ProjectStatus status, Actor actor) {
        requireRole(actor, ActorRole.ADMIN);
        List<Project> projects = status == null
                ? repository.findAll(Sort.by(Sort.Order.asc("pdfUploadedAt"), Sort.Order.asc("id")))
                : repository.findAllByStatusOrderByPdfUploadedAtAsc(status);
        return projects.stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse createDownloadUrl(UUID projectId, Actor actor) {
        Project project = loadAuthorizedProject(projectId, actor);
        if (project.getPdfObjectKey() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Este projeto ainda não possui PDF.");
        }
        return new DownloadUrlResponse(
                storage.createDownloadUrl(project.getPdfObjectKey(), Duration.ofMinutes(15)),
                900
        );
    }

    @Transactional
    public ProjectResponse approve(UUID projectId, br.unicamp.ft.si600.eventos.dto.ApproveProjectRequest request, Actor actor) {
        requireRole(actor, ActorRole.ADMIN);
        Project project = repository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        requireStatusForDecision(project);
        project.approve(actor.id(), OffsetDateTime.now(), request.approvedAreaM2());
        Project saved = repository.save(project);
        notifier.notifyApproved(saved);
        return ProjectResponse.from(saved);
    }

    @Transactional
    public ProjectResponse reject(UUID projectId, DecisionRequest request, Actor actor) {
        requireRole(actor, ActorRole.ADMIN);
        Project project = repository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        requireStatusForDecision(project);
        String justification = request.justification().trim();
        if (justification.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A justificativa é obrigatória.");
        }
        project.reject(actor.id(), OffsetDateTime.now(), justification);
        Project saved = repository.save(project);
        notifier.notifyRejected(saved);
        return ProjectResponse.from(saved);
    }

    /**
     * O objeto antigo só é removido após o commit (se o commit falhar, o registro ainda aponta para ele);
     * se a transação for revertida, o objeto recém-enviado é removido.
     */
    private void registerCommitCleanup(String newKey, String oldKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(oldKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteQuietly(oldKey);
                } else {
                    deleteQuietly(newKey);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        if (key == null) return;
        try {
            storage.delete(key);
        } catch (RuntimeException ignored) {
            // Remoção best-effort.
        }
    }

    private Project loadAuthorizedProject(UUID projectId, Actor actor) {
        if (actor.role() == ActorRole.CLIENT) {
            return repository.findByIdAndOwnerId(projectId, actor.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
        }
        return repository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
    }

    private void requireStatusForDecision(Project project) {
        if (project.getStatus() != ProjectStatus.PDF_EM_ANALISE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A decisão só pode ser registrada quando o PDF estiver em análise.");
        }
    }

    private void requireRole(Actor actor, ActorRole role) {
        if (actor.role() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Você não possui permissão para esta operação.");
        }
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O arquivo PDF é obrigatório.");
        }
        if (file.getSize() > MAX_PDF_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O PDF deve ter no máximo 10 MB.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O arquivo deve estar no formato PDF.");
        }
        try {
            byte[] header = file.getInputStream().readNBytes(5);
            if (header.length != 5 || header[0] != '%' || header[1] != 'P' ||
                    header[2] != 'D' || header[3] != 'F' || header[4] != '-') {
                throw new ApiException(HttpStatus.BAD_REQUEST, "O conteúdo enviado não é um PDF válido.");
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Não foi possível validar o PDF.");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "planta.pdf";
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!sanitized.toLowerCase().endsWith(".pdf")) sanitized += ".pdf";
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            // pdf_original_filename é VARCHAR(255): preserva a extensão ao truncar.
            sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH - 4) + ".pdf";
        }
        return sanitized;
    }
}
