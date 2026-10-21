package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.AlertTag;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.util.TimeHelper;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AlertTagCalculator {

    private static final long WARNING_DAYS = 3;
    private final TimeHelper timeHelper;

    public AlertTagCalculator(TimeHelper timeHelper) {
        this.timeHelper = Objects.requireNonNull(timeHelper, "TimeHelper não pode ser nulo.");
    }

    public AlertTag calculate(Project project) {
        if (project == null) {
            return AlertTag.EM_DIA;
        }

        // Projeto concluído tem todas as obrigações encerradas
        if (project.getStatus() == ProjectStatus.CONCLUIDO) {
            return AlertTag.EM_DIA;
        }

        // 1. Verificação de Atraso (Maior Criticidade)
        boolean pdfOverdue = isPdfOverdue(project);
        boolean paymentOverdue = isPaymentOverdue(project);

        if (pdfOverdue || paymentOverdue) {
            return AlertTag.ATRASADO;
        }

        // 2. Verificação de Prazo Próximo (<= 3 dias)
        boolean pdfDueSoon = isPdfDueSoon(project);
        boolean paymentDueSoon = isPaymentDueSoon(project);

        if (pdfDueSoon || paymentDueSoon) {
            return AlertTag.PRAZO_PROXIMO;
        }

        // 3. Verificação de Pendência Operacional
        if (project.getStatus() == ProjectStatus.PDF_EM_ANALISE) {
            return AlertTag.PENDENTE;
        }

        // 4. Regular
        return AlertTag.EM_DIA;
    }

    private boolean isPdfOverdue(Project project) {
        return (project.getStatus() == ProjectStatus.AGUARDANDO_PDF || project.getStatus() == ProjectStatus.REPROVADO)
                && timeHelper.isOverdue(project.getPdfDeadline());
    }

    private boolean isPdfDueSoon(Project project) {
        return (project.getStatus() == ProjectStatus.AGUARDANDO_PDF || project.getStatus() == ProjectStatus.REPROVADO)
                && timeHelper.isDueSoon(project.getPdfDeadline(), WARNING_DAYS);
    }

    private boolean isPaymentOverdue(Project project) {
        return project.getStatus() != ProjectStatus.CONCLUIDO
                && timeHelper.isOverdue(project.getPaymentDeadline());
    }

    private boolean isPaymentDueSoon(Project project) {
        return project.getStatus() != ProjectStatus.CONCLUIDO
                && timeHelper.isDueSoon(project.getPaymentDeadline(), WARNING_DAYS);
    }
}
