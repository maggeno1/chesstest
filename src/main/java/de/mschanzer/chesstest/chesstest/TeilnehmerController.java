package de.mschanzer.chesstest.chesstest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping; // Wichtig: Neue Annotation
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
@RequestMapping("/api/teilnehmer")
@CrossOrigin(origins = "*") // Erlaubt CORS für Entwicklung vom Tablet/anderen Ursprüngen
public class TeilnehmerController {

    private final TeilnehmerRepository teilnehmerRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TokenService tokenService;

    public TeilnehmerController(TeilnehmerRepository teilnehmerRepository, SimpMessagingTemplate messagingTemplate, TokenService tokenService) {
        this.teilnehmerRepository = teilnehmerRepository;
        this.messagingTemplate = messagingTemplate;
        this.tokenService = tokenService;
    }

    /**
     * Ruft alle registrierten Teilnehmer ab.
     * Endpunkt: GET /api/teilnehmer
     * Für die initiale Anzeige im Admin-Dashboard.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public Iterable<Teilnehmer> getAllTeilnehmer() {
        return teilnehmerRepository.findAll();
    }

    // Endpunkt zum Abrufen aller einzigartigen Vereinsnamen
    @GetMapping("/vereine")
    @Transactional(readOnly = true)
    public ResponseEntity<List<String>> getDistinctVereinNames() {
        List<String> vereine = teilnehmerRepository.findDistinctVereinNames();
        return ResponseEntity.ok(vereine);
    }

    @PostMapping("/generate-token")
    public ResponseEntity<RegistrationToken> generateToken() {
        RegistrationToken newToken = tokenService.generateNewToken();
        return ResponseEntity.status(HttpStatus.CREATED).body(newToken);
    }

    @GetMapping("/token-status/{tokenString}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Boolean>> getTokenStatus(@PathVariable String tokenString) {
        Optional<RegistrationToken> token = tokenService.validateToken(tokenString);
        boolean isValidAndUnused = token.isPresent();
        return ResponseEntity.ok(Map.of("isValid", isValidAndUnused));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Teilnehmer> updateTeilnehmer(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Optional<Teilnehmer> optionalTeilnehmer = teilnehmerRepository.findById(id);

        if (optionalTeilnehmer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Teilnehmer teilnehmer = optionalTeilnehmer.get();

        if (updates.containsKey("startgebuehrBezahlt")) {
            teilnehmer.setStartgebuehrBezahlt((Boolean) updates.get("startgebuehrBezahlt"));
        }
        if (updates.containsKey("startgebuehrMussNichtZahlen")) {
            teilnehmer.setStartgebuehrMussNichtZahlen((Boolean) updates.get("startgebuehrMussNichtZahlen"));
        }
        if (updates.containsKey("anwesenheitsStatus")) {
            teilnehmer.setAnwesenheitsStatus((String) updates.get("anwesenheitsStatus"));
        }
        if (updates.containsKey("kommentar")) {
            teilnehmer.setKommentar((String) updates.get("kommentar"));
        }

        Teilnehmer updatedTeilnehmer = teilnehmerRepository.save(teilnehmer);
        messagingTemplate.convertAndSend("/topic/teilnehmerUpdates", updatedTeilnehmer);

        return ResponseEntity.ok(updatedTeilnehmer);
    }

    @PutMapping("/verein/startgebuehr")
    @Transactional
    public ResponseEntity<String> updateVereinStartgebuehr(@RequestBody Map<String, Object> request) {
        String verein = (String) request.get("verein");
        boolean bezahlt = true;
        if (request.containsKey("bezahlt")) {
            Object bezahltValue = request.get("bezahlt");
            if (bezahltValue instanceof Boolean) {
                bezahlt = (Boolean) bezahltValue;
            } else if (bezahltValue instanceof String) {
                bezahlt = Boolean.parseBoolean((String) bezahltValue);
            }
        }

        if (verein == null || verein.isEmpty()) {
            return ResponseEntity.badRequest().body("Vereinsname fehlt im Request-Body.");
        }

        List<Teilnehmer> anwesendeVereinsmitglieder = teilnehmerRepository.findByVereinAndAnwesenheitsStatus(verein, "anwesend");

        if (anwesendeVereinsmitglieder.isEmpty()) {
            return ResponseEntity.ok(String.format("Keine anwesenden Spieler für Verein '%s' gefunden.", verein));
        }

        for (Teilnehmer t : anwesendeVereinsmitglieder) {
            t.setStartgebuehrBezahlt(bezahlt);
            t.setStartgebuehrMussNichtZahlen(false);
            teilnehmerRepository.save(t);
            messagingTemplate.convertAndSend("/topic/teilnehmerUpdates", t);
        }

        return ResponseEntity.ok(String.format("Startgebühr für %d anwesende Spieler des Vereins '%s' auf '%s' aktualisiert.",
                anwesendeVereinsmitglieder.size(), verein, bezahlt ? "bezahlt" : "nicht bezahlt"));
    }

    @PostMapping
    public ResponseEntity<Teilnehmer> addTeilnehmer(@RequestBody Teilnehmer newTeilnehmer,
                                                    @RequestParam(value = "token", required = false) String tokenString) {
        if (tokenString == null || tokenString.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        Optional<RegistrationToken> optionalToken = tokenService.validateToken(tokenString);
        if (optionalToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        RegistrationToken validToken = optionalToken.get();

        if (newTeilnehmer.getId() != null) {
            newTeilnehmer.setId(null);
        }

        if (newTeilnehmer.getAnwesenheitsStatus() == null || newTeilnehmer.getAnwesenheitsStatus().isEmpty()) {
            newTeilnehmer.setAnwesenheitsStatus("nicht anwesend");
        }
        if (newTeilnehmer.getKommentar() == null) {
            newTeilnehmer.setKommentar("");
        }
        if (!newTeilnehmer.isStartgebuehrBezahlt() && !newTeilnehmer.isStartgebuehrMussNichtZahlen()) {
            newTeilnehmer.setStartgebuehrBezahlt(false);
            newTeilnehmer.setStartgebuehrMussNichtZahlen(false);
        }

        if (newTeilnehmer.getVerein() == null || newTeilnehmer.getVerein().trim().isEmpty() ||
                newTeilnehmer.getName() == null || newTeilnehmer.getName().trim().isEmpty() ||
                newTeilnehmer.getAltersklasse() == null || newTeilnehmer.getAltersklasse().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        Teilnehmer savedTeilnehmer = teilnehmerRepository.save(newTeilnehmer);
        tokenService.invalidateToken(validToken);

        messagingTemplate.convertAndSend("/topic/tokenUsed", validToken.getToken());
        messagingTemplate.convertAndSend("/topic/teilnehmerUpdates", savedTeilnehmer);
        return ResponseEntity.status(201).body(savedTeilnehmer);
    }

    @GetMapping("/server-ip")
    public ResponseEntity<String> getServerIpAddress() {
        try {
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            return ResponseEntity.ok(ipAddress);
        } catch (UnknownHostException e) {
            System.err.println("Fehler beim Ermitteln der Server-IP: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not determine server IP address.");
        }
    }

    /**
     * NEU: Empfängt die Nachricht, dass ein Token gescannt wurde.
     * Sendet dies an ein öffentliches WebSocket-Topic, damit Admins darauf reagieren können.
     * Die URL für diesen Endpunkt ist: /app/tokenScanned (aus dem Frontend kommend)
     * Und wird an /topic/tokenScannedStatus (für alle Abonnenten) weitergeleitet.
     */
    @MessageMapping("/tokenScanned") // Dies ist der Zielpfad von `stompClient.send()` im Frontend
    public void handleTokenScanned(String tokenString) {
        System.out.println("Token gescannt: " + tokenString);
        // Sende die Info an alle Clients, die /topic/tokenScannedStatus abonnieren
        messagingTemplate.convertAndSend("/topic/tokenScannedStatus", tokenString);
    }
}