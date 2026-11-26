package sn.uadb.supervision.superviseur;

/** États affichés, du meilleur au pire. */
public enum EtatSante {
    UP, DEGRADE, INCONNU, OUT_OF_SERVICE, DOWN, INJOIGNABLE;

    /** L'instance rend-elle encore le service ? */
    public boolean disponible() {
        return this == UP || this == DEGRADE;
    }

    static EtatSante depuisActuator(String statut) {
        return switch (statut == null ? "" : statut) {
            case "UP" -> UP;
            case "DEGRADE" -> DEGRADE;
            case "DOWN" -> DOWN;
            case "OUT_OF_SERVICE" -> OUT_OF_SERVICE;
            default -> INCONNU;
        };
    }
}
