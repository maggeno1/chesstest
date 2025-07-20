package de.mschanzer.chesstest.chesstest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@RestController
@RequestMapping("/api/teilnehmer")
@CrossOrigin(origins = "*")
public class TeilnehmerController {

    private final TeilnehmerRepository teilnehmerRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TokenService tokenService;
    private final TournamentService tournamentService;
    private final TournamentRoundRepository tournamentRoundRepository;

    public TeilnehmerController(TeilnehmerRepository teilnehmerRepository,
                                SimpMessagingTemplate messagingTemplate,
                                TokenService tokenService,
                                TournamentService tournamentService,
                                TournamentRoundRepository tournamentRoundRoundRepository) {
        this.teilnehmerRepository = teilnehmerRepository;
        this.messagingTemplate = messagingTemplate;
        this.tokenService = tokenService;
        this.tournamentService = tournamentService;
        this.tournamentRoundRepository = tournamentRoundRoundRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Iterable<Teilnehmer> getAllTeilnehmer() {
        return teilnehmerRepository.findAll();
    }

    // ... (andere Methoden wie createTeilnehmer, updateTeilnehmer, deleteTeilnehmer, etc. bleiben unverändert)

    /**
     * Startet ein neues Turnier.
     * POST /api/teilnehmer/tournament/start
     * @param payload Enthält "tournamentName" und "totalRounds"
     */
    @PostMapping("/tournament/start")
    public ResponseEntity<Tournament> startTournament(@RequestBody Map<String, Object> payload) {
        try {
            String tournamentName = (String) payload.get("tournamentName");
            Integer totalRounds = (Integer) payload.get("totalRounds");

            if (tournamentName == null || tournamentName.trim().isEmpty() || totalRounds == null || totalRounds <= 0) {
                return ResponseEntity.badRequest().body(null);
            }

            Tournament tournament = tournamentService.startNewTournament(tournamentName, totalRounds);
            // Sende Updates über WebSockets
            messagingTemplate.convertAndSend("/topic/tournamentUpdates", tournament);
            messagingTemplate.convertAndSend("/topic/pairingUpdates", tournament.getRounds().get(0).getPairings()); // Sendet Paarungen der ersten Runde
            messagingTemplate.convertAndSend("/topic/standingsUpdates", tournamentService.getTournamentStandings(tournament));
            return ResponseEntity.status(HttpStatus.CREATED).body(tournament);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Startet die nächste Turnierrunde und erzeugt die Paarungen.
     * POST /api/teilnehmer/tournament/start-round
     */
    @PostMapping("/tournament/start-round")
    public ResponseEntity<TournamentRound> startNextRound(@RequestBody Map<String, Long> payload) {
        try {
            Long tournamentId = payload.get("tournamentId");
            if (tournamentId == null) {
                return ResponseEntity.badRequest().body(null);
            }
            Tournament tournament = tournamentService.getCurrentTournament();
            if (tournament == null || !tournament.getId().equals(tournamentId)) {
                return ResponseEntity.notFound().build();
            }
            TournamentRound newRound = tournamentService.createAndPairNextRound(tournament);
            messagingTemplate.convertAndSend("/topic/pairingUpdates", newRound.getPairings());
            messagingTemplate.convertAndSend("/topic/tournamentUpdates", tournament); // Sende aktualisiertes Turnierobjekt
            messagingTemplate.convertAndSend("/topic/standingsUpdates", tournamentService.getTournamentStandings(tournament)); // Sende aktualisierte Stände
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
     * Meldet ein Ergebnis für eine Paarung.
     * POST /api/teilnehmer/pairing/report-result
     * @param payload Enthält "pairingId" und "result" (1.0, 0.5, 0.0)
     */
    @PostMapping("/pairing/report-result")
    public ResponseEntity<Pairing> reportResult(@RequestBody Map<String, Object> payload) {
        try {
            Long pairingId = ((Number) payload.get("pairingId")).longValue();
            Double result = (Double) payload.get("result");

            // Rufen Sie die Methode 'completePairing' auf, die ich zuvor bereitgestellt habe
            Pairing updatedPairing = tournamentService.completePairing(pairingId, result);

            // Senden Sie Updates über WebSockets
            // Die aktualisierte Paarung
            messagingTemplate.convertAndSend("/topic/pairingUpdates", updatedPairing.getRound().getPairings());
            // Aktualisierte Turnierstände (nach Ergebnis und ggf. Buchholz-Neuberechnung)
            messagingTemplate.convertAndSend("/topic/standingsUpdates", tournamentService.getTournamentStandings(updatedPairing.getRound().getTournament()));
            // Wenn die Runde abgeschlossen ist, sendet dies ein Update für das Turnier
            if (updatedPairing.getRound().isCompleted()) {
                messagingTemplate.convertAndSend("/topic/tournamentUpdates", updatedPairing.getRound().getTournament());
            }

            return ResponseEntity.ok(updatedPairing);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Ruft die aktuellen Turnierstände ab.
     * GET /api/teilnehmer/tournament/standings
     */
    @GetMapping("/tournament/standings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Teilnehmer>> getTournamentStandings() {
        Tournament currentTournament = tournamentService.getCurrentTournament();
        if (currentTournament == null) {
            return ResponseEntity.ok(Collections.emptyList()); // Keine Stände, wenn kein Turnier läuft
        }
        return ResponseEntity.ok(tournamentService.getTournamentStandings(currentTournament));
    }


    /**
     * Ruft die Paarungen der aktuellen Runde ab.
     * GET /api/teilnehmer/tournament/current-pairings
     */
    @GetMapping("/tournament/current-pairings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Pairing>> getCurrentPairings() {
        Tournament currentTournament = tournamentService.getCurrentTournament();
        if (currentTournament == null || currentTournament.getCurrentRound() == 0) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        TournamentRound currentRound = tournamentRoundRepository.findByTournamentAndRoundNumber(currentTournament, currentTournament.getCurrentRound());
        if (currentRound == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(currentRound.getPairings());
    }

    /**
     * Ruft das aktuelle Turnierobjekt ab.
     * GET /api/teilnehmer/tournament/current
     */
    @GetMapping("/tournament/current")
    @Transactional(readOnly = true)
    public ResponseEntity<Tournament> getCurrentTournament() {
        Tournament tournament = tournamentService.getCurrentTournament();
        if (tournament == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tournament);
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