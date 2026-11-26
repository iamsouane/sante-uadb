package sn.uadb.supervision.commun;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

/** Endpoint Actuator personnalisé : /actuator/panne (lecture SUPERVISION, écriture ADMIN). */
@Endpoint(id = "panne")
public class PanneEndpoint {

    private final PanneSimulee panne;

    public PanneEndpoint(PanneSimulee panne) {
        this.panne = panne;
    }

    @ReadOperation
    public PanneSimulee.Etat lire() {
        return panne.lire();
    }

    @WriteOperation
    public PanneSimulee.Etat declencher(PanneSimulee.Statut statut, @Nullable String raison, @Nullable Integer latenceMs) {
        return panne.declencher(statut, raison == null ? "panne injectée" : raison, latenceMs == null ? 0 : latenceMs);
    }

    @DeleteOperation
    public PanneSimulee.Etat retablir() {
        return panne.retablir();
    }
}
