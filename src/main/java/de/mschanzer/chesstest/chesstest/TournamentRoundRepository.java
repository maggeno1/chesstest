package de.mschanzer.chesstest.chesstest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import java.util.List;

public interface TournamentRoundRepository extends JpaRepository<TournamentRound, Long> {
    List<TournamentRound> findByTournamentOrderByRoundNumberAsc(Tournament tournament);
    TournamentRound findByTournamentAndRoundNumber(Tournament tournament, int roundNumber);
}