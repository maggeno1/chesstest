package de.mschanzer.chesstest.chesstest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Für Transaktionsmanagement

import java.util.Optional;

@Service
public class TokenService {

    private final RegistrationTokenRepository tokenRepository;

    public TokenService(RegistrationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Generiert einen neuen, einmalig nutzbaren Registrierungstoken.
     * @return Der generierte RegistrationToken.
     */
    @Transactional
    public RegistrationToken generateNewToken() {
        RegistrationToken newToken = new RegistrationToken();
        return tokenRepository.save(newToken);
    }

    /**
     * Validiert einen gegebenen Token.
     * Ein Token ist gültig, wenn er existiert und noch nicht verwendet wurde.
     * @param tokenString Der zu validierende Token-String.
     * @return Ein Optional, das den RegistrationToken enthält, wenn er gültig ist, sonst leer.
     */
    @Transactional(readOnly = true)
    public Optional<RegistrationToken> validateToken(String tokenString) {
        return tokenRepository.findByToken(tokenString)
                .filter(token -> !token.isUsed()); // Filtert nur unbenutzte Tokens
    }

    /**
     * Deaktiviert einen Token, nachdem er erfolgreich verwendet wurde.
     * @param token Der RegistrationToken, der deaktiviert werden soll.
     */
    @Transactional
    public void invalidateToken(RegistrationToken token) {
        token.setUsed(true);
        tokenRepository.save(token);
    }
}