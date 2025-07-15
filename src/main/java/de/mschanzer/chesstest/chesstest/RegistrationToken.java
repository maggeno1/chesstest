package de.mschanzer.chesstest.chesstest;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID; // Für die Token-Generierung

@Table("registration_tokens") // Name der Datenbanktabelle
public class RegistrationToken {

    @Id
    private Long id;
    private String token;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isUsed; // true, wenn der Token verwendet wurde

    // Konstruktor, um einen neuen Token zu erzeugen
    public RegistrationToken() {
        this.token = UUID.randomUUID().toString(); // Generiert einen einzigartigen Token
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusMinutes(5);
        this.isUsed = false; // Initial ist der Token nicht verwendet
    }

    // Getter und Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Getter und Setter für expiresAt HINZUFÜGEN
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return isUsed;
    }

    public void setUsed(boolean used) {
        this.isUsed = used;
    }

    @Override
    public String toString() {
        return "RegistrationToken{" +
                "id=" + id +
                ", token='" + token + '\'' +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", is_used=" + isUsed +
                '}';
    }
}