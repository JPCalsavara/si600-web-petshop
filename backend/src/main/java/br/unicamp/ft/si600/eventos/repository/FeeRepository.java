package br.unicamp.ft.si600.eventos.repository;

import br.unicamp.ft.si600.eventos.entity.Fee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface FeeRepository extends JpaRepository<Fee, UUID> {}
