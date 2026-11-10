package br.unicamp.ft.si600.eventos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import br.unicamp.ft.si600.eventos.service.AuthService;

/**
 * US-01: cria o Admin inicial se ADMIN_EMAIL e ADMIN_PASSWORD estiverem definidos
 * (variáveis de ambiente ou backend/.env.local). Sem as variáveis, não faz nada.
 */
@Component
public class AdminSeed implements ApplicationRunner {
    private final AuthService authService;
    private final String email;
    private final String password;

    public AdminSeed(AuthService authService,
                     @Value("${app.auth.admin-email:${ADMIN_EMAIL:}}") String email,
                     @Value("${app.auth.admin-password:${ADMIN_PASSWORD:}}") String password) {
        this.authService = authService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) return;
        authService.ensureAdmin(email, password);
    }
}
