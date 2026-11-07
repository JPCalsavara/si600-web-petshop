package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.entity.*;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectHistoryEventRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectCsvExportService {

    private static final String UTF8_BOM = "\uFEFF";
    private static final String SEPARATOR = ";";
    private static final String LINE_BREAK = "\r\n";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ProjectRepository projectRepository;
    private final ProjectFeeRepository projectFeeRepository;
    private final ProjectHistoryEventRepository historyEventRepository;

    public record CsvExportResult(String filename, byte[] content) {}

    public ProjectCsvExportService(ProjectRepository projectRepository,
                                   ProjectFeeRepository projectFeeRepository,
                                   ProjectHistoryEventRepository historyEventRepository) {
        this.projectRepository = projectRepository;
        this.projectFeeRepository = projectFeeRepository;
        this.historyEventRepository = historyEventRepository;
    }

    @Transactional(readOnly = true)
    public CsvExportResult exportProjectCsv(UUID projectId, Actor actor) {
        if (actor == null || actor.role() != ActorRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Você não possui permissão para exportar os dados deste projeto.");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));

        List<ProjectFee> fees = projectFeeRepository.findAllByProjectIdOrderByFeeNameAsc(projectId);
        List<ProjectHistoryEvent> historyEvents =
                historyEventRepository.findAllByProjectIdOrderByCreatedAtAscSequenceOrderAsc(projectId);

        StringBuilder csv = new StringBuilder();
        csv.append(UTF8_BOM);

        appendProjectSection(csv, project);
        csv.append(LINE_BREAK);
        appendFeesSection(csv, project, fees);
        csv.append(LINE_BREAK);
        appendHistorySection(csv, historyEvents);

        String filename = "projeto-" + project.getId() + ".csv";
        return new CsvExportResult(filename, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void appendProjectSection(StringBuilder csv, Project project) {
        csv.append("DADOS DO PROJETO").append(LINE_BREAK);
        csv.append(String.join(SEPARATOR,
                "ID do Projeto",
                "Empresa",
                "CPF/CNPJ",
                "Representante",
                "E-mail de Contato",
                "Endereço",
                "Categoria do Estande",
                "Status do Projeto",
                "Área do Estande (m²)",
                "Prazo de Envio do PDF",
                "Prazo de Pagamento",
                "Arquivo PDF",
                "Decisão por",
                "Data da Decisão",
                "Justificativa de Reprovação"
        )).append(LINE_BREAK);

        appendRow(csv,
                project.getId() != null ? project.getId().toString() : "-",
                project.getCompanyName(),
                project.getDocument(),
                project.getRepresentativeName(),
                project.getContactEmail(),
                project.getAddress(),
                project.getCategory(),
                formatProjectStatus(project.getStatus()),
                formatDecimal(project.getBoothAreaM2()),
                formatDateTime(project.getPdfDeadline()),
                formatDateTime(project.getPaymentDeadline()),
                project.getPdfOriginalFilename(),
                project.getDecisionBy(),
                formatDateTime(project.getDecisionAt()),
                project.getRejectionJustification()
        );
    }

    private void appendFeesSection(StringBuilder csv, Project project, List<ProjectFee> fees) {
        csv.append("TAXAS DO PROJETO").append(LINE_BREAK);
        csv.append(String.join(SEPARATOR,
                "ID do Vínculo",
                "Nome da Taxa",
                "Descrição",
                "Tipo de Taxa",
                "Status da Taxa",
                "Quantidade",
                "Unidade",
                "Valor Calculado / Cotado (R$)",
                "Cotado por",
                "Data da Cotação"
        )).append(LINE_BREAK);

        for (ProjectFee pf : fees) {
            Fee fee = pf.getFee();
            ProjectFeeStatus status = pf.effectiveStatus();
            BigDecimal value;
            BigDecimal quantity;
            String unit;

            if (fee.getType() == FeeType.VARIAVEL) {
                boolean quoted = status == ProjectFeeStatus.COTADA || status == ProjectFeeStatus.PAGAMENTO_GERADO;
                value = quoted ? pf.getQuotedAmount() : null;
                quantity = pf.getQuantity();
                unit = fee.getMeasurementUnit();
            } else {
                FeeCalculator.Result result = FeeCalculator.calculate(fee, project.getBoothAreaM2());
                value = result.value();
                quantity = result.quantity();
                unit = result.unit();
            }

            appendRow(csv,
                    pf.getId() != null ? pf.getId().toString() : "-",
                    fee.getName(),
                    fee.getDescription(),
                    formatFeeType(fee.getType()),
                    formatFeeStatus(status),
                    formatDecimal(quantity),
                    unit,
                    formatDecimal(value),
                    pf.getQuotedBy(),
                    formatDateTime(pf.getQuotedAt())
            );
        }
    }

    private void appendHistorySection(StringBuilder csv, List<ProjectHistoryEvent> historyEvents) {
        csv.append("HISTÓRICO DE EVENTOS").append(LINE_BREAK);
        csv.append(String.join(SEPARATOR,
                "Data/Hora",
                "Tipo de Evento",
                "Autor",
                "Perfil do Autor",
                "Descrição"
        )).append(LINE_BREAK);

        for (ProjectHistoryEvent event : historyEvents) {
            appendRow(csv,
                    formatDateTime(event.getCreatedAt()),
                    formatHistoryEventType(event.getEventType()),
                    event.getActorId(),
                    formatActorRole(event.getActorRole()),
                    event.getDescription()
            );
        }
    }

    private void appendRow(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(escapeCsvValue(values[i]));
        }
        csv.append(LINE_BREAK);
    }

    static String escapeCsvValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "-";
        }
        String sanitized = rawValue.trim();
        if (!"-".equals(sanitized) && isPotentialFormula(sanitized)) {
            sanitized = "'" + sanitized;
        }
        boolean needsQuotes = sanitized.contains(SEPARATOR)
                || sanitized.contains("\"")
                || sanitized.contains("\n")
                || sanitized.contains("\r");
        if (!needsQuotes) {
            return sanitized;
        }
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    private static boolean isPotentialFormula(String value) {
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '@';
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    private static String formatProjectStatus(ProjectStatus status) {
        if (status == null) return "-";
        return switch (status) {
            case AGUARDANDO_PDF -> "Aguardando PDF";
            case PDF_EM_ANALISE -> "PDF em análise";
            case APROVADO -> "Aprovado";
            case REPROVADO -> "Reprovado";
            case AGUARDANDO_PAGAMENTO -> "Aguardando pagamento";
            case CONCLUIDO -> "Concluído";
        };
    }

    private static String formatFeeType(FeeType type) {
        if (type == null) return "-";
        return switch (type) {
            case FIXA -> "Fixa";
            case POR_METRAGEM -> "Por metragem";
            case VARIAVEL -> "Variável";
        };
    }

    private static String formatFeeStatus(ProjectFeeStatus status) {
        if (status == null) return "-";
        return switch (status) {
            case DISPONIVEL -> "Disponível";
            case AGUARDANDO_QUANTIDADE -> "Aguardando quantidade";
            case AGUARDANDO_COTACAO -> "Aguardando cotação";
            case COTADA -> "Cotada";
            case PAGAMENTO_GERADO -> "Pagamento gerado";
        };
    }

    private static String formatHistoryEventType(ProjectHistoryEventType eventType) {
        if (eventType == null) return "-";
        return switch (eventType) {
            case PROJETO_CRIADO -> "Projeto criado";
            case PDF_ENVIADO -> "PDF enviado";
            case PDF_APROVADO -> "PDF aprovado";
            case PDF_REPROVADO -> "PDF reprovado";
            case TAXAS_CONFIGURADAS -> "Taxas configuradas";
            case QUANTIDADE_INFORMADA -> "Quantidade informada";
            case TAXA_COTADA -> "Taxa cotada";
            case PAGAMENTO_GERADO -> "Pagamento gerado";
            case PAGAMENTO_CONFIRMADO -> "Pagamento confirmado";
            case PAGAMENTO_CANCELADO -> "Pagamento cancelado";
        };
    }

    private static String formatActorRole(ActorRole role) {
        if (role == null) return "-";
        return switch (role) {
            case ADMIN -> "Administrador";
            case CLIENT -> "Cliente";
        };
    }
}
