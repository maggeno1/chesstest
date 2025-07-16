package de.mschanzer.chesstest.chesstest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import java.util.List;

public interface PairingRepository extends JpaRepository<Pairing, Long> {
    List<Pairing> findByRound(TournamentRound round);
}