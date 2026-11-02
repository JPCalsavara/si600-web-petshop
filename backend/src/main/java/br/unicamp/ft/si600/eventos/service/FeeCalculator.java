package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.entity.AreaPricingMode;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * US-05: valor das taxas fixas e por metragem, calculado a partir da regra do catálogo e da metragem
 * oficial do estande. Taxas variáveis não têm valor calculado: dependem da cotação do Admin (US-07).
 */
public final class FeeCalculator {
    private FeeCalculator() {}

    /**
     * @param value    valor em R$ (null quando não há como calcular)
     * @param quantity quantidade cobrada: m² (valor por m²) ou número de unidades (unidades por intervalo)
     * @param unit     unidade da quantidade
     */
    public record Result(BigDecimal value, BigDecimal quantity, String unit) {
        static final Result NONE = new Result(null, null, null);
    }

    public static Result calculate(Fee fee, BigDecimal areaM2) {
        if (fee.getType() == FeeType.FIXA) {
            return new Result(scale(fee.getAmount()), null, null);
        }
        if (fee.getType() != FeeType.POR_METRAGEM || fee.getAreaPricingMode() == null) {
            return Result.NONE;
        }
        if (fee.getAreaPricingMode() == AreaPricingMode.VALOR_POR_M2) {
            BigDecimal value = areaM2 == null || fee.getAmountPerM2() == null
                    ? null : scale(areaM2.multiply(fee.getAmountPerM2()));
            return new Result(value, areaM2, "m²");
        }
        // Unidades por intervalo: "1 unidade a cada X m²", sempre arredondando para cima (US-02).
        if (areaM2 == null || fee.getAreaPerUnitM2() == null || fee.getAreaPerUnitM2().signum() <= 0) {
            return new Result(null, null, "unidade");
        }
        BigDecimal units = areaM2.divide(fee.getAreaPerUnitM2(), 0, RoundingMode.CEILING);
        BigDecimal value = fee.getUnitAmount() == null ? null : scale(units.multiply(fee.getUnitAmount()));
        return new Result(value, units, "unidade");
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
