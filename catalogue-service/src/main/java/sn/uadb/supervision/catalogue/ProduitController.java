package sn.uadb.supervision.catalogue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/produits")
public class ProduitController {

    public record Quantite(@Min(1) @Max(1000) int quantite) {
    }

    private final ProduitRepository produits;

    public ProduitController(ProduitRepository produits) {
        this.produits = produits;
    }

    @GetMapping
    public List<Produit> lister() {
        return produits.findAll();
    }

    @GetMapping("/{id}")
    public Produit lire(@PathVariable long id) {
        return produits.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit inconnu"));
    }

    @PostMapping("/{id}/reservations")
    @Transactional
    public Produit reserver(@PathVariable long id, @Valid @RequestBody Quantite q) {
        lire(id);
        if (produits.reserver(id, q.quantite()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuffisant");
        }
        return lire(id);
    }

    /** Réservé au rôle ADMIN (voir SecuriteCatalogue). */
    @PostMapping("/{id}/reapprovisionnements")
    @Transactional
    public Produit reapprovisionner(@PathVariable long id, @Valid @RequestBody Quantite q) {
        lire(id);
        produits.reapprovisionner(id, q.quantite());
        return lire(id);
    }
}
