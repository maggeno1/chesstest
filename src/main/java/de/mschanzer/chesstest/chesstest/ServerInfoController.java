package de.mschanzer.chesstest.chesstest; // Passe das Paket an dein Projekt an

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController; // Oder @Controller, falls du es schon hast

import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController // Wichtig: Stelle sicher, dass dies ein @RestController ist
public class ServerInfoController { // Oder nutze deinen bestehenden TeilnehmerController

    @GetMapping("/api/server-ip")
    public ResponseEntity<String> getServerIpAddress() {
        try {
            // Ermittelt die IP-Adresse des lokalen Hosts.
            // Beachte: Bei komplexen Netzwerkkonfigurationen (Docker, Mehrere Netzwerkkarten)
            // kann dies eine interne IP sein. Für einfache Setups ist es meist korrekt.
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            return ResponseEntity.ok(ipAddress);
        } catch (UnknownHostException e) {
            // Falls die IP-Adresse nicht ermittelt werden kann
            System.err.println("Fehler beim Ermitteln der Server-IP: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not determine server IP address.");
        }
    }
}