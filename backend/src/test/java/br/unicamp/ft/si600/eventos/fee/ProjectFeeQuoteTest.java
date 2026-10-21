package br.unicamp.ft.si600.eventos.fee;

import br.unicamp.ft.si600.eventos.entity.AreaPricingMode;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes essenciais da regra de domínio da US-07 (cotação e recotação). */
class ProjectFeeQuoteTest {
    private ProjectFee newVariableFee() {
        Project project = new Project("cliente-1", "Empresa Teste", "54.018.395/0001-89",
                "Representante", "B2C", OffsetDateTime.now().plusDays(10));
        Fee fee = new Fee("Energia", null, FeeType.VARIAVEL, null, null, null, null, null, "kVA");
        ProjectFee projectFee = new ProjectFee(project, fee);
        projectFee.submitQuantity(new BigDecimal("12.5"), OffsetDateTime.now());
        return projectFee;
    }

    @Test
    void deveRegistrarCotacaoValorStatusEAuditoria() {
        ProjectFee projectFee = newVariableFee();
        OffsetDateTime quotedAt = OffsetDateTime.now();

        projectFee.quote(new BigDecimal("250.00"), "admin-1", quotedAt);

        assertThat(projectFee.getQuotedAmount()).isEqualByComparingTo("250.00");
        assertThat(projectFee.getStatus()).isEqualTo(ProjectFeeStatus.COTADA);
        assertThat(projectFee.getQuotedBy()).isEqualTo("admin-1");
        assertThat(projectFee.getQuotedAt()).isEqualTo(quotedAt);
    }

    @Test
    void devePermitirRecotacaoEAtualizarAuditoria() {
        ProjectFee projectFee = newVariableFee();
        OffsetDateTime firstQuote = OffsetDateTime.now();
        OffsetDateTime secondQuote = firstQuote.plusMinutes(5);
        projectFee.quote(new BigDecimal("250.00"), "admin-1", firstQuote);

        projectFee.quote(new BigDecimal("275.50"), "admin-2", secondQuote);

        assertThat(projectFee.getQuotedAmount()).isEqualByComparingTo("275.50");
        assertThat(projectFee.getQuotedBy()).isEqualTo("admin-2");
        assertThat(projectFee.getQuotedAt()).isEqualTo(secondQuote);
        assertThat(projectFee.getStatus()).isEqualTo(ProjectFeeStatus.COTADA);
    }

    @Test
    void naoDevePermitirCotacaoAposGeracaoDoPagamento() {
        ProjectFee projectFee = newVariableFee();
        projectFee.quote(new BigDecimal("250.00"), "admin-1", OffsetDateTime.now());
        projectFee.markPaymentGenerated();

        assertThatThrownBy(() -> projectFee.quote(new BigDecimal("300.00"), "admin-1", OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(projectFee.getStatus()).isEqualTo(ProjectFeeStatus.PAGAMENTO_GERADO);
        assertThat(projectFee.getQuotedAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void novaQuantidadeDeveDescartarCotacaoAnteriorEVoltarParaAguardandoCotacao() {
        ProjectFee projectFee = newVariableFee();
        projectFee.quote(new BigDecimal("250.00"), "admin-1", OffsetDateTime.now());

        projectFee.submitQuantity(new BigDecimal("20"), OffsetDateTime.now());

        assertThat(projectFee.getStatus()).isEqualTo(ProjectFeeStatus.AGUARDANDO_COTACAO);
        assertThat(projectFee.getQuotedAmount()).isNull();
        assertThat(projectFee.getQuotedBy()).isNull();
        assertThat(projectFee.getQuotedAt()).isNull();
    }

    @Test
    void taxaFixaOuPorMetragemNasceDisponivelETaxaVariavelAguardaQuantidade() {
        Project project = new Project("cliente-1", "Empresa Teste", "54.018.395/0001-89",
                "Representante", "B2C", OffsetDateTime.now().plusDays(10));
        Fee fixed = new Fee("TFE", null, FeeType.FIXA, null, new BigDecimal("100.00"), null, null, null, null);
        Fee perArea = new Fee("Aluguel", null, FeeType.POR_METRAGEM, AreaPricingMode.VALOR_POR_M2,
                null, new BigDecimal("10.00"), null, null, null);
        Fee variable = new Fee("Energia", null, FeeType.VARIAVEL, null, null, null, null, null, "kVA");

        assertThat(new ProjectFee(project, fixed).getStatus()).isEqualTo(ProjectFeeStatus.DISPONIVEL);
        assertThat(new ProjectFee(project, perArea).getStatus()).isEqualTo(ProjectFeeStatus.DISPONIVEL);
        assertThat(new ProjectFee(project, variable).getStatus()).isEqualTo(ProjectFeeStatus.AGUARDANDO_QUANTIDADE);
    }
}
