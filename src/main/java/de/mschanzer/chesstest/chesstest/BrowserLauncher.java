package de.mschanzer.chesstest.chesstest; // Passe das Paket an dein Projekt an

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.InetAddress;
import java.net.URI;

@Component
public class BrowserLauncher implements ApplicationRunner {

    // Den Port aus application.properties injizieren
    @Value("${server.port:8080}") // Standardwert 8080, falls nicht gesetzt
    private int port;

    // Die URL-Pfad zum Dashboard aus application.properties injizieren
    @Value("${chesstest.admin-dashboard.url:/user.html}")
    private String adminDashboardPath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                // Lokale IP-Adresse ermitteln
                String ipAddress = InetAddress.getLocalHost().getHostAddress();
                // Die vollständige URL zusammenbauen
                URI uri = new URI("http://" + ipAddress + ":" + port + adminDashboardPath);

                System.out.println("Öffne Admin-Dashboard im Browser: " + uri);
                Desktop.getDesktop().browse(uri);
            } catch (Exception e) {
                System.err.println("Fehler beim Öffnen des Browsers: " + e.getMessage());
                // Fallback zu localhost, falls die IP-Ermittlung fehlschlägt
                try {
                    URI uri = new URI("http://localhost:" + port + adminDashboardPath);
                    System.out.println("Versuche, Admin-Dashboard über localhost zu öffnen: " + uri);
                    Desktop.getDesktop().browse(uri);
                } catch (Exception fallbackE) {
                    System.err.println("Fehler beim Öffnen des Browsers mit localhost: " + fallbackE.getMessage());
                }
            }
        } else {
            System.out.println("Desktop-Umgebung wird nicht unterstützt. Bitte öffnen Sie den Browser manuell:");
            System.out.println("http://<Ihre_IP_Adresse>:" + port + adminDashboardPath);
        }
    }
}