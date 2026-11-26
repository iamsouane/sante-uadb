package sn.uadb.supervision.superviseur;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Interroge /actuator/health d'une instance. */
@Component
public class Sonde {

    public record Resultat(EtatSante sante, long latenceMs, Map<String, String> composants, String detail) {

        static Resultat injoignable(long latenceMs, String cause) {
            return new Resultat(EtatSante.INJOIGNABLE, latenceMs, Map.of(), cause);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReponseSante(String status, Map<String, Composant> components) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Composant(String status) {
    }

    private final RestClient client;
    private final RestClient clientAuthentifie;

    public Sonde(SupervisionProperties p,
                 @Value("${supervision.securite.utilisateur}") String utilisateur,
                 @Value("${supervision.securite.mot-de-passe}") String motDePasse) {
        var fabrique = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(p.delaiConnexion()).build());
        fabrique.setReadTimeout(p.delaiReponse());
        this.client = RestClient.builder()
                .requestFactory(fabrique)
                .build();
        this.clientAuthentifie = client.mutate()
                .defaultHeaders(h -> h.setBasicAuth(utilisateur, motDePasse))
                .build();
    }

    /** Sonde authentifiée : renvoie aussi le statut de chaque composant. */
    public Resultat interroger(String url) {
        return interroger(clientAuthentifie, url);
    }

    /** Sonde anonyme : pour Eureka, qui a son propre compte et répondrait 401 à celui des services. */
    public Resultat interrogerAnonyme(String url) {
        return interroger(client, url);
    }

    private static Resultat interroger(RestClient client, String url) {
        long debut = System.nanoTime();
        try {
            // exchange() et non retrieve() : un 503 est une réponse normale (état DOWN), pas une erreur
            var reponse = client.get().uri(url).exchange((requete, r) -> r.bodyTo(ReponseSante.class));
            return convertir(reponse, ecoule(debut));
        } catch (RuntimeException e) {
            // Large volontairement : une instance mal configurée ne doit pas interrompre tout le cycle
            var cause = e.getCause() == null ? e : e.getCause();
            return Resultat.injoignable(ecoule(debut), cause.getClass().getSimpleName());
        }
    }

    private static Resultat convertir(ReponseSante reponse, long latenceMs) {
        if (reponse == null || reponse.status() == null) {
            return new Resultat(EtatSante.INCONNU, latenceMs, Map.of(), "réponse illisible");
        }
        var composants = new LinkedHashMap<String, String>();
        if (reponse.components() != null) {
            reponse.components().forEach((nom, c) -> composants.put(nom, c.status()));
        }
        var fautifs = composants.entrySet().stream()
                .filter(e -> !"UP".equals(e.getValue()) && !"UNKNOWN".equals(e.getValue()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
        return new Resultat(EtatSante.depuisActuator(reponse.status()), latenceMs, composants,
                String.join(", ", fautifs));
    }

    private static long ecoule(long debut) {
        return (System.nanoTime() - debut) / 1_000_000;
    }
}
