package de.mschanzer.chesstest.chesstest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional; // Für Transaktionen

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        this.tokenService = tokenService; // NEU: Zuweisung
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

    // NEU: Endpunkt zum Abrufen aller einzigartigen Vereinsnamen
    @GetMapping("/vereine")
    @Transactional(readOnly = true)
    public ResponseEntity<List<String>> getDistinctVereinNames() {
        List<String> vereine = teilnehmerRepository.findDistinctVereinNames();
        return ResponseEntity.ok(vereine);
    }

    @PostMapping("/generate-token")
    public ResponseEntity<RegistrationToken> generateToken() {
        RegistrationToken newToken = tokenService.generateNewToken();
        // Optional: Senden des neuen Tokens über WebSockets, falls Admin-Dashboard es live sehen soll
        // messagingTemplate.convertAndSend("/topic/newTokens", newToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(newToken);
    }

    @GetMapping("/token-status/{tokenString}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Boolean>> getTokenStatus(@PathVariable String tokenString) {
        Optional<RegistrationToken> token = tokenService.validateToken(tokenString);
        boolean isValidAndUnused = token.isPresent();
        return ResponseEntity.ok(Map.of("isValid", isValidAndUnused));
    }

    /**
     * Aktualisiert den Status (Startgebühr, Anwesenheit, Kommentar) eines einzelnen Teilnehmers.
     * Endpunkt: PUT /api/teilnehmer/{id}
     * Kann vom Tablet-Frontend oder Admin-Dashboard verwendet werden.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Teilnehmer> updateTeilnehmerStatus(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Optional<Teilnehmer> optionalTeilnehmer = teilnehmerRepository.findById(id);

        if (optionalTeilnehmer.isEmpty()) {
            return ResponseEntity.notFound().build(); // 404 Not Found, wenn Teilnehmer nicht existiert
        }

        Teilnehmer teilnehmer = optionalTeilnehmer.get();

        // Prüfe und wende die Updates an
        // KORREKTUR: Schlüsselnamen wurden an camelCase vom Frontend angepasst
        if (updates.containsKey("startgebuehrBezahlt")) { // Von "startgebuehr_bezahlt" zu "startgebuehrBezahlt"
            teilnehmer.setStartgebuehrBezahlt((Boolean) updates.get("startgebuehrBezahlt"));
        }
        if (updates.containsKey("anwesenheitsStatus")) { // Von "anwesenheits_status" zu "anwesenheitsStatus"
            teilnehmer.setAnwesenheitsStatus((String) updates.get("anwesenheitsStatus"));
        }
        if (updates.containsKey("kommentar")) { // Dieser war bereits korrekt
            teilnehmer.setKommentar((String) updates.get("kommentar"));
        }

        Teilnehmer updatedTeilnehmer = teilnehmerRepository.save(teilnehmer);

        // Senden des Updates an alle abonnierten WebSocket-Clients (Admin-Dashboard)
        messagingTemplate.convertAndSend("/topic/teilnehmerUpdates", updatedTeilnehmer);

        return ResponseEntity.ok(updatedTeilnehmer); // 200 OK mit dem aktualisierten Teilnehmer
    }

    /**
     * Aktualisiert die Startgebühr für alle anwesenden Spieler eines bestimmten Vereins.
     * Endpunkt: PUT /api/teilnehmer/verein/startgebuehr
     * Beispiel für eine spezifische Admin-Funktion.
     */
    @PutMapping("/verein/startgebuehr")
    @Transactional // Stellt sicher, dass alle Datenbankoperationen atomar sind
    public ResponseEntity<String> updateVereinStartgebuehr(@RequestBody Map<String, Object> request) { // Map<String, String> zu Map<String, Object>
        String verein = (String) request.get("verein");
        // NEU: Den 'bezahlt'-Status aus dem Request-Body lesen
        boolean bezahlt = true; // Standardwert
        if (request.containsKey("bezahlt")) {
            Object bezahltValue = request.get("bezahlt");
            if (bezahltValue instanceof Boolean) {
                bezahlt = (Boolean) bezahltValue;
            } else if (bezahltValue instanceof String) { // Falls Frontend "true"/"false" als String sendet
                bezahlt = Boolean.parseBoolean((String) bezahltValue);
            }
        }


        if (verein == null || verein.isEmpty()) {
            return ResponseEntity.badRequest().body("Vereinsname fehlt im Request-Body.");
        }

        // Finde alle anwesenden Mitglieder dieses Vereins
        List<Teilnehmer> anwesendeVereinsmitglieder = teilnehmerRepository.findByVereinAndAnwesenheitsStatus(verein, "anwesend");

        if (anwesendeVereinsmitglieder.isEmpty()) {
            return ResponseEntity.ok(String.format("Keine anwesenden Spieler für Verein '%s' gefunden.", verein));
        }

        // Aktualisiere die Startgebühr für jedes Mitglied
        for (Teilnehmer t : anwesendeVereinsmitglieder) {
            t.setStartgebuehrBezahlt(bezahlt); // KORREKTUR: Setze Startgebühr basierend auf dem 'bezahlt'-Parameter
            teilnehmerRepository.save(t); // Speichere den aktualisierten Teilnehmer

            // Senden des Updates für jeden einzelnen aktualisierten Teilnehmer an den Admin
            // Dies stellt sicher, dass das Dashboard für jeden aktualisierten Spieler ein Update erhält.
            messagingTemplate.convertAndSend("/topic/teilnehmerUpdates", t);
        }

        return ResponseEntity.ok(String.format("Startgebühr für %d anwesende Spieler des Vereins '%s' auf '%s' aktualisiert.",
                anwesendeVereinsmitglieder.size(), verein, bezahlt ? "bezahlt" : "nicht bezahlt"));
    }

    /**
     * Fügt einen neuen Teilnehmer zur Datenbank hinzu und validiert einen Token.
     * Endpunkt: POST /api/teilnehmer
     * Wird vom Formular auf add_teilnehmer.html verwendet.
     * Erfordert jetzt einen gültigen, unbenutzten Token.
     */
    @PostMapping
    public ResponseEntity<Teilnehmer> addTeilnehmer(@RequestBody Teilnehmer newTeilnehmer,
                                                    @RequestParam(value = "token", required = false) String tokenString) { // NEU: Token als RequestParam

        // NEU: Token-Validierung
        if (tokenString == null || tokenString.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // Token fehlt
        }

        Optional<RegistrationToken> optionalToken = tokenService.validateToken(tokenString);
        if (optionalToken.isEmpty()) {
            // Token ist ungültig oder bereits verwendet
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null); // Oder HttpStatus.FORBIDDEN
        }

        // Token ist gültig und unbenutzt, fahre mit der Teilnehmererstellung fort
        RegistrationToken validToken = optionalToken.get();

        // Sicherstellen, dass keine ID vom Client gesendet wird oder sie auf null setzen,
        // damit die Datenbank eine neue ID generiert.
        if (newTeilnehmer.getId() != null) {
            newTeilnehmer.setId(null);
        }

        // Setzen von Standardwerten, falls diese vom Frontend nicht explizit gesetzt werden
        if (newTeilnehmer.getAnwesenheitsStatus() == null || newTeilnehmer.getAnwesenheitsStatus().isEmpty()) {
            newTeilnehmer.setAnwesenheitsStatus("nicht anwesend");
        }
        if (newTeilnehmer.getKommentar() == null) {
            newTeilnehmer.setKommentar("");
        }

        // Grundlegende Validierung der erforderlichen Felder
        if (newTeilnehmer.getVerein() == null || newTeilnehmer.getVerein().trim().isEmpty() ||
                newTeilnehmer.getName() == null || newTeilnehmer.getName().trim().isEmpty() ||
                newTeilnehmer.getAltersklasse() == null || newTeilnehmer.getAltersklasse().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        Teilnehmer savedTeilnehmer = teilnehmerRepository.save(newTeilnehmer);

        // NEU: Token nach erfolgreicher Verwendung deaktivieren
        tokenService.invalidateToken(validToken);

        // Debug-Ausgabe hinzufügen:
        System.out.println("DEBUG: Sende WebSocket-Nachricht für verwendeten Token: " + validToken.getToken());

        // Optional: Senden des deaktivierten Tokens über WebSockets, falls Admin-Dashboard es live sehen soll
        messagingTemplate.convertAndSend("/topic/tokenUsed", validToken.getToken()); // Sende nur den Token-String

        messagingTemplate.convertAndSend("/topic/teilnehmerUpdates", savedTeilnehmer);
        return ResponseEntity.status(201).body(savedTeilnehmer);
    }


}