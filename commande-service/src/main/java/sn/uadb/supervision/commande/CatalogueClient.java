package sn.uadb.supervision.commande;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Client du catalogue : l'hôte « catalogue-service » est résolu par Eureka. */
@Component
public class CatalogueClient {

    static final String SERVICE = "catalogue-service";

    public record ProduitReserve(long id, String nom, BigDecimal prix, int stock) {
    }

    private final RestClient client;

    public CatalogueClient(LoadBalancerInterceptor equilibreur) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        var fabrique = new JdkClientHttpRequestFactory(http);
        fabrique.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder()
                .baseUrl("http://" + SERVICE)
                .requestFactory(fabrique)
                .requestInterceptor(equilibreur)
                .build();
    }

    public ProduitReserve reserver(long produitId, int quantite) {
        return client.post()
                .uri("/api/produits/{id}/reservations", produitId)
                .body(Map.of("quantite", quantite))
                .retrieve()
                .body(ProduitReserve.class);
    }
}
