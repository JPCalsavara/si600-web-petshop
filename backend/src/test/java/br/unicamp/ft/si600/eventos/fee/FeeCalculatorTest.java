package br.unicamp.ft.si600.eventos.fee;

import br.unicamp.ft.si600.eventos.entity.AreaPricingMode;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.service.FeeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** US-05/US-02: cálculo do valor das taxas fixas e por metragem. */
class FeeCalculatorTest {
    private Fee fixed(String amount) {
        return new Fee("TFE", null, FeeType.FIXA, null, new BigDecimal(amount), null, null, null, null);
    }

    private Fee perM2(String amountPerM2) {
        return new Fee("Aluguel", null, FeeType.POR_METRAGEM, AreaPricingMode.VALOR_POR_M2,
                null, new BigDecimal(amountPerM2), null, null, null);
    }

    private Fee perInterval(String areaPerUnit, String unitAmount) {
        return new Fee("Extintor", null, FeeType.POR_METRAGEM, AreaPricingMode.UNIDADES_POR_INTERVALO,
                null, null, new BigDecimal(areaPerUnit), new BigDecimal(unitAmount), null);
    }

    @Test
    void taxaFixaUsaOValorUnicoSemDependerDaMetragem() {
        FeeCalculator.Result result = FeeCalculator.calculate(fixed("100"), null);

        assertThat(result.value()).isEqualByComparingTo("100.00");
        assertThat(result.quantity()).isNull();
    }

    @Test
    void valorPorM2MultiplicaMetragemPeloValor() {
        FeeCalculator.Result result = FeeCalculator.calculate(perM2("10.50"), new BigDecimal("42.50"));

        assertThat(result.value()).isEqualByComparingTo("446.25");
        assertThat(result.quantity()).isEqualByComparingTo("42.50");
        assertThat(result.unit()).isEqualTo("m²");
    }

    @Test
    void extintorACada25M2EmEstandeDe50M2ResultaEmDuasUnidades() {
        FeeCalculator.Result result = FeeCalculator.calculate(perInterval("25", "80"), new BigDecimal("50"));

        assertThat(result.quantity()).isEqualByComparingTo("2");
        assertThat(result.value()).isEqualByComparingTo("160.00");
    }

    @Test
    void unidadesPorIntervaloArredondamParaCima() {
        FeeCalculator.Result result = FeeCalculator.calculate(perInterval("25", "80"), new BigDecimal("51"));

        assertThat(result.quantity()).isEqualByComparingTo("3");
        assertThat(result.value()).isEqualByComparingTo("240.00");
    }

    @Test
    void semMetragemNaoHaValorCalculadoParaTaxaPorMetragem() {
        assertThat(FeeCalculator.calculate(perM2("10"), null).value()).isNull();
        assertThat(FeeCalculator.calculate(perInterval("25", "80"), null).value()).isNull();
    }

    @Test
    void taxaVariavelNaoTemValorCalculado() {
        Fee variable = new Fee("Energia", null, FeeType.VARIAVEL, null, null, null, null, null, "kVA");

        assertThat(FeeCalculator.calculate(variable, new BigDecimal("50")).value()).isNull();
    }
}
