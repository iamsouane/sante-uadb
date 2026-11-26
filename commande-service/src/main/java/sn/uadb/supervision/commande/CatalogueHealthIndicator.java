package sn.uadb.supervision.commande;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import sn.uadb.supervision.commun.Statuts;

/**
 * Santé de la dépendance au catalogue, lue dans Eureka.
 *
 * <p>Catalogue absent = DEGRADE et non DOWN : les commandes existantes restent consultables, et
 * déclarer DOWN tous les appelants propagerait la panne en cascade.
 */
@Component
public class CatalogueHealthIndicator implements HealthIndicator {

    private final DiscoveryClient annuaire;

    public CatalogueHealthIndicator(DiscoveryClient annuaire) {
        this.annuaire = annuaire;
    }

    @Override
    public Health health() {
        int instances = annuaire.getInstances(CatalogueClient.SERVICE).size();
        return (instances > 0 ? Health.up() : Health.status(Statuts.DEGRADE))
                .withDetail("instancesDisponibles", instances)
                .build();
    }
}
