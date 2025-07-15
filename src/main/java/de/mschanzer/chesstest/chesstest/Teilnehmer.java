package de.mschanzer.chesstest.chesstest;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data; // Von Lombok für Getter/Setter/etc.

@Data // Generiert Getter, Setter, toString, equals, hashCode
@Table("teilnehmer") // Stellt sicher, dass der Tabellenname 'teilnehmer' ist
public class Teilnehmer {

    @Id
    private Long id;
    private String verein;
    private String name;
    private String altersklasse;
    private boolean startgebuehrBezahlt;
    private boolean startgebuehrMussNichtZahlen;
    private String anwesenheitsStatus; // z.B. "anwesend", "nicht anwesend", "abgesagt"
    private String kommentar;

    // Standardkonstruktor für Spring Data JDBC
    public Teilnehmer() {
        this.startgebuehrBezahlt = false;
        this.startgebuehrMussNichtZahlen = false;
        this.anwesenheitsStatus = "nicht anwesend";
    }

    // Optional: Ein Konstruktor für einfache Erstellung
    public Teilnehmer(String verein, String name, String altersklasse) {
        this.verein = verein;
        this.name = name;
        this.altersklasse = altersklasse;
        this.startgebuehrBezahlt = false;
        this.startgebuehrMussNichtZahlen = false;
        this.anwesenheitsStatus = "nicht anwesend";
    }

    public boolean isStartgebuehrBezahlt() {
        return startgebuehrBezahlt;
    }

    public void setStartgebuehrBezahlt(boolean startgebuehrBezahlt) {
        this.startgebuehrBezahlt = startgebuehrBezahlt;
    }

    public boolean isStartgebuehrMussNichtZahlen() { // <-- Wichtig: is-Präfix für boolean-Getter
        return startgebuehrMussNichtZahlen;
    }

    public void setStartgebuehrMussNichtZahlen(boolean startgebuehrMussNichtZahlen) {
        this.startgebuehrMussNichtZahlen = startgebuehrMussNichtZahlen;
    }
}