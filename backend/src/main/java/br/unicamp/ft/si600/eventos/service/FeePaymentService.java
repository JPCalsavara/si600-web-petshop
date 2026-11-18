package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.FeePaymentResponse;
import br.unicamp.ft.si600.eventos.dto.payment.GenerateBoletoCommand;
import br.unicamp.ft.si600.eventos.dto.payment.PaymentResponse;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.Payment;
import br.unicamp.ft.si600.eventos.entity.PaymentStatus;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectAddress;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.exception.PaymentException;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoException;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.Address;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.Payer;
import br.unicamp.ft.si600.eventos.repository.PaymentRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * US-11: gera o boleto de uma taxa individual do projeto no Mercado Pago.
 *
 * <p>Este serviço não é {@code @Transactional}: o {@link PaymentService} exige que a chamada HTTP ao provedor
 * ocorra fora de transação. A taxa só muda para PAGAMENTO_GERADO numa transação curta posterior, depois que o
 * provedor confirma o boleto. Em falha ou timeout a taxa permanece no status anterior.
 *
 * <p>Cada taxa tem uma obrigação por tentativa ({@code fee:<id>:<n>}). O TEC-02 mantém a obrigação reservada
 * mesmo após falha definitiva, então uma nova tentativa só é aberta (n+1) quando a anterior terminou em
 * FALHOU. Resultado incerto (timeout) nunca abre outra tentativa: o boleto pode ter sido emitido.
 */
@Service
public class FeePaymentService {
    private static final Logger log = LoggerFactory.getLogger(FeePaymentService.class);
    /** Identificação desta aplicação como chamadora confiável do PaymentService. */
    static final String INTEGRATION_ID = "eventos-web";
    /** Limites de vencimento aceitos pelo Mercado Pago para boleto. */
    private static final int MIN_EXPIRATION_DAYS = 3;
    private static final int MAX_EXPIRATION_DAYS = 30;

    private final ProjectRepository projects;
    private final ProjectFeeRepository projectFees;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final ProjectHistoryService historyService;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final EntityManager entityManager;

