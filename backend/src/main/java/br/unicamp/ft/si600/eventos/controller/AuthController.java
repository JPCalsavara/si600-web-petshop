package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.ChangePasswordRequest;
import br.unicamp.ft.si600.eventos.dto.ForgotPasswordRequest;
import br.unicamp.ft.si600.eventos.dto.ForgotPasswordResponse;
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

/** US-01: login, logout e consulta da sessão atual. US-14: troca de senha e pedido de recuperação. */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final String FORGOT_PASSWORD_MESSAGE =
            "Se o e-mail estiver cadastrado, o pedido foi enviado ao administrador, que informará uma senha temporária.";

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

    /** US-14: troca de senha com a senha atual. 422 (e não 401) quando a senha atual está errada. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                               HttpServletRequest request) {
        authService.changePassword(actorResolver.resolve(request), ActorFilter.bearerToken(request),
                body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** US-14: pública (liberada em PublicPaths). Resposta igual exista a conta ou não. */
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        authService.requestPasswordReset(body.email());
        return ResponseEntity.accepted().body(new ForgotPasswordResponse(FORGOT_PASSWORD_MESSAGE));
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        return authService.me(actorResolver.resolve(request));
    }
}
