package de.mschanzer.chesstest.chesstest;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Teilnehmer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String verein;
    private String name;
    private String altersklasse;
    private boolean startgebuehrBezahlt;
    private boolean startgebuehrMussNichtZahlen; // Neu hinzugefügt
    private String anwesenheitsStatus; // "anwesend", "nicht anwesend", "abgesagt"
    private String kommentar;

    // NEU: Turnierfelder
    private Double tournamentPoints = 0.0; // Punkte im Schweizer System Turnier
    private Double buchholzScore = 0.0;    // Buchholz-Wertung

    public Teilnehmer() {}

    // Konstruktor, falls benötigt (passen Sie ihn an Ihre Bedürfnisse an)
    public Teilnehmer(String verein, String name, String altersklasse) {
        this.verein = verein;
        this.name = name;
        this.altersklasse = altersklasse;
        this.startgebuehrBezahlt = false;
        this.startgebuehrMussNichtZahlen = false;
        this.anwesenheitsStatus = "nicht anwesend";
        this.kommentar = "";
        this.tournamentPoints = 0.0;
        this.buchholzScore = 0.0;
    }

    // Getter und Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVerein() {
        return verein;
    }

    public void setVerein(String verein) {
        this.verein = verein;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAltersklasse() {
        return altersklasse;
    }

    public void setAltersklasse(String altersklasse) {
        this.altersklasse = altersklasse;
    }

    public boolean isStartgebuehrBezahlt() {
        return startgebuehrBezahlt;
    }

    public void setStartgebuehrBezahlt(boolean startgebuehrBezahlt) {
        this.startgebuehrBezahlt = startgebuehrBezahlt;
    }

    public boolean isStartgebuehrMussNichtZahlen() {
        return startgebuehrMussNichtZahlen;
    }

    public void setStartgebuehrMussNichtZahlen(boolean startgebuehrMussNichtZahlen) {
        this.startgebuehrMussNichtZahlen = startgebuehrMussNichtZahlen;
    }

    public String getAnwesenheitsStatus() {
        return anwesenheitsStatus;
    }

    public void setAnwesenheitsStatus(String anwesenheitsStatus) {
        this.anwesenheitsStatus = anwesenheitsStatus;
    }

    public String getKommentar() {
        return kommentar;
    }

    public void setKommentar(String kommentar) {
        this.kommentar = kommentar;
    }

    // NEUE Getter und Setter für Turnierfelder
    public Double getTournamentPoints() {
        return tournamentPoints;
    }

    public void setTournamentPoints(Double tournamentPoints) {
        this.tournamentPoints = tournamentPoints;
    }

    public Double getBuchholzScore() {
        return buchholzScore;
    }

    public void setBuchholzScore(Double buchholzScore) {
        this.buchholzScore = buchholzScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Teilnehmer that = (Teilnehmer) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}