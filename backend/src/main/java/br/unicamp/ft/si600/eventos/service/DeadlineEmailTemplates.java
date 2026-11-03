package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.entity.DeadlineType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** US-10: textos dos e-mails. Sempre dizem o Projeto, o prazo e a ação pendente. */
final class DeadlineEmailTemplates {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    record Message(String subject, String body) {}

    private DeadlineEmailTemplates() {}

    static Message build(DeadlineType type, Project project, DeadlineEvaluator.Evaluation evaluation,
                         List<String> pendingFeeNames) {
        String when = when(evaluation);
        String deadline = DATE.format(evaluation.deadlineDate());
        String company = project.getCompanyName();

        return switch (type) {
            case PDF_CLIENTE -> {
                boolean rejected = project.getStatus() == ProjectStatus.REPROVADO;
                String action = rejected
                        ? "Ajuste o projeto conforme a justificativa da reprovação e envie o PDF novamente pelo sistema."
                        : "Envie o PDF do estande e informe a metragem pelo sistema.";
                yield new Message(
                        "Prazo de envio do PDF " + shortWhen(evaluation) + " - " + company,
                        """
                        Olá, %s.

                        O prazo para envio do PDF do estande %s.

                        Projeto: %s
                        Prazo limite para envio do PDF: %s
                        Ação pendente: %s

                        Esta é uma mensagem automática do Sistema de Eventos.
                        """.formatted(project.getRepresentativeName(), when, company, deadline, action));
            }
            case APROVACAO_ADMIN -> new Message(
                    "PDF aguardando aprovação, prazo " + shortWhen(evaluation) + " - " + company,
                    """
                    Olá.

                    O PDF do estande do projeto abaixo está aguardando análise e o prazo de envio %s.

                    Projeto: %s
                    Cliente: %s
                    Prazo limite para envio do PDF: %s
                    Ação pendente: analisar o PDF e aprová-lo ou reprová-lo no sistema.

                    Esta é uma mensagem automática do Sistema de Eventos.
                    """.formatted(when, company, project.getRepresentativeName(), deadline));
            case PAGAMENTO_CLIENTE -> new Message(
                    "Prazo de pagamento das taxas " + shortWhen(evaluation) + " - " + company,
                    """
                    Olá, %s.

                    O prazo para pagamento das taxas do estande %s.

                    Projeto: %s
                    Prazo limite de pagamento: %s
                    Taxas pendentes:
                    %s
                    Ação pendente: gere e pague o pagamento de cada taxa pelo sistema.

                    Esta é uma mensagem automática do Sistema de Eventos.
                    """.formatted(project.getRepresentativeName(), when, company, deadline, bullets(pendingFeeNames)));
        };
    }

    private static String bullets(List<String> names) {
        if (names.isEmpty()) return "- (nenhuma)";
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            if (out.length() > 0) out.append('\n');
            out.append("- ").append(name);
        }
        return out.toString();
    }

    /** Frase para o corpo, ex.: "vence em 3 dias" / "venceu há 2 dias". */
    private static String when(DeadlineEvaluator.Evaluation e) {
        long d = e.daysRemaining();
        if (d < 0) return "venceu há " + plural(-d);
        if (d == 0) return "vence hoje";
        if (d == 1) return "vence amanhã";
        return "vence em " + plural(d);
    }

    /** Versão curta para o assunto. */
    private static String shortWhen(DeadlineEvaluator.Evaluation e) {
        long d = e.daysRemaining();
        if (d < 0) return "atrasado";
        if (d == 0) return "vence hoje";
        if (d == 1) return "vence amanhã";
        return "vence em " + plural(d);
    }

    private static String plural(long days) {
        return days + (days == 1 ? " dia" : " dias");
    }
}
