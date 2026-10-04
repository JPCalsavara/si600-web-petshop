package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.storage.InMemoryObjectStorage;
import br.unicamp.ft.si600.eventos.storage.ObjectStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestStorageConfig {
    @Bean
    ObjectStorage objectStorage() {
        return new InMemoryObjectStorage();
    }
}
