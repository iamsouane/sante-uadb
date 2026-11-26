package sn.uadb.supervision.superviseur;

import java.net.http.HttpClient;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Relaie l'injection de panne vers /actuator/panne de l'instance visée. */
@Component
public class InjecteurPanne {

    private final RestClient client;

    public InjecteurPanne(SupervisionProperties p,
                          @Value("${supervision.securite.admin}") String admin,
                          @Value("${supervision.securite.mot-de-passe-admin}") String motDePasse) {
        var fabrique = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(p.delaiConnexion()).build());
        fabrique.setReadTimeout(p.delaiReponse());
        this.client = RestClient.builder().requestFactory(fabrique)
                .defaultHeaders(h -> h.setBasicAuth(admin, motDePasse)).build();
    }

    public void injecter(InstanceDecouverte instance, Vues.DemandePanne demande) {
        var corps = new HashMap<String, Object>();
        corps.put("statut", demande.statut());
        if (demande.raison() != null) {
            corps.put("raison", demande.raison());
        }
        if (demande.latenceMs() != null) {
            corps.put("latenceMs", demande.latenceMs());
        }
        appeler(() -> client.post().uri(instance.urlBase() + "/actuator/panne").body(corps)
                .retrieve().toBodilessEntity());
    }

    public void retablir(InstanceDecouverte instance) {
        appeler(() -> client.delete().uri(instance.urlBase() + "/actuator/panne").retrieve().toBodilessEntity());
    }

    private static void appeler(Runnable appel) {
        try {
            appel.run();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "L'instance a refusé la commande (injection désactivée ou instance injoignable)");
        }
    }
}
