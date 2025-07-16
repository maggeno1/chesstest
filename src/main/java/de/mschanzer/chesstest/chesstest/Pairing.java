package de.mschanzer.chesstest.chesstest; // Stellen Sie sicher, dass das Paket korrekt ist

import jakarta.persistence.*;

@Entity
public class Pairing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_player_id")
    private Teilnehmer whitePlayer; // Spieler mit den weißen Figuren

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_player_id")
    private Teilnehmer blackPlayer; // Spieler mit den schwarzen Figuren

    private Double result; // 1.0 = Weiß gewinnt, 0.5 = Remis, 0.0 = Schwarz gewinnt
    private boolean bye; // True, wenn dies ein Freilos ist

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id")
    private TournamentRound round;

    // Konstruktoren
    public Pairing() {}

    public Pairing(Teilnehmer whitePlayer, Teilnehmer blackPlayer, TournamentRound round) {
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.round = round;
        this.bye = false;
    }

    public Pairing(Teilnehmer playerWithBye, TournamentRound round) {
        this.whitePlayer = playerWithBye; // Der Spieler, der das Freilos bekommt, spielt "weiß"
        this.blackPlayer = null;
        this.round = round;
        this.result = 1.0; // Freilos ist immer 1 Punkt für den Spieler
        this.bye = true;
    }

    // Getter und Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Teilnehmer getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(Teilnehmer whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public Teilnehmer getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(Teilnehmer blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public Double getResult() {
        return result;
    }

    public void setResult(Double result) {
        this.result = result;
    }

    public boolean isBye() {
        return bye;
    }

    public void setBye(boolean bye) {
        this.bye = bye;
    }

    public TournamentRound getRound() {
        return round;
    }

    public void setRound(TournamentRound round) {
        this.round = round;
    }
}