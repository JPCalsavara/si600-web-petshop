package br.unicamp.ft.si600.eventos.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fees")
public class Fee {
    @Id @GeneratedValue
    private UUID id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 2000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private FeeType type;
    @Column(nullable = false)
    private boolean active = true;
    @Enumerated(EnumType.STRING) @Column(length = 40)
    private AreaPricingMode areaPricingMode;
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(name = "amount_per_m2", precision = 14, scale = 2)
    private BigDecimal amountPerM2;
    @Column(name = "area_per_unit_m2", precision = 12, scale = 2)
    private BigDecimal areaPerUnitM2;
    @Column(precision = 14, scale = 2)
    private BigDecimal unitAmount;
    @Column(length = 100)
    private String measurementUnit;

    protected Fee() {}

    public Fee(String name, String description, FeeType type, AreaPricingMode mode,
               BigDecimal amount, BigDecimal amountPerM2, BigDecimal areaPerUnitM2,
               BigDecimal unitAmount, String measurementUnit) {
        update(name, description, type, mode, amount, amountPerM2, areaPerUnitM2, unitAmount, measurementUnit);
    }

    public void update(String name, String description, FeeType type, AreaPricingMode mode,
                       BigDecimal amount, BigDecimal amountPerM2, BigDecimal areaPerUnitM2,
                       BigDecimal unitAmount, String measurementUnit) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.areaPricingMode = mode;
        this.amount = amount;
        this.amountPerM2 = amountPerM2;
        this.areaPerUnitM2 = areaPerUnitM2;
        this.unitAmount = unitAmount;
        this.measurementUnit = measurementUnit;
    }

    public void deactivate() { active = false; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public FeeType getType() { return type; }
    public boolean isActive() { return active; }
    public AreaPricingMode getAreaPricingMode() { return areaPricingMode; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getAmountPerM2() { return amountPerM2; }
    public BigDecimal getAreaPerUnitM2() { return areaPerUnitM2; }
    public BigDecimal getUnitAmount() { return unitAmount; }
    public String getMeasurementUnit() { return measurementUnit; }
}
