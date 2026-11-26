package sn.uadb.supervision.superviseur;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cœur de la supervision : à chaque cycle, croise le registre Eureka et les sondes
 * /actuator/health, détecte les changements d'état et publie les métriques.
 *
 * <p>Tout est en mémoire, volontairement : un superviseur ne doit pas dépendre de la base de
 * données qu'il surveille.
 */
@Service
public class Superviseur {

    private static final Logger log = LoggerFactory.getLogger(Superviseur.class);

    /** Suivi interne d'une instance ; accès protégé par le verrou de Superviseur. */
    private static final class Suivi {
        InstanceDecouverte instance;
        String statutEureka;
        EtatSante sante = EtatSante.INCONNU;
        int echecs;
        Sonde.Resultat dernier = new Sonde.Resultat(EtatSante.INCONNU, 0, Map.of(), "jamais sondée");
        Instant depuis;
        Instant verifieeLe;
        Instant vueDansEureka;
    }

    private static final class Compteur {
        long total;
        long disponibles;
    }

    private final Annuaire annuaire;
    private final Sonde sonde;
    private final SupervisionProperties config;
    private final Clock horloge;
    private final MeterRegistry metriques;
    private final MultiGauge jaugeInstances;
    private final MultiGauge jaugeServices;

    private final Map<String, Suivi> suivis = new TreeMap<>();
    private final Map<String, Compteur> compteurs = new LinkedHashMap<>();
    private final Deque<Vues.Evenement> evenements = new ArrayDeque<>();
    private volatile EtatSante eureka = EtatSante.INCONNU;

    @Autowired
    public Superviseur(Annuaire annuaire, Sonde sonde, SupervisionProperties config, MeterRegistry metriques) {
        this(annuaire, sonde, config, metriques, Clock.systemUTC());
    }

    Superviseur(Annuaire annuaire, Sonde sonde, SupervisionProperties config, MeterRegistry metriques, Clock horloge) {
        this.annuaire = annuaire;
        this.sonde = sonde;
        this.config = config;
        this.horloge = horloge;
        this.metriques = metriques;
        this.jaugeInstances = MultiGauge.builder("supervision.instance.disponible")
                .description("1 si l'instance rend le service").register(metriques);
        this.jaugeServices = MultiGauge.builder("supervision.service.instances.disponibles")
                .description("Instances disponibles par service").register(metriques);
        Gauge.builder("supervision.eureka.disponible", () -> eureka.disponible() ? 1 : 0).register(metriques);
    }

    /** fixedDelay : le cycle suivant attend la fin du précédent, les sondes ne s'empilent jamais. */
    @Scheduled(fixedDelayString = "${supervision.intervalle}")
    public void verifier() {
        var aSonder = preparer(annuaire.instances());

        // Sondes en parallèle : une instance qui expire (3 s) ne retarde pas les autres.
        // close() attend la fin de toutes les tâches, resultNow() est donc sûr ensuite.
        var taches = new LinkedHashMap<String, Future<Sonde.Resultat>>();
        Future<Sonde.Resultat> tacheEureka;
        try (var executeur = Executors.newVirtualThreadPerTaskExecutor()) {
            tacheEureka = executeur.submit(() -> sonde.interrogerAnonyme(config.eurekaSanteUrl()));
            aSonder.forEach(i -> taches.put(i.id(), executeur.submit(() -> sonde.interroger(i.urlSante()))));
        }
        eureka = tacheEureka.resultNow().sante();
        var resultats = new LinkedHashMap<String, Sonde.Resultat>();
        taches.forEach((id, tache) -> resultats.put(id, tache.resultNow()));
        appliquer(resultats);
    }

    /** Met à jour la liste des instances suivies et renvoie celles à sonder. */
    private synchronized List<InstanceDecouverte> preparer(List<InstanceDecouverte> decouvertes) {
        var maintenant = horloge.instant();
        suivis.values().forEach(s -> s.statutEureka = Vues.ABSENTE);
        for (var d : decouvertes) {
            var suivi = suivis.computeIfAbsent(d.id(), id -> new Suivi());
            suivi.instance = d;
            suivi.statutEureka = d.statutEureka();
            suivi.vueDansEureka = maintenant;
        }
        // Une instance qui a quitté Eureka ET ne répond plus finit par être retirée (redéploiement)
        suivis.values().removeIf(s -> {
            boolean expiree = Vues.ABSENTE.equals(s.statutEureka) && !s.sante.disponible()
                    && s.vueDansEureka.plus(config.conservation()).isBefore(maintenant);
            if (expiree) {
                noter(s, s.sante, "retirée : absente d'Eureka depuis " + config.conservation().toSeconds() + " s");
            }
            return expiree;
        });
        return suivis.values().stream().map(s -> s.instance).toList();
    }