    public FeePaymentService(ProjectRepository projects, ProjectFeeRepository projectFees,
                             PaymentRepository payments, PaymentService paymentService,
                             ProjectHistoryService historyService, Clock clock,
                             PlatformTransactionManager transactionManager, EntityManager entityManager) {
        this.projects = projects;
        this.projectFees = projectFees;
        this.payments = payments;
        this.paymentService = paymentService;
        this.historyService = historyService;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    /** Dados da cobrança lidos do banco antes da chamada externa. */
    private record Charge(String feeName, BigDecimal amount, String description, int expirationDays, Payer payer,
                          boolean alreadyGenerated) {}

    /** Tentativa corrente da taxa: a primeira obrigação sem registro ou com registro que não falhou. */
    private record Attempt(String reference, PaymentResponse existing) {}

    public FeePaymentResponse generate(UUID projectId, UUID projectFeeId, Actor actor) {
        requireClient(actor);
        Charge charge = readCharge(projectId, projectFeeId, actor);
        if (charge.alreadyGenerated()) {
            throw new ApiException(HttpStatus.CONFLICT, "O pagamento desta taxa já foi gerado.");
        }

        Attempt attempt = currentAttempt(projectFeeId);
        PaymentResponse payment = attempt.existing() != null
                ? attempt.existing()
                : requestBoleto(projectFeeId, attempt.reference(), charge);

        switch (payment.status()) {
            case CRIANDO -> throw new ApiException(HttpStatus.CONFLICT,
                    "A geração do boleto desta taxa está em andamento. Aguarde alguns instantes e atualize a página.");
            case INCERTO -> throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível confirmar a geração do boleto. Não tente novamente: procure a organização do evento.");
            case FALHOU -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível gerar o boleto agora. A taxa não foi alterada; tente novamente em instantes.");
            default -> { /* boleto existente no provedor */ }
        }
        // Um boleto GERADO de tentativa anterior (taxa sem a marcação) só vale se o valor ainda for o mesmo.
        if (payment.amount().compareTo(charge.amount()) != 0) {
            log.warn("US-11 valor divergente: projectFee={} pagamento={} valorAtual={}", projectFeeId,
                    payment.amount(), charge.amount());
            throw new ApiException(HttpStatus.CONFLICT,
                    "O valor da taxa mudou depois da emissão do boleto. Procure a organização do evento.");
        }
        markGenerated(projectId, projectFeeId, charge, actor);
        return FeePaymentResponse.of(projectFeeId, payment);
    }

    /** Boleto/código de pagamento da taxa. O provedor é consultado a cada chamada; nada é gravado. */
    public FeePaymentResponse getBoleto(UUID projectId, UUID projectFeeId, Actor actor) {
        requireClient(actor);
        Boolean generated = tx.execute(status -> {
            loadOwnedProject(projectId, actor);
            ProjectFee fee = projectFees.findForUpdate(projectFeeId, projectId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxa do projeto não encontrada."));
            return fee.isPaymentGenerated();
        });
        Attempt attempt = currentAttempt(projectFeeId);
        if (!Boolean.TRUE.equals(generated) || attempt.existing() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Esta taxa ainda não possui pagamento gerado.");
        }
        try {
            return FeePaymentResponse.of(projectFeeId, paymentService.getBoleto(INTEGRATION_ID, attempt.existing().id()));
        } catch (MercadoPagoException | PaymentException | DataAccessException ex) {
            log.warn("US-11 consulta do boleto falhou: projectFee={} causa={}", projectFeeId, ex.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível obter o boleto agora. Tente novamente em instantes.");
        }
    }

    private PaymentResponse requestBoleto(UUID projectFeeId, String reference, Charge charge) {
        // requestId determinístico por tentativa: um clique repetido reaproveita a mesma reserva em vez de duplicar.
        UUID requestId = UUID.nameUUIDFromBytes(reference.getBytes(StandardCharsets.UTF_8));
        var command = new GenerateBoletoCommand(INTEGRATION_ID, reference, requestId, charge.amount(), "BRL",
                charge.description(), charge.expirationDays(), charge.payer());
        try {
            return paymentService.generateBoleto(command);
        } catch (PaymentException ex) {
            log.warn("US-11 pagamento não concluído: projectFee={} tipo={}", projectFeeId, ex.kind());
            if (ex.kind() == PaymentException.Kind.CONFLICT) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Já existe uma geração de pagamento em andamento para esta taxa.");
            }
            throw unavailable();
        } catch (DataAccessException ex) {
            log.warn("US-11 falha de persistência ao gerar pagamento: projectFee={}", projectFeeId);
            throw unavailable();
        }
    }

    /** Valida o pedido e monta a cobrança. Só lê: a taxa não é alterada antes de o provedor responder. */
    private Charge readCharge(UUID projectId, UUID projectFeeId, Actor actor) {
        return tx.execute(status -> {
            Project project = loadOwnedProject(projectId, actor);
            requireFeesReleased(project);
            ProjectFee projectFee = projectFees.findForUpdate(projectFeeId, projectId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxa do projeto não encontrada."));
            if (projectFee.isPaymentGenerated()) {
                return new Charge(null, null, null, 0, null, true);
            }
            ProjectFeeStatus feeStatus = projectFee.effectiveStatus();
            if (feeStatus != ProjectFeeStatus.DISPONIVEL && feeStatus != ProjectFeeStatus.COTADA) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Esta taxa ainda não está disponível para pagamento.");
            }
            BigDecimal amount = amountOf(projectFee, project);
            if (amount == null || amount.signum() <= 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Esta taxa ainda não tem valor definido.");
            }
            String feeName = projectFee.getFee().getName();
            return new Charge(feeName, amount, description(feeName, project), expirationDays(project),
                    payerOf(project), false);
        });
    }

    /** Marca a taxa como PAGAMENTO_GERADO e registra o histórico, de forma idempotente. */
    private void markGenerated(UUID projectId, UUID projectFeeId, Charge charge, Actor actor) {
        try {
            tx.executeWithoutResult(status -> {
                ProjectFee projectFee = projectFees.findForUpdate(projectFeeId, projectId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Taxa do projeto não encontrada."));
                // Com open-in-view a taxa carregada em readCharge continua no contexto de persistência e o
                // Hibernate a devolve sem reler a linha. Sem o refresh, duas requisições simultâneas
                // enxergariam o status antigo mesmo depois de esperar o lock.
                entityManager.refresh(projectFee);
                if (projectFee.isPaymentGenerated()) return;
                ProjectFeeStatus feeStatus = projectFee.effectiveStatus();
                BigDecimal current = amountOf(projectFee, projectFee.getProject());
                // Corrida rara: a quantidade foi alterada (US-06) enquanto o boleto era emitido.
                if ((feeStatus != ProjectFeeStatus.DISPONIVEL && feeStatus != ProjectFeeStatus.COTADA)
                        || current == null || current.compareTo(charge.amount()) != 0) {
                    log.warn("US-11 taxa mudou durante a emissão: projectFee={}", projectFeeId);
                    throw new ApiException(HttpStatus.CONFLICT,
                            "A taxa foi alterada durante a geração do boleto. Procure a organização do evento.");
                }
                projectFee.markPaymentGenerated();
                projectFees.saveAndFlush(projectFee);
                historyService.recordPaymentGenerated(projectId, charge.feeName(), charge.amount(), actor);
            });
        } catch (DataAccessException ex) {
            // O boleto já existe no provedor. O próximo clique encontra o pagamento GERADO e conclui a marcação.
            log.warn("US-11 boleto emitido, mas a taxa não foi marcada: projectFee={}", projectFeeId);
            throw unavailable();
        }
    }

    private Attempt currentAttempt(UUID projectFeeId) {
        for (int n = 1; ; n++) {
            String reference = "fee:" + projectFeeId + ":" + n;
            Optional<Payment> found = payments.findByIntegrationIdAndObligationReference(INTEGRATION_ID, reference);
            if (found.isEmpty()) return new Attempt(reference, null);
            if (found.get().getStatus() != PaymentStatus.FALHOU) {
                return new Attempt(reference, PaymentResponse.from(found.get()));
            }
        }
    }

    /** Fixa e por metragem: valor do catálogo. Variável: valor cotado pelo Admin (US-07). */
    private static BigDecimal amountOf(ProjectFee projectFee, Project project) {
        if (projectFee.getFee().getType() == FeeType.VARIAVEL) {
            return projectFee.effectiveStatus() == ProjectFeeStatus.COTADA ? projectFee.getQuotedAmount() : null;
        }
        return FeeCalculator.calculate(projectFee.getFee(), project.getBoothAreaM2()).value();
    }

    /** Vencimento do boleto: até a data limite de pagamento do projeto, dentro dos limites do provedor. */
    private int expirationDays(Project project) {
        if (project.getPaymentDeadline() == null) return MAX_EXPIRATION_DAYS;
        LocalDate today = LocalDate.now(clock.withZone(ProjectService.EVENT_ZONE));
        LocalDate deadline = project.getPaymentDeadline().atZoneSameInstant(ProjectService.EVENT_ZONE).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, deadline);
        return (int) Math.max(MIN_EXPIRATION_DAYS, Math.min(MAX_EXPIRATION_DAYS, days));
    }

    /** Pagador a partir do cadastro do projeto. O endereço estruturado vem do BUG-07. */
    private static Payer payerOf(Project project) {
        ProjectAddress address = project.getStructuredAddress();
        if (address == null || address.getZipCode() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O endereço do projeto está incompleto. Peça à organização para atualizar o cadastro.");
        }
        String digits = project.getDocumentDigits();
        String documentType = digits != null && digits.length() == 11 ? "CPF"
                : digits != null && digits.length() == 14 ? "CNPJ" : null;
        if (documentType == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O CPF/CNPJ do projeto é inválido. Peça à organização para atualizar o cadastro.");
        }
        String[] names = project.getRepresentativeName().trim().split("\\s+", 2);
        String firstName = names[0];
        String lastName = names.length > 1 ? names[1] : names[0];
        return new Payer(project.getContactEmail(), firstName, lastName, documentType, digits,
                new Address(address.getZipCode(), address.getStreet(), address.getNumber(),
                        address.getNeighborhood(), address.getCity(), address.getState()));
    }

    private static String description(String feeName, Project project) {
        String text = "Taxa " + feeName + " - " + project.getCompanyName();
        return text.length() <= 255 ? text : text.substring(0, 255);
    }

    private Project loadOwnedProject(UUID projectId, Actor actor) {
        return projects.findByIdAndOwnerId(projectId, actor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
    }

    private static void requireFeesReleased(Project project) {
        ProjectStatus status = project.getStatus();
        if (status != ProjectStatus.APROVADO && status != ProjectStatus.AGUARDANDO_PAGAMENTO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "As taxas só ficam disponíveis depois que o PDF do estande for aprovado.");
        }
    }

    private static void requireClient(Actor actor) {
        if (actor == null || actor.role() != ActorRole.CLIENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Você não possui permissão para esta operação.");
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "O serviço de pagamentos está indisponível no momento. A taxa não foi alterada; tente novamente mais tarde.");
    }
}
