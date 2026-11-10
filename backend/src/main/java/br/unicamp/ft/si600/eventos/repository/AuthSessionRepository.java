package br.unicamp.ft.si600.eventos.repository;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.unicamp.ft.si600.eventos.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    /** Apaga de uma vez todas as sessões que expiraram antes de {@code limit}; devolve quantas foram apagadas. */
    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :limit")
    int deleteExpired(@Param("limit") OffsetDateTime limit);
}
