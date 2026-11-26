package sn.uadb.supervision.superviseur;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuperviseurTests {

    private static final String URL = "http://10.0.0.1:8081";

    private final HorlogeReglable horloge = new HorlogeReglable();
    private final List<InstanceDecouverte> registre = new ArrayList<>();
    private final Sonde sonde = mock(Sonde.class);
    private final SimpleMeterRegistry metriques = new SimpleMeterRegistry();
    private Superviseur superviseur;

    @BeforeEach
    void preparer() {
        var config = new SupervisionProperties("http://eureka/health", Duration.ofSeconds(5), Duration.ofSeconds(1),
                Duration.ofSeconds(3), 2, Duration.ofMinutes(3), 10, Map.of("catalogue-service", 1));
        when(sonde.interroger(anyString())).thenReturn(resultat(EtatSante.UP));
        when(sonde.interrogerAnonyme(anyString())).thenReturn(resultat(EtatSante.UP));
        superviseur = new Superviseur(() -> List.copyOf(registre), sonde, config, metriques, horloge);
    }

    @Test
    void unServiceAttenduMaisJamaisDemarreEstDown() {
        superviseur.verifier();
        assertThat(service().etat()).isEqualTo(EtatSante.DOWN);
        assertThat(superviseur.etat().global()).isEqualTo(EtatSante.DOWN);
    }

    @Test
    void instanceSaineDonneUp() {
        enregistrer("a", "UP");
        superviseur.verifier();
        assertThat(service().etat()).isEqualTo(EtatSante.UP);
        assertThat(service().instances().getFirst().divergence()).isFalse();
    }

    @Test
    void uneInstanceDownSurDeuxNEmpechePasLeServiceMaisLaCapaciteBaisse() {
        enregistrer("a", "UP");
        enregistrer("b", "DOWN");
        sante("b", EtatSante.DOWN);
        superviseur.verifier();
        assertThat(service().instancesDisponibles()).isEqualTo(1);
        // minimum = 1 atteint et l'instance restante est saine
        assertThat(service().etat()).isEqualTo(EtatSante.UP);
    }

    @Test
    void sousLeMinimumLeServiceEstDegrade() {
        assertThat(Superviseur.etatService(List.of(), 1, 2)).isEqualTo(EtatSante.DEGRADE);
        assertThat(Superviseur.etatService(List.of(), 0, 2)).isEqualTo(EtatSante.DOWN);
        assertThat(Superviseur.etatService(List.of(), 2, 2)).isEqualTo(EtatSante.UP);
    }

    @Test
    void signaleLaDivergenceEntreEurekaEtActuator() {
        enregistrer("a", "UP");
        sante("a", EtatSante.DOWN);
        superviseur.verifier();
        assertThat(service().instances().getFirst().divergence()).isTrue();
    }

    @Test
    void injoignableSeulementApresDeuxEchecsConsecutifs() {
        enregistrer("a", "UP");
        superviseur.verifier();
        sante("a", EtatSante.INJOIGNABLE);
        superviseur.verifier();
        assertThat(service().instances().getFirst().sante()).isEqualTo(EtatSante.UP);
        superviseur.verifier();
        assertThat(service().instances().getFirst().sante()).isEqualTo(EtatSante.INJOIGNABLE);
    }

    @Test
    void chaqueChangementEstHistoriseUneSeuleFois() {
        enregistrer("a", "UP");
        superviseur.verifier();
        superviseur.verifier();
        sante("a", EtatSante.DOWN);
        superviseur.verifier();
        superviseur.verifier();
        assertThat(superviseur.evenements(null, 50)).extracting(Vues.Evenement::vers)
                .containsExactly(EtatSante.DOWN, EtatSante.UP);
        assertThat(superviseur.evenements("autre", 50)).isEmpty();
    }

    @Test
    void lHistoriqueEstBorne() {
        enregistrer("a", "UP");
        for (int i = 0; i < 30; i++) {
            sante("a", i % 2 == 0 ? EtatSante.UP : EtatSante.DOWN);
            superviseur.verifier();
        }
        assertThat(superviseur.evenements(null, 500)).hasSize(10);
    }

    @Test
    void uneInstanceDisparueResteVisiblePuisEstRetiree() {
        enregistrer("a", "UP");
        superviseur.verifier();
        registre.clear();
        sante("a", EtatSante.INJOIGNABLE);
        superviseur.verifier();
        superviseur.verifier();
        assertThat(service().instances().getFirst().statutEureka()).isEqualTo(Vues.ABSENTE);

        horloge.avancer(Duration.ofMinutes(4));
        superviseur.verifier();
        assertThat(service().instances()).isEmpty();
        assertThat(superviseur.evenements(null, 1).getFirst().detail()).startsWith("retirée");
    }

    @Test
    void eurekaArreteDegradeLEtatGlobalSansLeMettreDown() {
        enregistrer("a", "UP");
        when(sonde.interrogerAnonyme("http://eureka/health")).thenReturn(resultat(EtatSante.INJOIGNABLE));
        superviseur.verifier();
        assertThat(superviseur.etat().eureka()).isEqualTo(EtatSante.INJOIGNABLE);
        assertThat(superviseur.etat().global()).isEqualTo(EtatSante.DEGRADE);
    }

    @Test
    void calculeLaDisponibiliteSansCompterLeDemarrage() {
        superviseur.verifier();
        superviseur.verifier();
        enregistrer("a", "UP");
        superviseur.verifier();
        superviseur.verifier();
        superviseur.verifier();
        sante("a", EtatSante.DOWN);
        superviseur.verifier();
        assertThat(service().disponibilite()).isEqualTo(75.0);
    }

    @Test
    void publieLesMetriques() {
        enregistrer("a", "UP");
        sante("a", EtatSante.DOWN);
        superviseur.verifier();
        assertThat(metriques.get("supervision.instance.disponible").gauge().value()).isZero();
        assertThat(metriques.get("supervision.transitions").tag("vers", "DOWN").counter().count()).isEqualTo(1);
    }

    private Vues.Service service() {
        return superviseur.etat().services().getFirst();
    }

    private void enregistrer(String id, String statutEureka) {
        registre.add(new InstanceDecouverte("catalogue-service", id, URL, URL + "/" + id, statutEureka));
    }

    private void sante(String id, EtatSante sante) {
        when(sonde.interroger(URL + "/" + id)).thenReturn(resultat(sante));
    }

    private static Sonde.Resultat resultat(EtatSante sante) {
        return new Sonde.Resultat(sante, 5, Map.of(), "");
    }

    private static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.parse("2026-11-25T08:00:00Z");

        void avancer(Duration d) {
            maintenant = maintenant.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }
}
