package br.unicamp.ft.si600.eventos.notification;

import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.project.CapturingEmailSender;
import br.unicamp.ft.si600.eventos.project.TestEmailConfig;
import br.unicamp.ft.si600.eventos.project.TestStorageConfig;
import br.unicamp.ft.si600.eventos.repository.DeadlineNotificationRepository;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.service.DeadlineNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-10: e-mails de prazo. "Hoje" é 10/11/2026 (08:00 em São Paulo); o relógio avança entre as execuções.
 * Cobre também os marcos por data no fuso do evento e a resolução do destinatário do Admin
 * (GMAIL_ADMIN, senão GMAIL_SENDER), sempre pelo seam público {@code DeadlineNotificationService.run()}.
 */
@SpringBootTest(properties = {
        "storage.b2.enabled=false",
        "notification.gmail.admin-email=admin@evento.com"
})
@Import({TestStorageConfig.class, TestEmailConfig.class, TestClockConfig.class})
class DeadlineNotificationIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final ZoneOffset SP = ZoneOffset.ofHours(-3);

    @Autowired DeadlineNotificationService service;
    @Autowired ProjectRepository projects;
    @Autowired FeeRepository fees;
    @Autowired ProjectFeeRepository projectFees;
    @Autowired DeadlineNotificationRepository notifications;
    @Autowired CapturingEmailSender emails;
    @Autowired MutableTestClock clock;
    @Autowired GmailProperties gmailProperties;

    private int seq;
    private String originalAdminEmail;
    private String originalSender;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        projectFees.deleteAll();
        projects.deleteAll();
        fees.deleteAll();
        emails.clear();
        clock.set(TestClockConfig.START);
        originalAdminEmail = gmailProperties.getAdminEmail();
        originalSender = gmailProperties.getSender();
    }

    @AfterEach
    void restoreGmailProperties() {
        gmailProperties.setAdminEmail(originalAdminEmail);
        gmailProperties.setSender(originalSender);
    }

    // --- Cliente: prazo de envio do PDF -------------------------------------------------------

    @Test
    void avisaOClienteTresDiasAntesDoPrazoDeEnvioDoPdf() {
        seedProject("cliente@exemplo.com", daysFromToday(3), daysFromToday(20));

        int sent = service.run();

        assertThat(sent).isEqualTo(1);
        CapturingEmailSender.Sent mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("cliente@exemplo.com");
        assertThat(mail.subject()).contains("envio do PDF", "vence em 3 dias", "Empresa Exemplo Ltda.");
        assertThat(mail.body()).contains("Empresa Exemplo Ltda.", "13/11/2026", "Envie o PDF");
    }

    @Test
    void naoAvisaQuandoOPrazoAindaEstaAMaisDeTresDias() {
        seedProject("cliente@exemplo.com", daysFromToday(4), daysFromToday(20));

        assertThat(service.run()).isZero();
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void enviaUmEmailPorMarcoENaoReenviaEnquantoOMarcoNaoMuda() {
        seedProject("cliente@exemplo.com", daysFromToday(3), daysFromToday(30));

        assertThat(service.run()).isEqualTo(1);      // 3 dias antes (13/11)
        assertThat(service.run()).isZero();          // mesma execução de novo: sem duplicar
        clock.advanceDays(1);
        assertThat(service.run()).isZero();          // 2 dias antes: ainda o marco de 3 dias
        clock.advanceDays(1);
        assertThat(service.run()).isEqualTo(1);      // 1 dia antes
        clock.advanceDays(1);
        assertThat(service.run()).isEqualTo(1);      // no dia
        clock.advanceDays(1);
        assertThat(service.run()).isEqualTo(1);      // atrasado
        clock.advanceDays(1);
        assertThat(service.run()).isZero();          // atrasado já avisado: sem spam diário
        clock.advanceDays(10);
        assertThat(service.run()).isZero();

        assertThat(emails.sent()).hasSize(4);
        assertThat(emails.sent().get(1).subject()).contains("vence amanhã");
        assertThat(emails.sent().get(2).subject()).contains("vence hoje");
        assertThat(emails.sent().get(3).subject()).contains("atrasado");
        assertThat(emails.sent().get(3).body()).contains("venceu há 1 dia");
    }

    @Test
    void pdfReprovadoPedeOReenvioAoCliente() {
        Project project = seedProject("cliente@exemplo.com", daysFromToday(1), daysFromToday(20));
        project.submitPdf("k", "p.pdf", "application/pdf", 10L, new BigDecimal("10"), OffsetDateTime.now());
        project.reject("admin-1", OffsetDateTime.now(), "Altura acima do máximo.");
        projects.save(project);

        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).to()).isEqualTo("cliente@exemplo.com");
        assertThat(emails.sent().get(0).body()).contains("novamente");
    }

    // --- Marcos e fuso (por data em America/Sao_Paulo) -----------------------------------------

    @Test
    void doisDiasAntesAindaEstaNoMarcoDeTresDias() {
        // 12/11 10:00 -> 2 dias: marco D_MENOS_3
        seedProject("cliente@exemplo.com", TODAY.plusDays(2).atTime(10, 0).atOffset(SP), daysFromToday(20));
        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).subject()).contains("vence em 2 dias");
    }

    @Test
    void noDiaDoPrazoAvisaMesmoQueAHoraDoPrazoJaTenhaPassado() {
        // 10/11 00:30 -> a rotina roda às 08:00, mas o prazo vale o dia inteiro
        seedProject("cliente@exemplo.com", TODAY.atTime(0, 30).atOffset(SP), daysFromToday(20));
        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).subject()).contains("vence hoje");
    }

    @Test
    void diaAnteriorAoPrazoEstaAtrasado() {
        seedProject("cliente@exemplo.com", TODAY.minusDays(1).atTime(23, 59).atOffset(SP), daysFromToday(20));
        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).subject()).contains("atrasado");
        assertThat(emails.sent().get(0).body()).contains("venceu há 1 dia");
    }

    @Test
    void comparaPelaDataDoFusoDoEventoENaoPeloUtc() {
        // 11/11 01:00 UTC == 10/11 22:00 em São Paulo: ainda é o dia do prazo.
        seedProject("cliente@exemplo.com", OffsetDateTime.parse("2026-11-11T01:00:00Z"), daysFromToday(20));
        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).subject()).contains("vence hoje");
        assertThat(emails.sent().get(0).body()).contains("10/11/2026");
    }

    // --- Destinatário do Admin ----------------------------------------------------------------

    @Test
    void adminSemEmailDedicadoRecebeNoRemetente() {
        gmailProperties.setAdminEmail(null);
        gmailProperties.setSender("remetente@gmail.com");
        submitPdf(seedProject("cliente@exemplo.com", daysFromToday(1), daysFromToday(20)));

        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).to()).isEqualTo("remetente@gmail.com");
    }

    @Test
    void adminEmBrancoRecebeNoRemetenteEEmailDoAdminEhAparado() {
        gmailProperties.setAdminEmail("  ");
        gmailProperties.setSender(" remetente@gmail.com ");
        submitPdf(seedProject("cliente@exemplo.com", daysFromToday(1), daysFromToday(20)));
        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).to()).isEqualTo("remetente@gmail.com");

        emails.clear();
        notifications.deleteAll();
        gmailProperties.setAdminEmail(" admin@evento.com ");
        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).to()).isEqualTo("admin@evento.com");
    }

    @Test
    void semAdminESemRemetenteNaoHaEnvioENemQuebraARotina() {
        gmailProperties.setAdminEmail(null);
        gmailProperties.setSender(null);
        submitPdf(seedProject("cliente@exemplo.com", daysFromToday(1), daysFromToday(20)));
        seedProject("outro@exemplo.com", daysFromToday(1), daysFromToday(20));

        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).to()).isEqualTo("outro@exemplo.com");
        assertThat(notifications.count()).isEqualTo(1);
    }

    // --- Pendência regularizada ---------------------------------------------------------------

    @Test
    void naoCobraOClienteQuandoOPdfJaFoiEnviado() {
        Project project = seedProject("cliente@exemplo.com", daysFromToday(1), daysFromToday(20));
        submitPdf(project);

        service.run();

        assertThat(emails.sent()).noneMatch(m -> m.to().equals("cliente@exemplo.com"));
    }

    @Test
    void naoCobraOClienteQuandoOPdfJaFoiAprovadoENaoHaTaxas() {
        Project project = seedProject("cliente@exemplo.com", daysFromToday(0), daysFromToday(1));
        submitPdf(project);
        project.approve("admin-1", OffsetDateTime.now(), new BigDecimal("50"));
        projects.save(project);

        assertThat(service.run()).isZero();
    }

    // --- Admin: PDF aguardando aprovação ------------------------------------------------------

    @Test
    void avisaOAdminQuandoOPdfEstaEmAnaliseEOPrazoSeAproxima() {
        Project project = seedProject("cliente@exemplo.com", daysFromToday(1), daysFromToday(20));
        submitPdf(project);

        assertThat(service.run()).isEqualTo(1);

        CapturingEmailSender.Sent mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("admin@evento.com");
        assertThat(mail.subject()).contains("aguardando aprovação", "vence amanhã", "Empresa Exemplo Ltda.");
        assertThat(mail.body()).contains("aprová-lo ou reprová-lo");
    }

    // --- Cliente: prazo de pagamento ----------------------------------------------------------

    @Test
    void avisaOClienteDoPrazoDePagamentoListandoAsTaxasPendentes() {
        Project project = seedProject("cliente@exemplo.com", daysFromToday(-10), daysFromToday(3));
        submitPdf(project);
        project.approve("admin-1", OffsetDateTime.now(), new BigDecimal("50"));
        projects.save(project);
        addFee(project, "TFE");
        addFee(project, "Taxa de limpeza");

        assertThat(service.run()).isEqualTo(1);

        CapturingEmailSender.Sent mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("cliente@exemplo.com");
        assertThat(mail.subject()).contains("pagamento das taxas", "vence em 3 dias");
        assertThat(mail.body()).contains("- TFE", "- Taxa de limpeza", "13/11/2026");
    }

    @Test
    void naoCobraTaxaComPagamentoJaGerado() {
        Project project = seedProject("cliente@exemplo.com", daysFromToday(-10), daysFromToday(3));
        approveProject(project);
        addFee(project, "TFE", true);
        addFee(project, "Taxa de limpeza", false);

        assertThat(service.run()).isEqualTo(1);

        CapturingEmailSender.Sent mail = emails.sent().get(0);
        assertThat(mail.body()).contains("- Taxa de limpeza").doesNotContain("- TFE");
    }

    @Test
    void naoCobraQuandoTodasAsTaxasTemPagamentoGerado() {
        Project project = seedProject("cliente@exemplo.com", daysFromToday(-10), daysFromToday(1));
        approveProject(project);
        addFee(project, "TFE", true);
        addFee(project, "Taxa de limpeza", true);

        assertThat(service.run()).isZero();
        assertThat(emails.sent()).isEmpty();
    }

    // --- Falhas e prazos alterados ------------------------------------------------------------

    @Test
    void falhaNoEnvioNaoRegistraOMarcoETentaDeNovoNaProximaRotina() {
        seedProject("cliente@exemplo.com", daysFromToday(3), daysFromToday(20));
        emails.failing(true);

        assertThat(service.run()).isZero();
        assertThat(notifications.count()).isZero();

        emails.failing(false);
        assertThat(service.run()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void projetoSemEmailDeContatoNaoQuebraARotina() {
        seedProject(null, daysFromToday(1), daysFromToday(20));
        seedProject("outro@exemplo.com", daysFromToday(1), daysFromToday(20));

        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent().get(0).to()).isEqualTo("outro@exemplo.com");
    }

    @Test
    void novoPrazoDefinidoPeloAdminVoltaAAvisar() {
        Project project = seedProject("cliente@exemplo.com", daysFromToday(1), daysFromToday(20));
        assertThat(service.run()).isEqualTo(1);

        project.updateDeadlines(daysFromToday(3), daysFromToday(20));
        projects.save(project);

        assertThat(service.run()).isEqualTo(1);
        assertThat(emails.sent()).hasSize(2);
    }

    // --- apoio --------------------------------------------------------------------------------

    private static OffsetDateTime daysFromToday(int days) {
        return TODAY.plusDays(days).atTime(18, 0).atOffset(SP);
    }

    private Project seedProject(String email, OffsetDateTime pdfDeadline, OffsetDateTime paymentDeadline) {
        int n = ++seq;
        Project project = Project.create("owner-" + n, "Empresa Exemplo Ltda.", "00.000.000/000" + n + "-00",
                "0000000000" + String.format("%04d", n), "Joãozinho da Silva", "B2C", "Rua X, 100",
                email, pdfDeadline, paymentDeadline, "admin-1", OffsetDateTime.now());
        return projects.save(project);
    }

    private void submitPdf(Project project) {
        project.submitPdf("projects/x/pdf/y.pdf", "planta.pdf", "application/pdf", 100L,
                new BigDecimal("42.50"), OffsetDateTime.now());
        projects.save(project);
    }

    private void approveProject(Project project) {
        submitPdf(project);
        project.approve("admin-1", OffsetDateTime.now(), new BigDecimal("50"));
        projects.save(project);
    }

    private void addFee(Project project, String name) {
        addFee(project, name, false);
    }

    private void addFee(Project project, String name, boolean paymentGenerated) {
        Fee fee = fees.save(new Fee(name, null, FeeType.FIXA, null, new BigDecimal("100.00"),
                null, null, null, null));
        ProjectFee projectFee = new ProjectFee(project, fee);
        if (paymentGenerated) projectFee.markPaymentGenerated();
        projectFees.save(projectFee);
    }
}
