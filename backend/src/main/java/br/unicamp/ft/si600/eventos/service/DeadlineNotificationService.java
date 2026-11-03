package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.entity.DeadlineNotification;
import br.unicamp.ft.si600.eventos.entity.DeadlineType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.notification.AsyncEmailSender;
import br.unicamp.ft.si600.eventos.notification.EmailSender;
import br.unicamp.ft.si600.eventos.notification.GmailProperties;
import br.unicamp.ft.si600.eventos.repository.DeadlineNotificationRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * US-10: avisa por e-mail quando um prazo se aproxima ou vence.
 *
 * <ul>
 *   <li>Cliente: prazo de envio do PDF (projeto aguardando PDF ou reprovado) e prazo de pagamento
 *       (projeto aprovado com taxas ainda não quitadas).</li>
 *   <li>Admin: prazo de envio do PDF de um projeto cujo PDF está aguardando análise.</li>
 * </ul>
 *
 * Um e-mail por marco: o envio só é registrado em {@code deadline_notifications} depois de o provedor
 * aceitá-lo, e a rotina ignora marcos já registrados. A pendência é reavaliada a cada execução, então
 * PDF enviado ou taxa quitada encerram os avisos sem nenhum passo extra.
 */
@Service
public class DeadlineNotificationService {
    private static final Logger log = LoggerFactory.getLogger(DeadlineNotificationService.class);

    /** Projetos concluídos não têm mais pendência de prazo. */
    private static final Set<ProjectStatus> ACTIVE = EnumSet.complementOf(EnumSet.of(ProjectStatus.CONCLUIDO));

    /**
     * Taxas sem pendência de cobrança. Com o pagamento já gerado (US-11) o Cliente não tem mais ação
     * pendente, então o aviso de prazo para de cobrá-lo. A US-12 criará o status "Pago"; ao criá-lo,
     * basta incluí-lo aqui.
     */
    private static final Set<ProjectFeeStatus> SETTLED = EnumSet.of(ProjectFeeStatus.PAGAMENTO_GERADO);

    private final ProjectRepository projectRepository;
    private final ProjectFeeRepository projectFeeRepository;
    private final DeadlineNotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final GmailProperties gmailProperties;
    private final DeadlineEvaluator evaluator;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DeadlineNotificationService(ProjectRepository projectRepository,
                                       ProjectFeeRepository projectFeeRepository,
                                       DeadlineNotificationRepository notificationRepository,
                                       EmailSender emailSender,
                                       GmailProperties gmailProperties,
                                       DeadlineEvaluator evaluator,
                                       Clock clock,
                                       PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.projectFeeRepository = projectFeeRepository;
        this.notificationRepository = notificationRepository;
        // O envio assíncrono engole falhas; aqui precisamos delas para não registrar um e-mail que não saiu.
        this.emailSender = emailSender instanceof AsyncEmailSender async ? async.delegate() : emailSender;
        this.gmailProperties = gmailProperties;
        this.evaluator = evaluator;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Executa a verificação de todos os projetos em andamento. Retorna quantos e-mails foram enviados. */
    public int run() {
        int sent = 0;
        for (Project project : projectRepository.findAllByStatusIn(ACTIVE)) {
            UUID projectId = project.getId();
            try {
                Integer count = transaction.execute(status -> processProject(projectId));
                sent += count == null ? 0 : count;
            } catch (RuntimeException ex) {
                // Um projeto com problema não pode impedir os avisos dos demais.
                log.error("Falha ao processar prazos do projeto {}: {}", projectId, ex.getMessage());
            }
        }
        log.info("Rotina de prazos concluída: {} e-mail(s) enviado(s).", sent);
        return sent;
    }

    private int processProject(UUID projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return 0;

        return switch (project.getStatus()) {
            case AGUARDANDO_PDF, REPROVADO -> notifyIfDue(project, DeadlineType.PDF_CLIENTE,
                    project.getPdfDeadline(), project.getContactEmail(), List.of());
            case PDF_EM_ANALISE -> notifyIfDue(project, DeadlineType.APROVACAO_ADMIN,
                    project.getPdfDeadline(), gmailProperties.resolveAdminRecipient(), List.of());
            case APROVADO, AGUARDANDO_PAGAMENTO -> notifyPayment(project);
            case CONCLUIDO -> 0;
        };
    }

    private int notifyPayment(Project project) {
        if (project.getPaymentDeadline() == null) return 0;
        List<String> pending = projectFeeRepository.findAllByProjectIdOrderByFeeNameAsc(project.getId()).stream()
                .filter(fee -> !SETTLED.contains(fee.getStatus()))
                .map(ProjectFee::getFee)
                .map(br.unicamp.ft.si600.eventos.entity.Fee::getName)
                .toList();
        if (pending.isEmpty()) return 0;
        return notifyIfDue(project, DeadlineType.PAGAMENTO_CLIENTE,
                project.getPaymentDeadline(), project.getContactEmail(), pending);
    }

    private int notifyIfDue(Project project, DeadlineType type, OffsetDateTime deadline,
                            String recipient, List<String> pendingFeeNames) {
        Optional<DeadlineEvaluator.Evaluation> evaluation = evaluator.evaluate(deadline);
        if (evaluation.isEmpty()) return 0;

        if (recipient == null || recipient.isBlank()) {
            log.warn("Projeto {} sem destinatário para o aviso {}; e-mail não enviado.", project.getId(), type);
            return 0;
        }
        String to = recipient.trim();
        DeadlineEvaluator.Evaluation due = evaluation.get();

        if (notificationRepository.existsByProjectIdAndDeadlineTypeAndMilestoneAndDeadlineDateAndRecipient(
                project.getId(), type, due.milestone(), due.deadlineDate(), to)) {
            return 0;
        }

        DeadlineEmailTemplates.Message message =
                DeadlineEmailTemplates.build(type, project, due, pendingFeeNames);
        try {
            emailSender.send(to, message.subject(), message.body());
        } catch (RuntimeException ex) {
            // Sem registro: a rotina de amanhã tenta de novo este marco.
            log.error("Falha ao enviar aviso {} do projeto {}: {}", type, project.getId(), ex.getMessage());
            return 0;
        }

        notificationRepository.save(new DeadlineNotification(project.getId(), type, due.milestone(),
                due.deadlineDate(), to, OffsetDateTime.now(clock)));
        return 1;
    }
}
