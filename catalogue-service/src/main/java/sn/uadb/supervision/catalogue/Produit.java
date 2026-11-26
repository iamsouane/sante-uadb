package sn.uadb.supervision.catalogue;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;

@Entity
public class Produit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nom;
    private BigDecimal prix;
    private int stock;

    protected Produit() {
    }

    public Produit(String nom, BigDecimal prix, int stock) {
        this.nom = nom;
        this.prix = prix;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public BigDecimal getPrix() {
        return prix;
    }

    public int getStock() {
        return stock;
    }
}
