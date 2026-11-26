package sn.uadb.supervision.catalogue;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;
import sn.uadb.supervision.commun.Statuts;

/** Santé métier : une rupture de stock dégrade le service sans le rendre indisponible. */
@Component
public class StockHealthIndicator extends AbstractHealthIndicator {

    private final ProduitRepository produits;

    public StockHealthIndicator(ProduitRepository produits) {
        super("Lecture du stock impossible");
        this.produits = produits;
    }

    @Override
    protected void doHealthCheck(Health.Builder sante) {
        // Une exception ici (base coupée) est convertie en DOWN par AbstractHealthIndicator
        long ruptures = produits.countByStock(0);
        sante.status(ruptures == 0 ? Status.UP : Statuts.DEGRADE)
                .withDetail("produitsEnRupture", ruptures);
    }
}
