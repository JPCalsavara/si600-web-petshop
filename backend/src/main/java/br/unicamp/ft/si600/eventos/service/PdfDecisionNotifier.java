package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.notification.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * US-08: avisa o cliente por e-mail da decisão sobre o PDF. O envio acontece só depois do commit
 * (nunca avisa de uma decisão revertida) e uma falha de e-mail jamais desfaz a decisão.
 */
@Component
public class PdfDecisionNotifier {
    private static final Logger log = LoggerFactory.getLogger(PdfDecisionNotifier.class);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo"));

    private final EmailSender emailSender;

    public PdfDecisionNotifier(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void notifyApproved(Project project) {
        String subject = "PDF do estande aprovado - " + project.getCompanyName();
        String body = """
                Olá, %s.

                O PDF do estande da empresa %s foi aprovado pela organização.
                Metragem oficial registrada: %s m².

                O próximo passo é a cotação das taxas do projeto pela organização.

                Esta é uma mensagem automática do Sistema de Eventos.
                """.formatted(project.getRepresentativeName(), project.getCompanyName(),
                project.getBoothAreaM2() == null ? "-" : project.getBoothAreaM2().toPlainString());
        dispatch(project, subject, body);
    }

    public void notifyRejected(Project project) {
        String subject = "PDF do estande reprovado - " + project.getCompanyName();
        String body = """
                Olá, %s.

                O PDF do estande da empresa %s foi reprovado pela organização.

                Justificativa:
                %s

                Ajuste o projeto conforme a justificativa e envie o PDF novamente pelo sistema.
                Prazo limite para envio do PDF: %s.

                Esta é uma mensagem automática do Sistema de Eventos.
                """.formatted(project.getRepresentativeName(), project.getCompanyName(),
                project.getRejectionJustification(), DATE.format(project.getPdfDeadline()));
        dispatch(project, subject, body);
    }

    private void dispatch(Project project, String subject, String body) {
        String to = project.getContactEmail();
        if (to == null || to.isBlank()) {
            log.warn("Projeto {} sem e-mail de contato; notificação não enviada.", project.getId());
            return;
        }
        Runnable task = () -> {
            try {
                emailSender.send(to, subject, body);
            } catch (RuntimeException ex) {
                log.error("Falha ao notificar o cliente do projeto {}: {}", project.getId(), ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
