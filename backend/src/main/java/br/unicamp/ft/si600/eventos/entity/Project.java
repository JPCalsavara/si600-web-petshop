package br.unicamp.ft.si600.eventos.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_projects_owner_id", columnList = "owner_id"),
        @Index(name = "idx_projects_status", columnList = "status")
})
public class Project {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "document", nullable = false, length = 30)
    private String document;

    @Column(name = "representative_name", nullable = false, length = 200)
    private String representativeName;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "pdf_deadline", nullable = false)
    private OffsetDateTime pdfDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectStatus status = ProjectStatus.AGUARDANDO_PDF;

    @Column(name = "booth_area_m2", precision = 12, scale = 2)
    private BigDecimal boothAreaM2;

    @Column(name = "pdf_object_key", length = 500)
    private String pdfObjectKey;

    @Column(name = "pdf_original_filename", length = 255)
    private String pdfOriginalFilename;

    @Column(name = "pdf_content_type", length = 100)
    private String pdfContentType;

    @Column(name = "pdf_size_bytes")
    private Long pdfSizeBytes;

    @Column(name = "pdf_uploaded_at")
    private OffsetDateTime pdfUploadedAt;

    @Column(name = "decision_by", length = 100)
    private String decisionBy;

    @Column(name = "decision_at")
    private OffsetDateTime decisionAt;

    @Column(name = "rejection_justification", columnDefinition = "text")
    private String rejectionJustification;

    protected Project() {}

    public Project(String ownerId, String companyName, String document,
                   String representativeName, String category, OffsetDateTime pdfDeadline) {
        this.ownerId = ownerId;
        this.companyName = companyName;
        this.document = document;
        this.representativeName = representativeName;
        this.category = category;
        this.pdfDeadline = pdfDeadline;
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getCompanyName() { return companyName; }
    public String getDocument() { return document; }
    public String getRepresentativeName() { return representativeName; }
    public String getCategory() { return category; }
    public OffsetDateTime getPdfDeadline() { return pdfDeadline; }
    public ProjectStatus getStatus() { return status; }
    public BigDecimal getBoothAreaM2() { return boothAreaM2; }
    public String getPdfObjectKey() { return pdfObjectKey; }
    public String getPdfOriginalFilename() { return pdfOriginalFilename; }
    public String getPdfContentType() { return pdfContentType; }
    public Long getPdfSizeBytes() { return pdfSizeBytes; }
    public OffsetDateTime getPdfUploadedAt() { return pdfUploadedAt; }
    public String getDecisionBy() { return decisionBy; }
    public OffsetDateTime getDecisionAt() { return decisionAt; }
    public String getRejectionJustification() { return rejectionJustification; }

    public void submitPdf(BigDecimal areaM2, String objectKey, String filename,
                          String contentType, long size, OffsetDateTime submittedAt) {
        this.boothAreaM2 = areaM2;
        this.pdfObjectKey = objectKey;
        this.pdfOriginalFilename = filename;
        this.pdfContentType = contentType;
        this.pdfSizeBytes = size;
        this.pdfUploadedAt = submittedAt;
        this.status = ProjectStatus.PDF_EM_ANALISE;
        this.decisionBy = null;
        this.decisionAt = null;
        this.rejectionJustification = null;
    }

    public void approve(String adminId, OffsetDateTime decidedAt) {
        this.status = ProjectStatus.APROVADO;
        this.decisionBy = adminId;
        this.decisionAt = decidedAt;
        this.rejectionJustification = null;
    }

    public void reject(String adminId, OffsetDateTime decidedAt, String justification) {
        this.status = ProjectStatus.REPROVADO;
        this.decisionBy = adminId;
        this.decisionAt = decidedAt;
        this.rejectionJustification = justification;
    }
}
