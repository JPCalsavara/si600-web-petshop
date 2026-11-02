package br.unicamp.ft.si600.eventos.config;

import br.unicamp.ft.si600.eventos.entity.AreaPricingMode;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

/** Seed idempotente para desenvolvimento: valores marcados explicitamente como demonstração. */
@Configuration
@ConditionalOnProperty(name = "app.fees.seed-demo", havingValue = "true")
public class FeeCatalogSeed {
    @Bean
    ApplicationRunner seedDemoFeeCatalog(FeeRepository repository) {
        return args -> seed(repository);
    }

    @Transactional
    public void seed(FeeRepository repository) {
        Set<String> existing = repository.findAll().stream().map(Fee::getName).collect(Collectors.toSet());
        add(repository, existing, "Aluguel do estande", "Demonstração — valor por m² não oficial; confirmar tarifa com a organização.",
                FeeType.POR_METRAGEM, AreaPricingMode.VALOR_POR_M2, null, "10.00", null, null, null);
        add(repository, existing, "TFE", "Demonstração — valor não oficial; confirmar regra e valor com a organização.",
                FeeType.FIXA, null, "100.00", null, null, null, null);
        add(repository, existing, "Energia elétrica por kVA", "Demonstração — valor por kVA não oficial; confirmar tarifa com a organização.",
                FeeType.VARIAVEL, null, null, null, null, null, "kVA");
        add(repository, existing, "Taxa de limpeza", "Demonstração — valor não oficial; confirmar regra com a organização.",
                FeeType.FIXA, null, "50.00", null, null, null, null);
        add(repository, existing, "Extintor de incêndio", "Demonstração — 1 unidade a cada 25 m², valor unitário não oficial; confirmar regra e preço com a organização.",
                FeeType.POR_METRAGEM, AreaPricingMode.UNIDADES_POR_INTERVALO, null, null, "25.00", "80.00", null);
        add(repository, existing, "Taxa de emissão de credencial extra", "Demonstração — valor não oficial; confirmar preço com a organização.",
                FeeType.FIXA, null, "20.00", null, null, null, null);
    }

    private void add(FeeRepository repository, Set<String> existing, String name, String description,
                     FeeType type, AreaPricingMode mode, String amount, String perM2,
                     String areaPerUnit, String unitAmount, String unit) {
        if (existing.contains(name)) return;
        repository.save(new Fee(name, description, type, mode, decimal(amount), decimal(perM2),
                decimal(areaPerUnit), decimal(unitAmount), unit));
    }

    private BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}
