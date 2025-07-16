package de.mschanzer.chesstest.chesstest; // Stellen Sie sicher, dass das Paket korrekt ist

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class TournamentRound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int roundNumber;
    private boolean completed; // Zeigt an, ob alle Partien dieser Runde ein Ergebnis haben

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Pairing> pairings = new ArrayList<>();

    // Konstruktoren
    public TournamentRound() {}

    public TournamentRound(int roundNumber, Tournament tournament) {
        this.roundNumber = roundNumber;
        this.tournament = tournament;
        this.completed = false;
    }

    // Getter und Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public void setTournament(Tournament tournament) {
        this.tournament = tournament;
    }

    public List<Pairing> getPairings() {
        return pairings;
    }

    public void addPairing(Pairing pairing) {
        this.pairings.add(pairing);
        pairing.setRound(this);
    }
}