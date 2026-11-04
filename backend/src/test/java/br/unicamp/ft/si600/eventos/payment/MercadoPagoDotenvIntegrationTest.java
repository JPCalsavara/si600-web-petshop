package br.unicamp.ft.si600.eventos.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Executa Config Data em processos reais, com diretórios e credenciais fictícias isolados. */
class MercadoPagoDotenvIntegrationTest {
    @TempDir Path root;

    @Test
    void deveCarregarEnvDaRaizQuandoExecutadoNaRaizOuNoBackend() throws Exception {
        writeEnv(root, "fixture-root-token");
        Path backend = Files.createDirectory(root.resolve("backend"));
        probe(root, "fixture-root-token", null);
        probe(backend, "fixture-root-token", null);
    }

    @Test
    void devePriorizarEnvDoBackendSemPerderConfiguracaoDaRaiz() throws Exception {
        writeEnv(root, "fixture-root-token");
        Path backend = Files.createDirectory(root.resolve("backend"));
        Files.writeString(backend.resolve(".env"), "MP_ACCESS_TOKEN=fixture-backend-token\n");
        probe(root, "fixture-backend-token", null);
        probe(backend, "fixture-backend-token", null);
    }

    @Test
    void devePriorizarAmbienteDoProcessoSobreDotenv() throws Exception {
        writeEnv(root, "fixture-root-token");
        probe(root, "fixture-process-token", "fixture-process-token");
    }

    @Test
    void deveCarregarMesmosValoresPeloApplicationYmlDoBackend() throws Exception {
        writeEnv(root, "fixture-root-token");
        Path backend = Files.createDirectory(root.resolve("backend"));
        String location = Path.of("src/main/resources/application.yml").toAbsolutePath().toUri().toString();
        probe(root, "fixture-root-token", null, location);
        probe(backend, "fixture-root-token", null, location);
    }

    private void writeEnv(Path directory, String token) throws Exception {
        Files.writeString(directory.resolve(".env"), "MP_ENABLED=true\nMP_ACCESS_TOKEN=" + token
                + "\nMP_BASE_URL=http://127.0.0.1:54321\n");
    }

    private void probe(Path directory, String expectedToken, String processToken) throws Exception {
        probe(directory, expectedToken, processToken, "classpath:mercadopago.yml");
    }

    private void probe(Path directory, String expectedToken, String processToken, String location) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(List.of(java, "-cp", classpath,
                getClass().getName(), expectedToken, location)).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.startsWith("MP_") || key.startsWith("MERCADOPAGO_")
                || key.startsWith("SPRING_") || key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS"));
        if (processToken != null) builder.environment().put("MP_ACCESS_TOKEN", processToken);
        Process process = builder.start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Processo isolado de configuração não terminou no prazo.");
        }
        assertThat(process.exitValue()).as("Carregamento de configuração no diretório isolado").isZero();
    }

    public static void main(String[] args) {
        try {
            MercadoPagoSandboxSettings settings = MercadoPagoSandboxSettings.load(args[1]);
            var properties = settings.gatewayProperties();
            if (!properties.enabled() || !args[0].equals(properties.accessToken())
                    || !"http://127.0.0.1:54321".equals(properties.baseUrl())
                    || properties.readTimeout().toSeconds() != 10 || settings.enabledFor("create")) {
                System.exit(1);
            }
        } catch (Exception ex) {
            System.exit(2); // Não imprimir configuração, exceção de binding ou credencial.
        }
    }
}
