package sn.uadb.supervision.commun;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** État de la panne injectée pour la démonstration (en mémoire, propre à chaque instance). */
public class PanneSimulee {

    static final int LATENCE_MAX_MS = 10_000;

    public enum Statut { UP, DEGRADE, OUT_OF_SERVICE, DOWN }

    public record Etat(Statut statut, String raison, int latenceMs, Instant depuis) {
    }

    private final AtomicReference<Etat> etat = new AtomicReference<>(aucune());

    public Etat lire() {
        return etat.get();
    }

    public Etat declencher(Statut statut, String raison, int latenceMs) {
        var nouveau = new Etat(statut, raison, Math.clamp(latenceMs, 0, LATENCE_MAX_MS), Instant.now());
        etat.set(nouveau);
        return nouveau;
    }

    public Etat retablir() {
        etat.set(aucune());
        return etat.get();
    }

    private static Etat aucune() {
        return new Etat(Statut.UP, "aucune panne", 0, Instant.now());
    }
}
