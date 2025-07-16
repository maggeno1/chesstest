package de.mschanzer.chesstest.chesstest; // Stellen Sie sicher, dass das Paket korrekt ist

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Tournament {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int currentRound;
    private int totalRounds;
    private boolean finished;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("roundNumber ASC")
    private List<TournamentRound> rounds = new ArrayList<>();

    // Konstruktoren
    public Tournament() {}

    public Tournament(String name, int totalRounds) {
        this.name = name;
        this.startTime = LocalDateTime.now();
        this.currentRound = 0; // Turnier startet vor Runde 1
        this.totalRounds = totalRounds;
        this.finished = false;
    }

    // Getter und Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public void setTotalRounds(int totalRounds) {
        this.totalRounds = totalRounds;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public List<TournamentRound> getRounds() {
        return rounds;
    }

    public void addRound(TournamentRound round) {
        this.rounds.add(round);
        round.setTournament(this);
    }
}