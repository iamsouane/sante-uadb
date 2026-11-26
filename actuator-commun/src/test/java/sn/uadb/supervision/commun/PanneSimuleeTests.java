package sn.uadb.supervision.commun;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class PanneSimuleeTests {

    private final PanneSimulee panne = new PanneSimulee();
    private final PanneEndpoint endpoint = new PanneEndpoint(panne);
    private final PanneSimuleeHealthIndicator indicateur = new PanneSimuleeHealthIndicator(panne);

    @Test
    void upParDefaut() {
        assertThat(indicateur.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void declencherPuisRetablir() {
        endpoint.declencher(PanneSimulee.Statut.DOWN, "test", null);
        assertThat(indicateur.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicateur.health().getDetails()).containsEntry("raison", "test");

        endpoint.retablir();
        assertThat(indicateur.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void laLatenceEstBornee() {
        assertThat(endpoint.declencher(PanneSimulee.Statut.UP, null, 999_999).latenceMs())
                .isEqualTo(PanneSimulee.LATENCE_MAX_MS);
        assertThat(endpoint.declencher(PanneSimulee.Statut.UP, null, -5).latenceMs()).isZero();
    }
}
