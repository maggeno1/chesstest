package de.mschanzer.chesstest.chesstest;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jdbc.repository.query.Query; // Diese Import-Anweisung hinzufügen
import java.util.List;

public interface TeilnehmerRepository extends CrudRepository<Teilnehmer, Long> {
    List<Teilnehmer> findByVereinAndAnwesenheitsStatus(String verein, String anwesenheitsStatus);

    // NEU: Methode zum Abrufen aller einzigartigen Vereinsnamen
    @Query("SELECT DISTINCT verein FROM teilnehmer WHERE verein IS NOT NULL AND verein != '' ORDER BY verein")
    List<String> findDistinctVereinNames();
}