package br.unicamp.ft.si600.eventos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Endereço estruturado do cliente (BUG-07), necessário para o pagador do boleto no Mercado Pago.
 * Projetos anteriores ao campo ficam com todas as colunas nulas e mantêm só o texto livre em {@code Project.address}.
 */
@Embeddable
public class ProjectAddress {
    @Column(name = "address_zip_code", length = 8)
    private String zipCode;

    @Column(name = "address_street", length = 200)
    private String street;

    @Column(name = "address_number", length = 30)
    private String number;

    @Column(name = "address_complement", length = 100)
    private String complement;

    @Column(name = "address_neighborhood", length = 100)
    private String neighborhood;

    @Column(name = "address_city", length = 100)
    private String city;

    @Column(name = "address_state", length = 2)
    private String state;

    protected ProjectAddress() {}

    /** Recebe o CEP só com os 8 dígitos; o chamador valida o formato. */
    public ProjectAddress(String zipCode, String street, String number, String complement,
                          String neighborhood, String city, String state) {
        this.zipCode = zipCode;
        this.street = street.trim();
        this.number = number.trim();
        this.complement = complement == null || complement.isBlank() ? null : complement.trim();
        this.neighborhood = neighborhood.trim();
        this.city = city.trim();
        this.state = state;
    }

    /** Texto livre equivalente, usado em Project.address (CSV e telas que exibem uma linha só). */
    public String toDisplayText() {
        String line = street + ", " + number;
        if (complement != null) line += " - " + complement;
        return line + " - " + neighborhood + ", " + city + "/" + state
                + " - CEP " + zipCode.substring(0, 5) + "-" + zipCode.substring(5);
    }

    public String getZipCode() { return zipCode; }
    public String getStreet() { return street; }
    public String getNumber() { return number; }
    public String getComplement() { return complement; }
    public String getNeighborhood() { return neighborhood; }
    public String getCity() { return city; }
    public String getState() { return state; }
}
