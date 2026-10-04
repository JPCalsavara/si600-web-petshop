package br.unicamp.ft.si600.eventos.config;

import br.unicamp.ft.si600.eventos.storage.InMemoryObjectStorage;
import br.unicamp.ft.si600.eventos.storage.ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "storage.b2.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryStorageConfiguration {

    @Bean
    ObjectStorage objectStorage() {
        return new InMemoryObjectStorage();
    }
}
