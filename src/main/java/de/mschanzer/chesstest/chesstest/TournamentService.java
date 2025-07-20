package de.mschanzer.chesstest.chesstest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRoundRepository tournamentRoundRepository;
    private final PairingRepository pairingRepository;
    private final TeilnehmerRepository teilnehmerRepository;

    public TournamentService(TournamentRepository tournamentRepository, TournamentRoundRepository tournamentRoundRepository,
                             PairingRepository pairingRepository, TeilnehmerRepository teilnehmerRepository) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentRoundRepository = tournamentRoundRepository;
        this.pairingRepository = pairingRepository;
        this.teilnehmerRepository = teilnehmerRepository;
    }

    /**
     * Startet ein neues Turnier mit den aktuell anwesenden Teilnehmern.
     * @param tournamentName Name des Turniers
     * @param totalRounds Gesamtzahl der Runden
     * @return Das gestartete Turnier.
     * @throws IllegalStateException wenn bereits ein Turnier läuft oder keine anwesenden Teilnehmer vorhanden sind.
     */
    @Transactional
    public Tournament startNewTournament(String tournamentName, int totalRounds) {
        if (tournamentRepository.findByFinished(false) != null) {
            throw new IllegalStateException("Es läuft bereits ein Turnier. Bitte beenden Sie das aktuelle Turnier zuerst.");
        }

        List<Teilnehmer> activeParticipants = teilnehmerRepository.findByAnwesenheitsStatus("anwesend");
        if (activeParticipants.isEmpty()) {
            throw new IllegalStateException("Keine anwesenden Teilnehmer gefunden, um ein Turnier zu starten.");
        }

        // Setze Turnierpunkte und Buchholz für alle aktiven Teilnehmer zurück
        activeParticipants.forEach(t -> {
            t.setTournamentPoints(0.0);
            t.setBuchholzScore(0.0);
            teilnehmerRepository.save(t);
        });

        Tournament tournament = new Tournament(tournamentName, totalRounds);
        tournament = tournamentRepository.save(tournament);

        // Erstelle die erste Runde und ihre Paarungen
        createAndPairNextRound(tournament);

        return tournament;
    }

    /**
     * Schließt eine Paarung ab und aktualisiert die Turnierpunkte der Spieler.
     * @param pairingId Die ID der Paarung.
     * @param result Das Ergebnis der Partie (1.0 für Weiß gewinnt, 0.5 für Remis, 0.0 für Schwarz gewinnt).
     * @return Die aktualisierte Paarung.
     * @throws NoSuchElementException wenn die Paarung nicht gefunden wird.
     * @throws IllegalStateException wenn das Ergebnis ungültig ist oder die Runde bereits abgeschlossen ist.
     */
    @Transactional
    public Pairing completePairing(Long pairingId, Double result) {
        Pairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new NoSuchElementException("Paarung nicht gefunden mit ID: " + pairingId));

        if (pairing.getResult() != null) {
            throw new IllegalStateException("Diese Paarung wurde bereits abgeschlossen.");
        }
        if (result == null || (result != 0.0 && result != 0.5 && result != 1.0)) {
            throw new IllegalStateException("Ungültiges Ergebnis. Erlaubte Werte: 0.0, 0.5, 1.0");
        }

        pairing.setResult(result);
        pairing = pairingRepository.save(pairing);

        // Aktualisiere Turnierpunkte
        Teilnehmer whitePlayer = pairing.getWhitePlayer();
        Teilnehmer blackPlayer = pairing.getBlackPlayer();

        if (!pairing.isBye()) {
            if (result == 1.0) { // Weiß gewinnt
                whitePlayer.setTournamentPoints(whitePlayer.getTournamentPoints() + 1.0);
            } else if (result == 0.5) { // Remis
                whitePlayer.setTournamentPoints(whitePlayer.getTournamentPoints() + 0.5);
                blackPlayer.setTournamentPoints(blackPlayer.getTournamentPoints() + 0.5);
            } else { // Schwarz gewinnt (0.0)
                blackPlayer.setTournamentPoints(blackPlayer.getTournamentPoints() + 1.0);
            }
        } else {
            // Bei Freilos erhält der Spieler 1 Punkt (wird schon im Konstruktor gesetzt)
            // Es wird nur sichergestellt, dass der Punkt korrekt verbucht wird
            whitePlayer.setTournamentPoints(whitePlayer.getTournamentPoints() + 1.0);
        }

        teilnehmerRepository.save(whitePlayer);
        if (blackPlayer != null) { // BlackPlayer kann bei Freilos null sein
            teilnehmerRepository.save(blackPlayer);
        }

        // Überprüfe, ob die Runde abgeschlossen ist
        TournamentRound currentRound = pairing.getRound();
        boolean allPairingsCompleted = currentRound.getPairings().stream()
                .allMatch(p -> p.getResult() != null);

        if (allPairingsCompleted) {
            currentRound.setCompleted(true);
            tournamentRoundRepository.save(currentRound);
            // Buchholz-Wertung für alle Teilnehmer neu berechnen, da sich die Gegnerpunkte geändert haben könnten
            recalculateBuchholz(currentRound.getTournament());
        }

        return pairing;
    }

    /**
     * Beendet das aktuelle Turnier.
     * @param tournamentId ID des zu beendenden Turniers.
     * @return Das beendete Turnier.
     * @throws NoSuchElementException wenn das Turnier nicht gefunden wird.
     * @throws IllegalStateException wenn das Turnier bereits beendet ist.
     */
    @Transactional
    public Tournament endTournament(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new NoSuchElementException("Turnier nicht gefunden mit ID: " + tournamentId));

        if (tournament.isFinished()) {
            throw new IllegalStateException("Turnier ist bereits beendet.");
        }

        tournament.setEndTime(LocalDateTime.now());
        tournament.setFinished(true);
        return tournamentRepository.save(tournament);
    }

    /**
     * Gibt das aktuell laufende (nicht beendete) Turnier zurück.
     * @return Das aktuelle Turnier oder null, wenn keines läuft.
     */
    @Transactional(readOnly = true)
    public Tournament getCurrentTournament() {
        return tournamentRepository.findByFinished(false);
    }

    /**
     * Liefert die Teilnehmer mit den aktuellen Turnierpunkten und Buchholz-Wertung.
     */
    @Transactional(readOnly = true)
    public List<Teilnehmer> getTournamentStandings(Tournament tournament) {
        // Buchholz kann sich ändern, wenn Ergebnisse eintrudeln, daher hier nochmal berechnen oder zumindest sortieren
        recalculateBuchholz(tournament); // Sicherstellen, dass Buchholz aktuell ist
        List<Teilnehmer> standings = teilnehmerRepository.findByAnwesenheitsStatus("anwesend");
        standings.sort(Comparator
                .comparing(Teilnehmer::getTournamentPoints, Comparator.reverseOrder())
                .thenComparing(Teilnehmer::getBuchholzScore, Comparator.reverseOrder())
                .thenComparing(Teilnehmer::getName));
        return standings;
    }

    /**
     * Setzt die Turnierinformationen aller Teilnehmer zurück.
     * Nützlich nach Turnierende oder vor dem Start eines neuen Turniers.
     */
    @Transactional
    public void resetAllTournamentData() {
        teilnehmerRepository.findAll().forEach(t -> {
            t.setTournamentPoints(0.0);
            t.setBuchholzScore(0.0);
            teilnehmerRepository.save(t);
        });
        // Alle Runden und Paarungen löschen
        pairingRepository.deleteAll();
        tournamentRoundRepository.deleteAll();
        // Alle Turniere löschen
        tournamentRepository.findAll().forEach(tournamentRepository::delete);
    }

    /**
     * Erstellt die nächste Runde für das gegebene Turnier und paart die Teilnehmer.
     * Implementiert hier eine einfache Schweizer System Paarung.
     * @param tournament Das Turnier, für das die nächste Runde erstellt werden soll.
     * @return Die neu erstellte Turnierrunde mit Paarungen.
     * @throws IllegalStateException wenn das Turnier nicht existiert, beendet ist oder die maximale Rundenzahl erreicht ist.
     */
    @Transactional
    public TournamentRound createAndPairNextRound(Tournament tournament) {
        if (tournament == null) {
            throw new IllegalStateException("Turnier darf nicht null sein.");
        }
        if (tournament.isFinished()) {
            throw new IllegalStateException("Das Turnier ist bereits beendet.");
        }

        int nextRoundNumber = tournament.getCurrentRound() + 1;
        if (nextRoundNumber > tournament.getTotalRounds()) {
            throw new IllegalStateException("Maximale Rundenzahl für dieses Turnier erreicht.");
        }

        // Sicherstellen, dass die vorherige Runde abgeschlossen ist (außer für Runde 1)
        if (tournament.getCurrentRound() > 0) {
            TournamentRound previousRound = tournamentRoundRepository.findByTournamentAndRoundNumber(tournament, tournament.getCurrentRound());
            if (previousRound != null && !previousRound.isCompleted()) {
                throw new IllegalStateException("Die aktuelle Runde " + tournament.getCurrentRound() + " ist noch nicht abgeschlossen. Bitte alle Ergebnisse eintragen.");
            }
        }


        TournamentRound newRound = new TournamentRound(nextRoundNumber, tournament);
        newRound = tournamentRoundRepository.save(newRound);

        // Teilnehmer nach Punkten sortieren (höchste Punktzahl zuerst)
        List<Teilnehmer> participants = teilnehmerRepository.findByAnwesenheitsStatus("anwesend");
        participants.sort(Comparator.comparing(Teilnehmer::getTournamentPoints, Comparator.reverseOrder()));

        List<Pairing> pairings = new ArrayList<>();
        Set<Teilnehmer> pairedPlayers = new HashSet<>();

        // Handle ungerade Anzahl von Spielern (Freilos)
        if (participants.size() % 2 != 0) {
            Teilnehmer playerForBye = findPlayerForBye(participants, tournament);
            if (playerForBye != null) {
                Pairing byePairing = new Pairing(playerForBye, newRound);
                pairings.add(byePairing);
                pairedPlayers.add(playerForBye);
                System.out.println("Freilos für: " + playerForBye.getName() + " in Runde " + nextRoundNumber);
            } else {
                // Dies sollte nicht passieren, wenn die Logik korrekt ist
                throw new IllegalStateException("Konnte keinen Spieler für ein Freilos finden.");
            }
        }

        // Einfache Schweizer System Paarung (vereinfacht)
        List<Teilnehmer> availablePlayers = participants.stream()
                .filter(p -> !pairedPlayers.contains(p))
                .collect(Collectors.toList());

        while (!availablePlayers.isEmpty()) {
            Teilnehmer player1 = availablePlayers.remove(0); // Immer den punkthöchsten verfügbaren Spieler nehmen
            Teilnehmer player2 = null;

            // Suche nach einem passenden Gegner
            for (int i = 0; i < availablePlayers.size(); i++) {
                Teilnehmer potentialOpponent = availablePlayers.get(i);
                // Überprüfe, ob die Spieler noch nicht gegeneinander gespielt haben
                if (!havePlayedBefore(player1, potentialOpponent, tournament)) {
                    player2 = potentialOpponent;
                    break;
                }
            }

            if (player2 != null) {
                availablePlayers.remove(player2);
                Pairing newPairing = new Pairing(player1, player2, newRound);
                pairings.add(newPairing);
                System.out.println("Paarung: " + player1.getName() + " (weiß) vs. " + player2.getName() + " (schwarz)");
            } else {
                // Fallback: Wenn kein idealer Gegner gefunden wird, paare mit dem nächsten verfügbaren
                // Dies kann zu Wiederholungen führen, wenn die `havePlayedBefore`-Logik zu restriktiv ist
                // Für ein robustes Schweizer System ist hier eine komplexere Logik erforderlich (z.B. Brackets)
                if (!availablePlayers.isEmpty()) {
                    player2 = availablePlayers.remove(0);
                    Pairing newPairing = new Pairing(player1, player2, newRound);
                    pairings.add(newPairing);
                    System.out.println("Fallback Paarung (potenziell Wiederholung): " + player1.getName() + " (weiß) vs. " + player2.getName() + " (schwarz)");
                } else {
                    // Sollte nicht passieren, wenn die Anzahl der Spieler gerade ist (nach Freilos)
                    System.err.println("Fehler: Konnte keinen Gegner für " + player1.getName() + " finden.");
                }
            }
        }

        pairingRepository.saveAll(pairings);
        newRound.setPairings(pairings); // Aktualisiere die Paarungen in der Runde

        tournament.setCurrentRound(nextRoundNumber);
        tournament.getRounds().add(newRound); // Füge die neue Runde zur Turnierliste hinzu
        tournamentRepository.save(tournament);

        return newRound;
    }

    /**
     * Hilfsmethode, um den Spieler für ein Freilos zu finden.
     * Ein Spieler, der bereits ein Freilos hatte, sollte nach Möglichkeit keines mehr bekommen.
     * Andernfalls der Spieler mit den wenigsten Punkten.
     * @param participants Die Liste der aktiven Teilnehmer.
     * @param tournament Das aktuelle Turnier.
     * @return Der Spieler, der das Freilos erhält.
     */
    private Teilnehmer findPlayerForBye(List<Teilnehmer> participants, Tournament tournament) {
        // Spieler finden, die noch kein Freilos hatten
        List<Teilnehmer> playersWithoutBye = participants.stream()
                .filter(p -> tournamentRoundRepository.findByTournamentOrderByRoundNumberAsc(tournament).stream()
                        .flatMap(r -> r.getPairings().stream())
                        .noneMatch(pa -> pa.isBye() && pa.getWhitePlayer().equals(p)))
                .collect(Collectors.toList());

        if (!playersWithoutBye.isEmpty()) {
            // Gib dem Spieler mit den wenigsten Punkten, der noch kein Freilos hatte, das Freilos
            return playersWithoutBye.stream()
                    .min(Comparator.comparing(Teilnehmer::getTournamentPoints))
                    .orElse(null);
        } else {
            // Wenn alle Spieler schon ein Freilos hatten, gib es dem Spieler mit den wenigsten Punkten
            return participants.stream()
                    .min(Comparator.comparing(Teilnehmer::getTournamentPoints))
                    .orElse(null);
        }
    }


    /**
     * Überprüft, ob zwei Spieler in diesem Turnier bereits gegeneinander gespielt haben.
     * @param player1 Spieler 1
     * @param player2 Spieler 2
     * @param tournament Das Turnier
     * @return true, wenn sie bereits gegeneinander gespielt haben, sonst false.
     */
    private boolean havePlayedBefore(Teilnehmer player1, Teilnehmer player2, Tournament tournament) {
        // Holen Sie alle Runden des Turniers
        List<TournamentRound> rounds = tournamentRoundRepository.findByTournamentOrderByRoundNumberAsc(tournament);

        for (TournamentRound round : rounds) {
            // Durchsuchen Sie die Paarungen jeder Runde
            for (Pairing pairing : round.getPairings()) {
                // Überprüfen Sie, ob Spieler 1 und Spieler 2 in dieser Paarung waren
                if ((pairing.getWhitePlayer().equals(player1) && pairing.getBlackPlayer() != null && pairing.getBlackPlayer().equals(player2)) ||
                        (pairing.getWhitePlayer().equals(player2) && pairing.getBlackPlayer() != null && pairing.getBlackPlayer().equals(player1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Berechnet die Buchholz-Wertung für alle Teilnehmer eines Turniers neu.
     * Die Buchholz-Wertung ist die Summe der Turnierpunkte aller Gegner.
     * Bei einem Freilos zählt der Gegnerpunkt des "virtuellen" Gegners (Standard: 0.5 Punkte für den Spieler).
     * @param tournament Das Turnier, für das die Buchholz-Wertung berechnet werden soll.
     */
    @Transactional
    public void recalculateBuchholz(Tournament tournament) {
        List<Teilnehmer> participants = teilnehmerRepository.findByAnwesenheitsStatus("anwesend");
        Map<Long, Teilnehmer> participantMap = participants.stream()
                .collect(Collectors.toMap(Teilnehmer::getId, p -> p));

        // Initialisiere Buchholz-Werte auf 0
        participants.forEach(p -> p.setBuchholzScore(0.0));

        // Sammle alle Paarungen aus allen Runden des Turniers
        List<Pairing> allPairings = new ArrayList<>();
        List<TournamentRound> rounds = tournamentRoundRepository.findByTournamentOrderByRoundNumberAsc(tournament);
        for (TournamentRound round : rounds) {
            allPairings.addAll(round.getPairings());
        }

        // Berechne Buchholz für jeden Teilnehmer
        for (Teilnehmer participant : participants) {
            double buchholzScore = 0.0;
            // Finde alle Paarungen, an denen dieser Teilnehmer beteiligt war
            List<Pairing> participantPairings = allPairings.stream()
                    .filter(p -> p.getWhitePlayer().equals(participant) || (p.getBlackPlayer() != null && p.getBlackPlayer().equals(participant)))
                    .collect(Collectors.toList());

            for (Pairing pairing : participantPairings) {
                if (pairing.isBye()) {
                    // Bei einem Freilos zählt der "virtuelle Gegner" 0.5 Punkte
                    buchholzScore += 0.5; // Oder je nach Regelwerk, manchmal ist es auch der Durchschnitt der Gegnerpunkte
                } else {
                    Teilnehmer opponent = null;
                    if (pairing.getWhitePlayer().equals(participant)) {
                        opponent = pairing.getBlackPlayer();
                    } else {
                        opponent = pairing.getWhitePlayer();
                    }
                    if (opponent != null && opponent.getTournamentPoints() != null) {
                        buchholzScore += opponent.getTournamentPoints();
                    }
                }
            }
            participant.setBuchholzScore(buchholzScore);
        }
        teilnehmerRepository.saveAll(participants);
    }

    // Weitere Methoden wie `getTournamentStandings`, `resetAllTournamentData` bleiben unverändert
}