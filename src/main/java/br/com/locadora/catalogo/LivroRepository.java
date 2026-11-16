package br.com.locadora.catalogo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LivroRepository extends JpaRepository<Livro, UUID> {}
