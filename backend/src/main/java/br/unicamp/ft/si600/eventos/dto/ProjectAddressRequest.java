package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Endereço estruturado do cliente no POST /projects (BUG-07). O CEP aceita máscara 00000-000 ou 8 dígitos. */
public record ProjectAddressRequest(
        @NotBlank(message = "O CEP é obrigatório")
        @Pattern(regexp = "[0-9]{5}-?[0-9]{3}", message = "CEP inválido. Informe 8 dígitos (00000-000).") String zipCode,
        @NotBlank(message = "O logradouro é obrigatório")
        @Size(max = 200, message = "O logradouro deve ter no máximo 200 caracteres") String street,
        @NotBlank(message = "O número é obrigatório")
        @Size(max = 30, message = "O número deve ter no máximo 30 caracteres") String number,
        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres") String complement,
        @NotBlank(message = "O bairro é obrigatório")
        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres") String neighborhood,
        @NotBlank(message = "A cidade é obrigatória")
        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres") String city,
        @NotBlank(message = "A UF é obrigatória")
        @Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO",
                message = "UF inválida") String state
) {}
