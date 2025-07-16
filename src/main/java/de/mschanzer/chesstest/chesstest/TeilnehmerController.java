package de.mschanzer.chesstest.chesstest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/teilnehmer")
@CrossOrigin(origins = "*")
public class TeilnehmerController {

    private final TeilnehmerRepository teilnehmerRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TokenService tokenService;
    private final TournamentService tournamentService;
    private final TournamentRoundRepository tournamentRoundRepository; // Deklaration ist korrekt

    public TeilnehmerController(TeilnehmerRepository teilnehmerRepository,
                                SimpMessagingTemplate messagingTemplate,
                                TokenService tokenService,
                                TournamentService tournamentService,
                                TournamentRoundRepository tournamentRoundRepository) { // HIER HINZUGEFÜGT
        this.teilnehmerRepository = teilnehmerRepository;
        this.messagingTemplate = messagingTemplate;
        this.tokenService = tokenService;
        this.tournamentService = tournamentService;
        this.tournamentRoundRepository = tournamentRoundRepository; // HIER HINZUGEFÜGT
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Iterable<Teilnehmer> getAllTeilnehmer() {
        return teilnehmerRepository.findAll();
    }

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

    @MessageMapping("/tokenScanned")
    public void handleTokenScanned(String tokenString) {
        System.out.println("Token gescannt: " + tokenString);
        messagingTemplate.convertAndSend("/topic/tokenScannedStatus", tokenString);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteTeilnehmer(@PathVariable Long id) {
        if (!teilnehmerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        teilnehmerRepository.deleteById(id);
        messagingTemplate.convertAndSend("/topic/teilnehmerDeleted", id);
        return ResponseEntity.noContent().build();
    }

    // --- NEUE ENDPUNKTE FÜR TURNIERVERWALTUNG ---

    /**
     * Startet ein neues Turnier.
     * POST /api/teilnehmer/tournament/start
     * Request Body: { "name": "Mein Turnier", "totalRounds": 5 }
     */
    @PostMapping("/tournament/start")
    public ResponseEntity<Tournament> startTournament(@RequestBody Map<String, Object> request) {
        String tournamentName = (String) request.get("name");
        Integer totalRounds = (Integer) request.get("totalRounds");

        if (tournamentName == null || tournamentName.trim().isEmpty() || totalRounds == null || totalRounds <= 0) {
            return ResponseEntity.badRequest().body(null);
        }
        try {
            Tournament tournament = tournamentService.startNewTournament(tournamentName, totalRounds);
            // Sende WebSocket-Update, dass ein Turnier gestartet wurde
            messagingTemplate.convertAndSend("/topic/tournamentUpdates", tournament);
            messagingTemplate.convertAndSend("/topic/pairingUpdates", tournament.getRounds().get(0).getPairings()); // Sende erste Paarungen
            messagingTemplate.convertAndSend("/topic/standingsUpdates", tournamentService.getTournamentStandings(tournament)); // Sende erste Standings
            return ResponseEntity.status(HttpStatus.CREATED).body(tournament);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null); // Z.B. Turnier läuft bereits
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Wechselt zur nächsten Runde (erstellt Paarungen für die nächste Runde).
     * POST /api/teilnehmer/tournament/next-round/{tournamentId}
     */
    @PostMapping("/tournament/next-round/{tournamentId}")
    public ResponseEntity<TournamentRound> nextRound(@PathVariable Long tournamentId) {
        try {
            Tournament tournament = tournamentService.getCurrentTournament(); // Annahme: nur ein Turnier läuft
            if (tournament == null || !tournament.getId().equals(tournamentId)) {
                return ResponseEntity.notFound().build();
            }
            TournamentRound newRound = tournamentService.createAndPairNextRound(tournament);
            // Sende WebSocket-Update über die neue Runde und Paarungen
            messagingTemplate.convertAndSend("/topic/tournamentUpdates", tournament);
            messagingTemplate.convertAndSend("/topic/pairingUpdates", newRound.getPairings());
            messagingTemplate.convertAndSend("/topic/standingsUpdates", tournamentService.getTournamentStandings(tournament));
            return ResponseEntity.ok(newRound);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Meldet ein Spielergebnis.
     * PUT /api/teilnehmer/tournament/report-result/{pairingId}
     * Request Body: { "result": 1.0 }
     */
    @PutMapping("/tournament/report-result/{pairingId}")
    public ResponseEntity<Pairing> reportResult(@PathVariable Long pairingId, @RequestBody Map<String, Double> request) {
        Double result = request.get("result");
        if (result == null) {
            return ResponseEntity.badRequest().body(null);
        }
        try {
            Pairing updatedPairing = tournamentService.reportResult(pairingId, result);
            // Sende WebSocket-Update über das aktualisierte Pairing
            messagingTemplate.convertAndSend("/topic/pairingUpdated", updatedPairing);
            // Sende aktualisierte Rangliste (Punkte ändern sich)
            Tournament tournament = tournamentService.getCurrentTournament();
            if (tournament != null) {
                messagingTemplate.convertAndSend("/topic/standingsUpdates", tournamentService.getTournamentStandings(tournament));
                if (updatedPairing.getRound().isCompleted()) { // Wenn Runde abgeschlossen, sende Tournament Update
                    messagingTemplate.convertAndSend("/topic/tournamentUpdates", tournament);
                }
            }
            return ResponseEntity.ok(updatedPairing);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Ruft den aktuellen Turnierstatus ab.
     * GET /api/teilnehmer/tournament/current
     */
    @GetMapping("/tournament/current")
    public ResponseEntity<Tournament> getCurrentTournament() {
        Tournament tournament = tournamentService.getCurrentTournament();
        if (tournament == null) {
            return ResponseEntity.noContent().build(); // 204 No Content, wenn kein Turnier läuft
        }
        return ResponseEntity.ok(tournament);
    }

    /**
     * Ruft die Paarungen einer bestimmten Runde ab.
     * GET /api/teilnehmer/tournament/{tournamentId}/round/{roundNumber}/pairings
     */
    @GetMapping("/tournament/{tournamentId}/round/{roundNumber}/pairings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Pairing>> getPairingsForRound(@PathVariable Long tournamentId, @PathVariable int roundNumber) {
        Tournament tournament = tournamentService.getCurrentTournament();
        if (tournament == null || !tournament.getId().equals(tournamentId)) {
            return ResponseEntity.notFound().build();
        }
        TournamentRound round = tournamentRoundRepository.findByTournamentAndRoundNumber(tournament, roundNumber);
        if (round == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(round.getPairings());
    }

    /**
     * Ruft die aktuelle Rangliste ab.
     * GET /api/teilnehmer/tournament/standings
     */
    @GetMapping("/tournament/standings")
    public ResponseEntity<List<Teilnehmer>> getStandings() {
        Tournament tournament = tournamentService.getCurrentTournament();
        if (tournament == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(tournamentService.getTournamentStandings(tournament));
    }

    /**
     * Beendet das aktuelle Turnier.
     * POST /api/teilnehmer/tournament/end/{tournamentId}
     */
    @PostMapping("/tournament/end/{tournamentId}")
    public ResponseEntity<Tournament> endTournament(@PathVariable Long tournamentId) {
        try {
            Tournament tournament = tournamentService.endTournament(tournamentId);
            messagingTemplate.convertAndSend("/topic/tournamentUpdates", tournament); // Sende als beendet
            messagingTemplate.convertAndSend("/topic/standingsUpdates", tournamentService.getTournamentStandings(tournament)); // Letzte Standings
            return ResponseEntity.ok(tournament);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Setzt alle Turnierdaten und Turnierstände der Teilnehmer zurück.
     * POST /api/teilnehmer/tournament/reset-all
     */
    @PostMapping("/tournament/reset-all")
    public ResponseEntity<Void> resetAllTournamentData() {
        tournamentService.resetAllTournamentData();
        // Sende ein leeres Update, um die UIs zu leeren/zurückzusetzen
        messagingTemplate.convertAndSend("/topic/tournamentUpdates", List.of());
        messagingTemplate.convertAndSend("/topic/pairingUpdates", List.of());
        messagingTemplate.convertAndSend("/topic/standingsUpdates", List.of());
        return ResponseEntity.noContent().build();
    }
}