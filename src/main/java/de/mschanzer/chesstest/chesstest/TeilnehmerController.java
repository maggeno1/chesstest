package de.mschanzer.chesstest.chesstest;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import org.springframework.transaction.annotation.Transactional; // Für Transaktionen

@RestController
@RequestMapping("/api/teilnehmer")
@CrossOrigin(origins = "*") // Erlaubt CORS für Entwicklung vom Tablet
public class TeilnehmerController {

    private final TeilnehmerRepository teilnehmerRepository;
    private final SimpMessagingTemplate messagingTemplate; // Für WebSockets

    public TeilnehmerController(TeilnehmerRepository teilnehmerRepository, SimpMessagingTemplate messagingTemplate) {
        this.teilnehmerRepository = teilnehmerRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Endpunkt zum Aktualisieren des Status eines einzelnen Teilnehmers
    @PutMapping("/{id}")
    public ResponseEntity<Teilnehmer> updateTeilnehmerStatus(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Optional<Teilnehmer> optionalTeilnehmer = teilnehmerRepository.findById(id);
        if (optionalTeilnehmer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Teilnehmer teilnehmer = optionalTeilnehmer.get();

        if (updates.containsKey("startgebuehr_bezahlt")) {
            teilnehmer.setStartgebuehrBezahlt((Boolean) updates.get("startgebuehr_bezahlt"));
        }
        if (updates.containsKey("anwesenheits_status")) {
            teilnehmer.setAnwesenheitsStatus((String) updates.get("anwesenheits_status"));
        }
        if (updates.containsKey("kommentar")) {
            teilnehmer.setKommentar((String) updates.get("kommentar"));
        }

        Teilnehmer updatedTeilnehmer = teilnehmerRepository.save(teilnehmer);

        // Senden des Updates an den Admin über WebSocket
        messagingTemplate.convertAndSend("/topic/teilnehmerUpdates", updatedTeilnehmer);

        return ResponseEntity.ok(updatedTeilnehmer);
    }

    // Endpunkt zum Aktualisieren der Startgebühr für alle Anwesenden eines Vereins
    @PutMapping("/verein/startgebuehr")
    @Transactional // Stellt sicher, dass alle Updates in einer Transaktion sind
    public ResponseEntity<String> updateVereinStartgebuehr(@RequestBody Map<String, String> request) {
        String verein = request.get("verein");
        if (verein == null || verein.isEmpty()) {
            return ResponseEntity.badRequest().body("Vereinsname fehlt.");
        }

        List<Teilnehmer> anwesendeVereinsmitglieder = teilnehmerRepository.findByVereinAndAnwesenheitsStatus(verein, "anwesend");

        if (anwesendeVereinsmitglieder.isEmpty()) {
            return ResponseEntity.ok("Keine anwesenden Spieler für Verein " + verein + " gefunden.");
        }

        for (Teilnehmer t : anwesendeVereinsmitglieder) {
            t.setStartgebuehrBezahlt(true);
            teilnehmerRepository.save(t); // Speichert jeden aktualisierten Teilnehmer
            // Senden jedes Updates einzeln an den Admin
            messagingTemplate.convertAndSend("/topic/teilnehmerUpdates", t);
        }

        return ResponseEntity.ok(String.format("Startgebühr für %d anwesende Spieler des Vereins '%s' aktualisiert.", anwesendeVereinsmitglieder.size(), verein));
    }

    // Optional: Endpunkt zum Abrufen aller Teilnehmer (für Admin-Ansicht initial)
    @GetMapping
    public Iterable<Teilnehmer> getAllTeilnehmer() {
        return teilnehmerRepository.findAll();
    }
}