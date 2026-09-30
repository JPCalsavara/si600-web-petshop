package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<HealthResponse> checkHealth() {
        HealthResponse response = new HealthResponse(
                "UP",
                "Backend SI600 Sistema de Eventos operacional",
                Instant.now()
        );
        return ResponseEntity.ok(response);
    }
}
