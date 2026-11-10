package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.LoginRequest;
import br.unicamp.ft.si600.eventos.dto.LoginResponse;
import br.unicamp.ft.si600.eventos.dto.MeResponse;
import br.unicamp.ft.si600.eventos.security.ActorFilter;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** US-01: login, logout e consulta da sessão atual. */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final ActorResolver actorResolver;

    public AuthController(AuthService authService, ActorResolver actorResolver) {
        this.authService = authService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest body) {
        return authService.login(body.email(), body.password());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(ActorFilter.bearerToken(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        return authService.me(actorResolver.resolve(request));
    }
}
