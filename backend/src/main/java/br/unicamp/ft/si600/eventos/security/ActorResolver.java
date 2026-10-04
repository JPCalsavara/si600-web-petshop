package br.unicamp.ft.si600.eventos.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ActorResolver {
    public Actor resolve(HttpServletRequest request) {
        Actor actor = (Actor) request.getAttribute(ActorFilter.ACTOR_ATTRIBUTE);
        if (actor == null) {
            throw new IllegalStateException("Ator autenticado ausente");
        }
        return actor;
    }
}
