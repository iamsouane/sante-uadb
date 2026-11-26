package sn.uadb.supervision.superviseur;

import java.util.List;

/** Port vers le registre de services (Eureka en production, liste fixe dans les tests). */
public interface Annuaire {

    List<InstanceDecouverte> instances();
}
