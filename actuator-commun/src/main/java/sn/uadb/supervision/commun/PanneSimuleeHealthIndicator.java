package sn.uadb.supervision.commun;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** Composant « panneSimulee » de /actuator/health. */
public class PanneSimuleeHealthIndicator implements HealthIndicator {

    private final PanneSimulee panne;

    public PanneSimuleeHealthIndicator(PanneSimulee panne) {
        this.panne = panne;
    }

    @Override
    public Health health() {
        var etat = panne.lire();
        if (etat.latenceMs() > 0) {
            attendre(etat.latenceMs());
        }
        return Health.status(etat.statut().name())
                .withDetail("raison", etat.raison())
                .withDetail("depuis", etat.depuis().toString())
                .build();
    }

    private static void attendre(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