    private synchronized void appliquer(Map<String, Sonde.Resultat> resultats) {
        var maintenant = horloge.instant();
        resultats.forEach((id, resultat) -> {
            var suivi = suivis.get(id);
            if (suivi == null) {
                return;
            }
            suivi.dernier = resultat;
            suivi.verifieeLe = maintenant;
            suivi.echecs = resultat.sante() == EtatSante.INJOIGNABLE ? suivi.echecs + 1 : 0;

            var nouveau = resultat.sante();
            if (nouveau == EtatSante.INJOIGNABLE && suivi.echecs < config.seuilEchecs()) {
                nouveau = suivi.sante; // pas encore confirmé
            }
            if (nouveau != suivi.sante) {
                var ancien = suivi.sante;
                suivi.sante = nouveau;
                suivi.depuis = maintenant;
                noter(suivi, ancien, resultat.detail());
            }
        });
        publier();
    }

    private void noter(Suivi suivi, EtatSante ancien, String detail) {
        var e = new Vues.Evenement(horloge.instant(), suivi.instance.service(), suivi.instance.id(), ancien,
                suivi.sante, detail);
        evenements.addFirst(e);
        while (evenements.size() > config.historique()) {
            evenements.removeLast();
        }
        metriques.counter("supervision.transitions", "service", e.service(), "vers", e.vers().name()).increment();
        log.info("{} {} -> {} ({})", e.instance(), e.de(), e.vers(), detail);
    }

    private void publier() {
        var services = services();
        for (var s : services) {
            // Le décompte commence quand le service a été vu disponible une première fois :
            // le temps de démarrage de la pile n'est pas une panne
            if (!compteurs.containsKey(s.nom()) && !s.etat().disponible()) {
                continue;
            }
            var c = compteurs.computeIfAbsent(s.nom(), n -> new Compteur());
            c.total++;
            if (s.etat().disponible()) {
                c.disponibles++;
            }
        }
        jaugeServices.register(services.stream()
                .map(s -> MultiGauge.Row.of(Tags.of("service", s.nom()), s.instancesDisponibles())).toList(), true);
        jaugeInstances.register(services.stream().flatMap(s -> s.instances().stream())
                .map(i -> MultiGauge.Row.of(Tags.of("service", i.service(), "instance_id", i.id()),
                        i.sante().disponible() ? 1 : 0))
                .toList(), true);
    }

    public synchronized Vues.Etat etat() {
        var services = services();
        // Eureka indisponible ne coupe pas le trafic (copies locales du registre) : DEGRADE
        var global = services.stream().map(Vues.Service::etat).max(Comparator.naturalOrder())
                .orElse(EtatSante.INCONNU);
        if (global == EtatSante.UP && !eureka.disponible()) {
            global = EtatSante.DEGRADE;
        }
        return new Vues.Etat(global, horloge.instant(), eureka, services);
    }

    public synchronized List<Vues.Evenement> evenements(String service, int limite) {
        return evenements.stream().filter(e -> service == null || service.equals(e.service())).limit(limite).toList();
    }

    public synchronized Optional<InstanceDecouverte> instance(String id) {
        return Optional.ofNullable(suivis.get(id)).map(s -> s.instance);
    }

    private List<Vues.Service> services() {
        var noms = new TreeSet<>(config.servicesAttendus().keySet());
        suivis.values().forEach(s -> noms.add(s.instance.service()));
        var resultat = new ArrayList<Vues.Service>();
        for (var nom : noms) {
            var instances = suivis.values().stream().filter(s -> s.instance.service().equals(nom))
                    .map(Superviseur::vue).toList();
            int disponibles = (int) instances.stream().filter(i -> i.sante().disponible()).count();
            int minimum = config.servicesAttendus().getOrDefault(nom, 1);
            var c = compteurs.get(nom);
            double taux = c == null || c.total == 0 ? 0 : Math.round(10_000.0 * c.disponibles / c.total) / 100.0;
            resultat.add(new Vues.Service(nom, etatService(instances, disponibles, minimum), disponibles,
                    instances.size(), minimum, taux, instances));
        }
        return resultat;
    }

    /** Aucune instance saine : DOWN. Moins que le minimum ou une instance dégradée : DEGRADE. Sinon UP. */
    static EtatSante etatService(List<Vues.Instance> instances, int disponibles, int minimum) {
        if (disponibles == 0) {
            return EtatSante.DOWN;
        }
        boolean toutUp = instances.stream().filter(i -> i.sante().disponible())
                .allMatch(i -> i.sante() == EtatSante.UP);
        return disponibles >= minimum && toutUp ? EtatSante.UP : EtatSante.DEGRADE;
    }

    private static Vues.Instance vue(Suivi s) {
        boolean upPourEureka = "UP".equals(s.statutEureka);
        return new Vues.Instance(s.instance.id(), s.instance.service(), s.instance.urlBase(), s.statutEureka, s.sante,
                upPourEureka != s.sante.disponible(), s.dernier.latenceMs(), s.dernier.composants(),
                s.dernier.detail(), s.depuis, s.verifieeLe);
    }
}
