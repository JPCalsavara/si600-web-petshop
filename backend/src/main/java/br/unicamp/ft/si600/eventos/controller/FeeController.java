package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.*;
import br.unicamp.ft.si600.eventos.security.ActorResolver;
import br.unicamp.ft.si600.eventos.service.FeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fees")
public class FeeController {
    private final FeeService service;
    private final ActorResolver actorResolver;
    public FeeController(FeeService service, ActorResolver actorResolver) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    public ResponseEntity<FeeResponse> create(@Valid @RequestBody FeeRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body, actorResolver.resolve(request)));
    }

    @GetMapping
    public List<FeeResponse> list(HttpServletRequest request) {
        return service.list(actorResolver.resolve(request));
    }

    @GetMapping("/{id}")
    public FeeResponse get(@PathVariable UUID id, HttpServletRequest request) {
        return service.get(id, actorResolver.resolve(request));
    }

    @PutMapping("/{id}")
    public FeeResponse update(@PathVariable UUID id, @Valid @RequestBody FeeRequest body, HttpServletRequest request) {
        return service.update(id, body, actorResolver.resolve(request));
    }

    @PostMapping("/{id}/deactivate")
    public FeeResponse deactivate(@PathVariable UUID id, HttpServletRequest request) {
        return service.deactivate(id, actorResolver.resolve(request));
    }
}
