package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.storage.InMemoryObjectStorage;
import br.unicamp.ft.si600.eventos.storage.ObjectStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestStorageConfig {
    // Nome diferente de "objectStorage" (bean de InMemoryStorageConfiguration): o Spring Boot
    // proíbe sobrescrever definições de bean. @Primary faz o serviço e os testes usarem esta instância.
    @Bean
    @Primary
    ObjectStorage testObjectStorage() {
        return new InMemoryObjectStorage();
    }
}
