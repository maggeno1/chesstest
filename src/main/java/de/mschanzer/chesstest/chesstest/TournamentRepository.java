package de.mschanzer.chesstest.chesstest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    // Optional: Eine Methode, um das aktuellste/laufende Turnier zu finden
    // z.B. Optional<Tournament> findTopByFinishedOrderByStartTimeDesc(boolean finished);
    // oder findAllByFinished(false) wenn nur ein Turnier gleichzeitig läuft
    Tournament findByFinished(boolean finished); // Findet ein unfertiges Turnier
}