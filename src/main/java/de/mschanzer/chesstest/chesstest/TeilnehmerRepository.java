package de.mschanzer.chesstest.chesstest;

import org.springframework.data.repository.CrudRepository;
import java.util.List;

public interface TeilnehmerRepository extends CrudRepository<Teilnehmer, Long> {
    List<Teilnehmer> findByVereinAndAnwesenheitsStatus(String verein, String anwesenheitsStatus);
}