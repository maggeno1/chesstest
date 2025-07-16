package de.mschanzer.chesstest.chesstest;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table; // Optional, wenn Tabellenname vom Klassennamen abweicht

import java.time.LocalDateTime;
import java.util.UUID;

@Entity // <-- Wichtig: Dies kennzeichnet die Klasse als JPA-Entität
@Table(name = "registration_tokens") // <-- Optional: Wenn der Tabellenname vom Klassennamen abweicht
public class RegistrationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // <-- Generierungsstrategie für die ID
    private Long id;
    private String token;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isUsed;

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