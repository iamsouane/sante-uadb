package sn.uadb.supervision.commande;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long produitId;
    private int quantite;
    private BigDecimal total;
    private Instant creeeLe;

    protected Commande() {
    }

    public Commande(Long produitId, int quantite, BigDecimal total) {
        this.produitId = produitId;
        this.quantite = quantite;
        this.total = total;
        this.creeeLe = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProduitId() {
        return produitId;
    }

    public int getQuantite() {
        return quantite;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreeeLe() {
        return creeeLe;
    }
}
