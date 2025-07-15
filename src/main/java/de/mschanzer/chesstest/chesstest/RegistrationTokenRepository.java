package de.mschanzer.chesstest.chesstest;

import org.springframework.data.repository.CrudRepository;
import java.util.Optional;

public interface RegistrationTokenRepository extends CrudRepository<RegistrationToken, Long> {

    // Methode, um einen Token anhand seines String-Wertes zu finden
    Optional<RegistrationToken> findByToken(String token);
}