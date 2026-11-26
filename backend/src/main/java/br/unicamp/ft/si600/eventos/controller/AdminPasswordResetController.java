package br.unicamp.ft.si600.eventos.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.unicamp.ft.si600.eventos.dto.PasswordResetRequestResponse;
import br.unicamp.ft.si600.eventos.dto.TemporaryPasswordResponse;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;

/** US-14: painel do Admin para atender pedidos de recuperação de acesso. */
@RestController
@RequestMapping("/admin/password-resets")
public class AdminPasswordResetController {
    private final AuthService authService;
    private final ActorResolver actorResolver;

    public AdminPasswordResetController(AuthService authService, ActorResolver actorResolver) {
        this.authService = authService;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    public List<PasswordResetRequestResponse> listPending(HttpServletRequest request) {
        return authService.listPendingPasswordResets(actorResolver.resolve(request));
    }

    @PostMapping("/{id}/resolve")
    public TemporaryPasswordResponse resolve(@PathVariable UUID id, HttpServletRequest request) {
        return authService.resolvePasswordReset(id, actorResolver.resolve(request));
    }
}
