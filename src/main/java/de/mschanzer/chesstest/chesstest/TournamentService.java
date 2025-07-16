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
            throw new IllegalStateException("Es läuft bereits ein Turnier. Bitte beenden Sie es zuerst.");
        }

        List<Teilnehmer> activePlayers = teilnehmerRepository.findByAnwesenheitsStatus("anwesend");
        if (activePlayers.isEmpty()) {
            throw new IllegalStateException("Keine anwesenden Teilnehmer gefunden, um ein Turnier zu starten.");
        }

        Tournament tournament = new Tournament(tournamentName, totalRounds);
        tournamentRepository.save(tournament);

        // Setze Punkte und Buchholz-Werte für alle Teilnehmer auf 0 zurück
        for(Teilnehmer player : activePlayers) {
            player.setTournamentPoints(0.0);
            player.setBuchholzScore(0.0);
            teilnehmerRepository.save(player);
        }

        // Start der ersten Runde
        createAndPairNextRound(tournament);

        return tournament;
    }

    /**
     * Erstellt die Paarungen für die nächste Runde nach dem Schweizer System.
     * @param tournament Das aktuelle Turnier.
     * @return Die neu erstellte Runde.
     * @throws IllegalStateException wenn das Turnier beendet ist oder die aktuelle Runde nicht abgeschlossen ist.
     */
    @Transactional
    public TournamentRound createAndPairNextRound(Tournament tournament) {
        if (tournament.isFinished()) {
            throw new IllegalStateException("Das Turnier ist bereits beendet.");
        }
        if (tournament.getCurrentRound() > 0) {
            TournamentRound prevRound = tournamentRoundRepository.findByTournamentAndRoundNumber(tournament, tournament.getCurrentRound());
            if (prevRound != null && !prevRound.isCompleted()) {
                throw new IllegalStateException("Die aktuelle Runde " + prevRound.getRoundNumber() + " ist noch nicht abgeschlossen.");
            }
        }

        int nextRoundNumber = tournament.getCurrentRound() + 1;
        if (nextRoundNumber > tournament.getTotalRounds()) {
            tournament.setFinished(true);
            tournament.setEndTime(LocalDateTime.now());
            tournamentRepository.save(tournament);
            throw new IllegalStateException("Alle Runden sind gespielt. Das Turnier ist beendet.");
        }

        TournamentRound newRound = new TournamentRound(nextRoundNumber, tournament);
        tournamentRoundRepository.save(newRound);

        // Aktualisiere Buchholz vor jeder neuen Runde (nachdem Ergebnisse der letzten Runde vorliegen)
        if (tournament.getCurrentRound() > 0) {
            recalculateBuchholz(tournament);
        }

        List<Teilnehmer> activePlayers = teilnehmerRepository.findByAnwesenheitsStatus("anwesend");
        // Sortiere Spieler nach Punkten (absteigend) und dann nach Buchholz (absteigend), Name (aufsteigend) für die Paarung
        activePlayers.sort(Comparator
                .comparing(Teilnehmer::getTournamentPoints, Comparator.reverseOrder())
                .thenComparing(Teilnehmer::getBuchholzScore, Comparator.reverseOrder())
                .thenComparing(Teilnehmer::getName));

        List<Teilnehmer> unpairedPlayers = new ArrayList<>(activePlayers);
        List<Pairing> pairings = new ArrayList<>();
        Set<Teilnehmer> pairedThisRound = new HashSet<>();

        // Handle Freilos (Bye)
        if (unpairedPlayers.size() % 2 != 0) {
            // Finde den niedrigstplatzierten Spieler ohne Freilos in vorherigen Runden
            Optional<Teilnehmer> byePlayerOpt = unpairedPlayers.stream()
                    .filter(p -> !hasReceivedByeInPreviousRounds(p, tournament))
                    .min(Comparator
                            .comparing(Teilnehmer::getTournamentPoints)
                            .thenComparing(Teilnehmer::getName)); // Bei gleichen Punkten Alphabetisch

            if (byePlayerOpt.isPresent()) {
                Teilnehmer byePlayer = byePlayerOpt.get();
                pairings.add(new Pairing(byePlayer, newRound));
                pairedThisRound.add(byePlayer);
                unpairedPlayers.remove(byePlayer);
                System.out.println("Freilos für: " + byePlayer.getName() + " in Runde " + newRound.getRoundNumber());
            } else {
                // Fallback: Wenn alle schon ein Freilos hatten, gib es dem Letzten
                Teilnehmer byePlayer = unpairedPlayers.get(unpairedPlayers.size() - 1);
                pairings.add(new Pairing(byePlayer, newRound));
                pairedThisRound.add(byePlayer);
                unpairedPlayers.remove(byePlayer);
                System.out.println("Freilos für: " + byePlayer.getName() + " (alle hatten schon bye) in Runde " + newRound.getRoundNumber());
            }
        }

        // Paarungsalgorithmus (sehr vereinfachtes Schweizer System)
        // Versuche, Spieler mit ähnlichen Punkten zu paaren
        for (int i = 0; i < unpairedPlayers.size(); i++) {
            Teilnehmer white = unpairedPlayers.get(i);
            if (pairedThisRound.contains(white)) continue;

            for (int j = i + 1; j < unpairedPlayers.size(); j++) {
                Teilnehmer black = unpairedPlayers.get(j);
                if (pairedThisRound.contains(black)) continue;

                // Überprüfe, ob sie bereits gegeneinander gespielt haben
                if (!havePlayedBefore(white, black, tournament)) {
                    // Berücksichtige Farben (vereinfacht: versuche, Farben auszugleichen)
                    if (canPairWithColorBalance(white, black, tournament)) {
                        pairings.add(new Pairing(white, black, newRound));
                        pairedThisRound.add(white);
                        pairedThisRound.add(black);
                        break; // Schwarzer Spieler gefunden, weiter zum nächsten weißen Spieler
                    }
                }
            }
        }

        // Fallback für nicht gepaarte Spieler (kann bei komplexeren Regeln vorkommen)
        // Hier müsste eine robustere Lösung her, die z.B. stärkere Gegner zulässt oder mehr Iterationen macht.
        // Für eine einfache Implementierung, paaren wir sie einfach der Reihe nach auf
        List<Teilnehmer> remainingUnpaired = unpairedPlayers.stream()
                .filter(p -> !pairedThisRound.contains(p))
                .collect(Collectors.toList());

        for (int i = 0; i + 1 < remainingUnpaired.size(); i += 2) {
            Teilnehmer white = remainingUnpaired.get(i);
            Teilnehmer black = remainingUnpaired.get(i + 1);
            if (!pairedThisRound.contains(white) && !pairedThisRound.contains(black)) {
                pairings.add(new Pairing(white, black, newRound));
                pairedThisRound.add(white);
                pairedThisRound.add(black);
            }
        }


        // Speichern der Paarungen
        for (Pairing p : pairings) {
            newRound.addPairing(p);
            pairingRepository.save(p);
        }

        tournament.setCurrentRound(nextRoundNumber);
        tournamentRepository.save(tournament);

        return newRound;
    }

    /**
     * Überprüft, ob zwei Spieler in diesem Turnier bereits gegeneinander gespielt haben.
     */
    private boolean havePlayedBefore(Teilnehmer p1, Teilnehmer p2, Tournament tournament) {
        for (TournamentRound round : tournament.getRounds()) {
            for (Pairing pairing : round.getPairings()) {
                if (!pairing.isBye()) { // Freilose zählen nicht als gespielte Partien
                    if ((pairing.getWhitePlayer().equals(p1) && pairing.getBlackPlayer().equals(p2)) ||
                            (pairing.getWhitePlayer().equals(p2) && pairing.getBlackPlayer().equals(p1))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Prüft, ob ein Spieler in vorherigen Runden ein Freilos erhalten hat.
     */
    private boolean hasReceivedByeInPreviousRounds(Teilnehmer player, Tournament tournament) {
        return tournament.getRounds().stream()
                .flatMap(r -> r.getPairings().stream())
                .filter(Pairing::isBye)
                .anyMatch(p -> p.getWhitePlayer().equals(player));
    }

    /**
     * Vereinfachte Farbbalance-Prüfung: Versucht, abwechselnd weiße und schwarze Farben zu verteilen.
     * Für ein echtes Schweizer System sind hier detailliertere Farbgeschichten notwendig.
     * Hier wird nur geprüft, ob der Spieler nicht 2x die gleiche Farbe in Folge hatte
     * und versucht, die Gesamtanzahl an Weiß- und Schwarzpartien auszugleichen.
     */
    private boolean canPairWithColorBalance(Teilnehmer whiteCandidate, Teilnehmer blackCandidate, Tournament tournament) {
        // Zähle gespielte Weiß- und Schwarzpartien für beide Spieler
        int whiteCount1 = 0;
        int blackCount1 = 0;
        int whiteCount2 = 0;
        int blackCount2 = 0;

        for (TournamentRound round : tournament.getRounds()) {
            for (Pairing pairing : round.getPairings()) {
                if (!pairing.isBye()) {
                    if (pairing.getWhitePlayer().equals(whiteCandidate)) whiteCount1++;
                    if (pairing.getBlackPlayer().equals(whiteCandidate)) blackCount1++;
                    if (pairing.getWhitePlayer().equals(blackCandidate)) whiteCount2++;
                    if (pairing.getBlackPlayer().equals(blackCandidate)) blackCount2++;
                }
            }
        }

        // Einfache Regel: Versuche Spieler zu vermeiden, die 2x hintereinander die gleiche Farbe hatten (außer in Runde 1)
        if (tournament.getCurrentRound() > 0) {
            Optional<Pairing> lastPairingWhite = tournament.getRounds().get(tournament.getCurrentRound() - 1).getPairings().stream()
                    .filter(p -> p.getWhitePlayer() != null && p.getWhitePlayer().equals(whiteCandidate))
                    .findFirst();
            Optional<Pairing> lastPairingBlack = tournament.getRounds().get(tournament.getCurrentRound() - 1).getPairings().stream()
                    .filter(p -> p.getBlackPlayer() != null && p.getBlackPlayer().equals(blackCandidate))
                    .findFirst();

            if (lastPairingWhite.isPresent() && lastPairingWhite.get().getWhitePlayer().equals(whiteCandidate)) { // whiteCandidate hatte zuletzt Weiß
                return false; // Darf nicht wieder Weiß bekommen
            }
            if (lastPairingBlack.isPresent() && lastPairingBlack.get().getBlackPlayer().equals(blackCandidate)) { // blackCandidate hatte zuletzt Schwarz
                return false; // Darf nicht wieder Schwarz bekommen
            }
        }


        // Versuche, dass niemand zu viele Weiß- oder Schwarzpartien hat
        // z.B. wenn whiteCandidate schon viel Weiß hatte und blackCandidate viel Schwarz,
        // ist diese Paarung möglicherweise ungünstig, wenn man Farbe ausgleichen will.
        if (Math.abs(whiteCount1 - blackCount1) > 1 && (whiteCount1 > blackCount1)) return false; // whiteCandidate hat zu viel Weiß
        if (Math.abs(whiteCount2 - blackCount2) > 1 && (blackCount2 > whiteCount2)) return false; // blackCandidate hat zu viel Schwarz

        return true; // Paarung ist zulässig (vereinfacht)
    }


    /**
     * Meldet ein Ergebnis für eine Paarung und aktualisiert die Punkte der Spieler.
     * @param pairingId ID der Paarung
     * @param result Ergebnis (1.0 = Weiß gewinnt, 0.5 = Remis, 0.0 = Schwarz gewinnt)
     * @return Die aktualisierte Paarung.
     * @throws IllegalArgumentException bei ungültigem Ergebnis
     * @throws NoSuchElementException wenn Paarung nicht gefunden wird
     */
    @Transactional
    public Pairing reportResult(Long pairingId, Double result) {
        if (result != 0.0 && result != 0.5 && result != 1.0) {
            throw new IllegalArgumentException("Ungültiges Ergebnis. Erlaubt sind 1.0 (Weiß gewinnt), 0.5 (Remis), 0.0 (Schwarz gewinnt).");
        }

        Pairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new NoSuchElementException("Paarung mit ID " + pairingId + " nicht gefunden."));

        if (pairing.getResult() != null) {
            // Wenn bereits ein Ergebnis vorhanden ist, muss man erst die alten Punkte rückgängig machen.
            // Für diese einfache Implementierung überspringen wir das oder verbieten es.
            // Hier verbieten wir es, um Komplexität zu reduzieren.
            throw new IllegalStateException("Ergebnis für diese Paarung wurde bereits gemeldet.");
        }

        pairing.setResult(result);
        pairingRepository.save(pairing);

        Teilnehmer whitePlayer = pairing.getWhitePlayer();
        Teilnehmer blackPlayer = pairing.getBlackPlayer();

        // Punkte aktualisieren
        if (whitePlayer != null) {
            if (result == 1.0) whitePlayer.setTournamentPoints(whitePlayer.getTournamentPoints() + 1.0);
            else if (result == 0.5) whitePlayer.setTournamentPoints(whitePlayer.getTournamentPoints() + 0.5);
            teilnehmerRepository.save(whitePlayer);
        }
        if (blackPlayer != null) {
            if (result == 0.0) blackPlayer.setTournamentPoints(blackPlayer.getTournamentPoints() + 1.0);
            else if (result == 0.5) blackPlayer.setTournamentPoints(blackPlayer.getTournamentPoints() + 0.5);
            teilnehmerRepository.save(blackPlayer);
        }

        // Prüfen, ob alle Paarungen in der Runde abgeschlossen sind
        TournamentRound round = pairing.getRound();
        boolean allPairingsCompleted = round.getPairings().stream()
                .allMatch(p -> p.getResult() != null);
        if (allPairingsCompleted) {
            round.setCompleted(true);
            tournamentRoundRepository.save(round);
        }

        return pairing;
    }

    /**
     * Berechnet die Buchholz-Wertung für alle anwesenden Spieler.
     * Die Buchholz-Wertung ist die Summe der Punkte der Gegner.
     */
    @Transactional
    public void recalculateBuchholz(Tournament tournament) {
        List<Teilnehmer> activePlayers = teilnehmerRepository.findByAnwesenheitsStatus("anwesend");
        Map<Long, Double> playerPointsMap = activePlayers.stream()
                .collect(Collectors.toMap(Teilnehmer::getId, Teilnehmer::getTournamentPoints));

        for (Teilnehmer player : activePlayers) {
            double buchholz = 0.0;
            // Sammle alle Gegner, gegen die der Spieler in diesem Turnier gespielt hat
            List<Teilnehmer> opponents = new ArrayList<>();
            for (TournamentRound round : tournament.getRounds()) {
                for (Pairing pairing : round.getPairings()) {
                    if (pairing.isBye()) continue; // Freilose zählen nicht für Buchholz

                    if (pairing.getWhitePlayer().equals(player)) {
                        opponents.add(pairing.getBlackPlayer());
                    } else if (pairing.getBlackPlayer().equals(player)) {
                        opponents.add(pairing.getWhitePlayer());
                    }
                }
            }

            // Summiere die Punkte der Gegner
            for (Teilnehmer opponent : opponents) {
                buchholz += playerPointsMap.getOrDefault(opponent.getId(), 0.0);
            }
            player.setBuchholzScore(buchholz);
            teilnehmerRepository.save(player); // Speichern der aktualisierten Buchholz-Wertung
        }
    }

    /**
     * Beendet das aktuelle Turnier.
     */
    @Transactional
    public Tournament endTournament(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new NoSuchElementException("Turnier mit ID " + tournamentId + " nicht gefunden."));
        tournament.setFinished(true);
        tournament.setEndTime(LocalDateTime.now());
        tournamentRepository.save(tournament);
        recalculateBuchholz(tournament); // Letzte Buchholz-Berechnung
        return tournament;
    }

    /**
     * Liefert das aktuell laufende Turnier.
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
        // Optional: Alte Turniere und Runden löschen oder archivieren, falls nicht mehr benötigt
        tournamentRepository.findAll().forEach(tournamentRepository::delete);
    }
}