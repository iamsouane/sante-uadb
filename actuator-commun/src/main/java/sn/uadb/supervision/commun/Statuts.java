package sn.uadb.supervision.commun;

import org.springframework.boot.health.contributor.Status;

public final class Statuts {

    /** Le service répond, mais une fonction secondaire est atteinte. HTTP 200, reste routable. */
    public static final Status DEGRADE = new Status("DEGRADE");

    private Statuts() {
    }
}
