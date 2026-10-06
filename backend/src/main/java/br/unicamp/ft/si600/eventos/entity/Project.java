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

    // Campos da US-03. Nullable no banco para não quebrar linhas anteriores (ddl-auto=update);
    // a obrigatoriedade é garantida na criação (ProjectService).
    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "contact_email", length = 254, unique = true)
    private String contactEmail;

    @Column(name = "document_digits", length = 14, unique = true)
    private String documentDigits;

    @Column(name = "payment_deadline")
    private OffsetDateTime paymentDeadline;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

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

    public static Project create(String ownerId, String companyName, String document, String documentDigits,
                                 String representativeName, String category, String address,
                                 String contactEmail, OffsetDateTime pdfDeadline,
                                 OffsetDateTime paymentDeadline, String createdBy, OffsetDateTime createdAt) {
        Project p = new Project(ownerId, companyName, document, representativeName, category, pdfDeadline);
        p.documentDigits = documentDigits;
        p.address = address;
        p.contactEmail = contactEmail;
        p.paymentDeadline = paymentDeadline;
        p.createdBy = createdBy;
        p.createdAt = createdAt;
        return p;
    }

    public void updateDeadlines(OffsetDateTime pdfDeadline, OffsetDateTime paymentDeadline) {
        this.pdfDeadline = pdfDeadline;
        this.paymentDeadline = paymentDeadline;
    }

    public UUID getId() { return id; }
    public String getAddress() { return address; }
    public String getContactEmail() { return contactEmail; }
    public String getDocumentDigits() { return documentDigits; }
    public OffsetDateTime getPaymentDeadline() { return paymentDeadline; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
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

    public void submitPdf(String objectKey, String filename,
                          String contentType, long size, OffsetDateTime submittedAt) {
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

    public void approve(String adminId, OffsetDateTime decidedAt, BigDecimal approvedAreaM2) {
        this.status = ProjectStatus.APROVADO;
        this.decisionBy = adminId;
        this.decisionAt = decidedAt;
        this.boothAreaM2 = approvedAreaM2;
        this.rejectionJustification = null;
    }

    public void reject(String adminId, OffsetDateTime decidedAt, String justification) {
        this.status = ProjectStatus.REPROVADO;
        this.decisionBy = adminId;
        this.decisionAt = decidedAt;
        this.rejectionJustification = justification;
    }
}
