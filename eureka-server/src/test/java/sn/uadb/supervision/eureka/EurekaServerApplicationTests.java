package sn.uadb.supervision.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "EUREKA_PASSWORD=secret-test")
class EurekaServerApplicationTests {

    @LocalServerPort
    int port;

    @Test
    void laSanteEstPublique() throws Exception {
        assertThat(statut("/actuator/health", null)).isEqualTo(200);
    }

    @Test
    void leRegistreExigeUneAuthentification() throws Exception {
        assertThat(statut("/eureka/apps", null)).isEqualTo(401);
        assertThat(statut("/eureka/apps", "eureka:mauvais")).isEqualTo(401);
        assertThat(statut("/eureka/apps", "eureka:secret-test")).isEqualTo(200);
    }

    @Test
    void lesMetriquesNeSontPasPubliques() throws Exception {
        assertThat(statut("/actuator/prometheus", null)).isEqualTo(401);
    }

    private int statut(String chemin, String identifiants) throws Exception {
        var requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin));
        if (identifiants != null) {
            requete.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(identifiants.getBytes()));
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(requete.build(), BodyHandlers.ofString()).statusCode();
        }
    }
}
