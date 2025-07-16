package de.mschanzer.chesstest.chesstest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TeilnehmerRepository extends JpaRepository<Teilnehmer, Long> {
    @Query("SELECT DISTINCT t.verein FROM Teilnehmer t WHERE t.verein IS NOT NULL AND t.verein <> ''")
    List<String> findDistinctVereinNames();

    List<Teilnehmer> findByVereinAndAnwesenheitsStatus(String verein, String anwesenheitsStatus);

    List<Teilnehmer> findByAnwesenheitsStatus(String anwesenheitsStatus); // NEU: Für Turnierlogik
}