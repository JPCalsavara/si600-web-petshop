package br.unicamp.ft.si600.eventos.payment.http;

public final class PaymentHttpPaths {
    private PaymentHttpPaths() {}
    public static String normalized(String path) { return path.replaceAll(";[^/]*", ""); }
    public static boolean internal(String path) { return normalized(path).startsWith("/internal/payments"); }
    public static boolean webhook(String path) { return normalized(path).equals("/webhooks/mercadopago"); }
    public static boolean protectedPath(String path) { return internal(path) || webhook(path); }
}
