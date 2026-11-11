package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.payment.BoletoDetailsResponse;
import br.unicamp.ft.si600.eventos.dto.payment.GenerateBoletoCommand;
import br.unicamp.ft.si600.eventos.dto.payment.PaymentResponse;
import br.unicamp.ft.si600.eventos.entity.Payment;
import br.unicamp.ft.si600.eventos.entity.PaymentStatus;
import br.unicamp.ft.si600.eventos.exception.PaymentException;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoException;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.BoletoRequest;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.OrderResult;
import br.unicamp.ft.si600.eventos.repository.PaymentRepository;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static br.unicamp.ft.si600.eventos.exception.PaymentException.Kind;

/** Interface Java backend. O chamador autentica a integração e autoriza os dados da obrigação. */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository repository;
    private final ObjectProvider<MercadoPagoGateway> gateways;
    private final Validator validator;
    private final Clock clock;
    private final String fingerprintSecret;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public PaymentService(PaymentRepository repository, ObjectProvider<MercadoPagoGateway> gateways,
                          Validator validator, Clock clock,
                          PlatformTransactionManager transactions,
                          @Value("${payment.fingerprint-secret:}") String fingerprintSecret) {
        this.repository = repository;
        this.gateways = gateways;
        this.validator = validator;
        this.clock = clock;
        this.fingerprintSecret = fingerprintSecret;
        this.write = new TransactionTemplate(transactions);
        this.write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.write.setTimeout(5);
        this.read = new TransactionTemplate(transactions);
        this.read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.read.setReadOnly(true);
        this.read.setTimeout(5);
    }

    public PaymentResponse generateBoleto(GenerateBoletoCommand command) {
        requireNoAmbientTransaction();
        validate(command);
        String fingerprint = fingerprint(command);
        PaymentResponse existing = read.execute(tx -> existingRequest(command, fingerprint));
        if (existing != null) return existing;
        MercadoPagoGateway gateway = gateway();
        Reservation reservation = reserve(command, fingerprint);
        if (!reservation.created()) return reservation.payment();

        PaymentResponse payment = reservation.payment();
        OrderResult result;
        try {
            result = gateway.createBoleto(new BoletoRequest(payment.id(), reservation.key(), payment.amount(),
                    command.description(), command.expirationDays(), command.payer()));
        } catch (MercadoPagoException ex) {
            boolean uncertain = ex.creationMayHaveSucceeded() || ex.providerStatus() == 409 || ex.providerStatus() == 423;
            return failCreation(payment, uncertain, ex.kind().name(), ex.providerStatus());
        } catch (RuntimeException ex) {
            return failCreation(payment, true, "UNEXPECTED_GATEWAY_FAILURE", 0);
        }
        try {
            return apply(payment.integrationId(), payment.id(), result, false, null);
        } catch (PaymentException ex) {
            if (ex.kind() != Kind.VERIFICATION_FAILED) throw ex;
            return failCreation(payment, true, "VERIFICATION_FAILED", result == null ? 0 : result.providerHttpStatus());
        }
    }

    /** Leitura exclusivamente local, inclusive quando o provedor está indisponível. */
    public PaymentResponse getPayment(String integrationId, UUID paymentId) {
        validateIdentity(integrationId, paymentId);
        return read.execute(tx -> PaymentResponse.from(load(integrationId, paymentId, false)));
    }

    /** Histórico local paginado; o controller autoriza o acesso ao ambiente de desenvolvimento. */
    public org.springframework.data.domain.Page<PaymentResponse> getPaymentHistory(String integrationId, int page) {
        var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id");
        return read.execute(tx -> repository.findByIntegrationId(integrationId,
                org.springframework.data.domain.PageRequest.of(page, 20, sort)).map(PaymentResponse::from));
    }

    public BoletoDetailsResponse getBoleto(String integrationId, UUID paymentId) {
        requireNoAmbientTransaction();
        PaymentResponse payment = getPayment(integrationId, paymentId);
        if (payment.orderId() == null) throw new PaymentException(Kind.RECOVERY_REQUIRED, paymentId);
        return fetchAndApply(integrationId, paymentId, payment.orderId());
    }

    public PaymentResponse synchronizeOrder(String integrationId, UUID paymentId) {
        requireNoAmbientTransaction();
        PaymentResponse payment = getPayment(integrationId, paymentId);
        if (payment.orderId() == null) return payment;
        return fetchAndApply(integrationId, paymentId, payment.orderId()).payment();
    }

    /** Resolve a cobrança pela external_reference verificada do provedor; o corpo não escolhe o pagamento. */
    public PaymentResponse synchronizeNotifiedOrder(String orderId) {
        requireNoAmbientTransaction();
        if (orderId == null || !orderId.matches("ORD[A-Z0-9]{1,100}")) throw new PaymentException(Kind.INVALID_INPUT, null);
        // A resposta autenticada do provedor é a fonte de associação. Isso também recupera
        // reservas incertas em que o POST criou a ordem, mas a resposta não foi persistida.
        OrderResult snapshot = gateway().getOrder(orderId);
        UUID paymentId;
        try {
            paymentId = UUID.fromString(snapshot.externalReference());
            if (!paymentId.toString().equals(snapshot.externalReference())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.warn("MP_WEBHOOK order={} resolution=payment_not_found_local", orderId);
            throw new PaymentException(Kind.NOT_FOUND, null);
        }
        PaymentResponse local = read.execute(tx -> repository.findById(paymentId).map(PaymentResponse::from).orElse(null));
        if (local == null) {
            log.warn("MP_WEBHOOK order={} resolution=payment_not_found_local", orderId);
            throw new PaymentException(Kind.NOT_FOUND, paymentId);
        }
        UUID alreadyAssociated = read.execute(tx -> repository.findByOrderId(orderId).map(Payment::getId).orElse(null));
        if (alreadyAssociated != null && !alreadyAssociated.equals(paymentId)) {
            throw new PaymentException(Kind.CONFLICT, paymentId);
        }
        return apply(local.integrationId(), local.id(), snapshot, true, null);
    }

    /** Recuperação assistida: o ID candidato só é vinculado depois de GET e conferência de referência/valor. */
    public PaymentResponse synchronizeOrder(String integrationId, UUID paymentId, String candidateOrderId) {
        requireNoAmbientTransaction();
        PaymentResponse payment = getPayment(integrationId, paymentId);
        if (candidateOrderId == null || !candidateOrderId.matches("ORD[A-Z0-9]{1,100}")) {
            throw new PaymentException(Kind.INVALID_INPUT, paymentId);
        }
        if (payment.orderId() != null && !payment.orderId().equals(candidateOrderId)) {
            throw new PaymentException(Kind.CONFLICT, paymentId);
        }
        return fetchAndApply(integrationId, paymentId, candidateOrderId).payment();
    }

    private BoletoDetailsResponse fetchAndApply(String integrationId, UUID paymentId, String orderId) {
        // A concurrent commit invalidates the snapshot; only GET may be retried, never creation.
        for (int attempt = 0; attempt < 2; attempt++) {
            Long version = read.execute(tx -> load(integrationId, paymentId, false).getVersion());
            OrderResult snapshot = gateway().getOrder(orderId);
            try {
                var updated = apply(integrationId, paymentId, snapshot, true, version);
                return new BoletoDetailsResponse(updated, snapshot.ticketUrl(), snapshot.digitableLine(), snapshot.barcodeContent());
            } catch (PaymentException ex) {
                if (ex.kind() != Kind.STALE_SNAPSHOT || attempt == 1) throw ex;
            }
        }
        throw new PaymentException(Kind.STALE_SNAPSHOT, paymentId);
    }

    private Reservation reserve(GenerateBoletoCommand command, String fingerprint) {
        try {
            return write.execute(tx -> {
                PaymentResponse existing = existingRequest(command, fingerprint);
                if (existing != null) return new Reservation(existing, null, false);
                // A constraint decide corridas. Um pré-check da obrigação poderia confundir
                // uma reserva da mesma requisição recém-commitada com outra requisição.
                Payment saved = repository.saveAndFlush(new Payment(command.integrationId(), command.obligationReference(),
                        command.requestId(), UUID.randomUUID(), fingerprint,
                        command.amount().setScale(2, RoundingMode.UNNECESSARY), command.currency(), now()));
                return new Reservation(PaymentResponse.from(saved), saved.getIdempotencyKey(), true);
            });
        } catch (DataIntegrityViolationException ex) {
            // O insert perdedor já foi revertido; a leitura ocorre numa nova transação.
            PaymentResponse existing = read.execute(tx -> existingRequest(command, fingerprint));
            if (existing != null) return new Reservation(existing, null, false);
            UUID canonical = read.execute(tx -> repository.findByIntegrationIdAndObligationReference(
                    command.integrationId(), command.obligationReference()).map(Payment::getId).orElse(null));
            throw new PaymentException(canonical == null ? Kind.STORAGE_FAILURE : Kind.CONFLICT, canonical);
        }
    }

    private PaymentResponse existingRequest(GenerateBoletoCommand command, String fingerprint) {
        return repository.findByIntegrationIdAndRequestId(command.integrationId(), command.requestId()).map(payment -> {
            if (!MessageDigest.isEqual(payment.getRequestFingerprint().getBytes(StandardCharsets.US_ASCII),
                    fingerprint.getBytes(StandardCharsets.US_ASCII))) {
                throw new PaymentException(Kind.CONFLICT, payment.getId());
            }
            return PaymentResponse.from(payment);
        }).orElse(null);
    }

    private PaymentResponse apply(String integrationId, UUID paymentId, OrderResult result, boolean verifiedGet, Long expectedVersion) {
        try {
            return write.execute(tx -> {
                Payment payment = load(integrationId, paymentId, true);
                verify(payment, result);
                if (expectedVersion != null && !expectedVersion.equals(payment.getVersion())) {
                    throw new PaymentException(Kind.STALE_SNAPSHOT, paymentId);
                }
                payment.applyProviderSnapshot(result.orderId(), result.paymentId(), result.status(), result.statusDetail(),
                        result.paymentStatus(), result.paymentStatusDetail(), result.paymentExpiresAt(),
                        result.providerHttpStatus(), mapStatus(result, verifiedGet), now());
                repository.flush();
                return PaymentResponse.from(payment);
            });
        } catch (PaymentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // A reserva CRIANDO já foi commitada. Nunca repetir POST para resolver falha local.
            throw new PaymentException(Kind.STORAGE_FAILURE, paymentId);
        }
    }

    private void verify(Payment payment, OrderResult result) {
        if (result == null || result.orderId() == null || !result.orderId().matches("ORD[A-Z0-9]{1,100}")
                || !payment.getId().toString().equals(result.externalReference()) || result.totalAmount() == null
                || payment.getAmount().compareTo(result.totalAmount()) != 0
                || (payment.getOrderId() != null && !payment.getOrderId().equals(result.orderId()))
                || (payment.getPaymentId() != null && result.paymentId() != null
                && !payment.getPaymentId().equals(result.paymentId()))) {
            throw new PaymentException(Kind.VERIFICATION_FAILED, payment.getId());
        }
    }

    private static PaymentStatus mapStatus(OrderResult result, boolean verifiedGet) {
        if (verifiedGet) {
            if ("refunded".equals(result.status()) && "refunded".equals(result.statusDetail())) return PaymentStatus.REEMBOLSADO;
            if ("processed".equals(result.status()) && "partially_refunded".equals(result.statusDetail())) return PaymentStatus.REEMBOLSADO_PARCIAL;
            if ("charged_back".equals(result.status())) return PaymentStatus.CONTESTADO;
        }
        if (verifiedGet && "processed".equals(result.status()) && "accredited".equals(result.statusDetail())
                && "processed".equals(result.paymentStatus()) && "accredited".equals(result.paymentStatusDetail())) {
            return PaymentStatus.PAGO;
        }
        return switch (result.status()) {
            case "created", "processing", "processed" -> PaymentStatus.CRIANDO;
            case "action_required" -> PaymentStatus.GERADO;
            case "canceled" -> "expired".equals(result.statusDetail()) ? PaymentStatus.EXPIRADO : PaymentStatus.CANCELADO;
            case "failed" -> "expired".equals(result.statusDetail()) ? PaymentStatus.EXPIRADO : PaymentStatus.FALHOU;
            default -> PaymentStatus.INCERTO;
        };
    }

    private PaymentResponse failCreation(PaymentResponse original, boolean uncertain, String code, int httpStatus) {
        try {
            return write.execute(tx -> {
                Payment payment = load(original.integrationId(), original.id(), true);
                payment.recordCreationFailure(uncertain, code, httpStatus, now());
                repository.flush();
                return PaymentResponse.from(payment);
            });
        } catch (RuntimeException ex) {
            throw new PaymentException(Kind.STORAGE_FAILURE, original.id());
        }
    }

    private Payment load(String integrationId, UUID paymentId, boolean locked) {
        return (locked ? repository.findForUpdate(paymentId, integrationId)
                : repository.findByIdAndIntegrationId(paymentId, integrationId))
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, paymentId));
    }

    private void validate(GenerateBoletoCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new PaymentException(Kind.INVALID_INPUT, null);
        int digits = "CPF".equals(command.payer().identificationType()) ? 11 : 14;
        if (command.payer().identificationNumber().length() != digits) throw new PaymentException(Kind.INVALID_INPUT, null);
    }

    private String fingerprint(GenerateBoletoCommand command) {
        byte[] secret = fingerprintSecret.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32 || fingerprintSecret.isBlank() || fingerprintSecret.startsWith("COLOCAR_")) {
            throw new PaymentException(Kind.CONFIGURATION, null);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            var payer = command.payer();
            var address = payer.address();
            // Codificação v1 explícita e prefixada por comprimento: independente do JSON global.
            List<String> values = List.of("payment-v1", command.integrationId(), command.obligationReference(),
                    command.requestId().toString(), command.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    command.currency(), command.description(), Integer.toString(command.expirationDays()),
                    payer.email(), payer.firstName(), payer.lastName(), payer.identificationType(), payer.identificationNumber(),
                    address.zipCode(), address.streetName(), address.streetNumber(), address.neighborhood(), address.city(), address.state());
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                mac.update(bytes);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException ex) {
            throw new PaymentException(Kind.CONFIGURATION, null);
        }
    }

    private MercadoPagoGateway gateway() {
        MercadoPagoGateway gateway = gateways.getIfAvailable();
        if (gateway == null) throw new PaymentException(Kind.CONFIGURATION, null);
        return gateway;
    }

    private static void validateIdentity(String integrationId, UUID paymentId) {
        if (integrationId == null || integrationId.isBlank() || integrationId.length() > 100 || paymentId == null) {
            throw new PaymentException(Kind.INVALID_INPUT, paymentId);
        }
    }

    private static void requireNoAmbientTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new PaymentException(Kind.AMBIENT_TRANSACTION, null);
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private record Reservation(PaymentResponse payment, UUID key, boolean created) {}
}
