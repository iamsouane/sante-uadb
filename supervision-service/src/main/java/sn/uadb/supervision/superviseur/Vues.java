package sn.uadb.supervision.superviseur;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Objets exposés par l'API REST (contrat OpenAPI). */
public final class Vues {

    public static final String ABSENTE = "ABSENTE";

    private Vues() {
    }

    /** @param statutEureka statut déclaré dans Eureka, ou ABSENTE si l'instance n'y est plus
     *  @param divergence vrai si Eureka et Actuator ne disent pas la même chose */
    public record Instance(String id, String service, String url, String statutEureka, EtatSante sante,
                           boolean divergence, long latenceMs, Map<String, String> composants, String detail,
                           Instant depuis, Instant verifieeLe) {
    }

    /** @param disponibilite pourcentage de sondes où le service était rendu, depuis sa première apparition */
    public record Service(String nom, EtatSante etat, int instancesDisponibles, int instancesTotal, int minimum,
                          double disponibilite, List<Instance> instances) {
    }

    public record Etat(EtatSante global, Instant horodatage, EtatSante eureka, List<Service> services) {
    }

    public record Evenement(Instant horodatage, String service, String instance, EtatSante de, EtatSante vers,
                            String detail) {
    }

    public record DemandePanne(@NotNull StatutPanne statut,
                               @Size(max = 120) String raison,
                               @Min(0) @Max(10_000) Integer latenceMs) {
    }

    public enum StatutPanne { UP, DEGRADE, OUT_OF_SERVICE, DOWN }
}
