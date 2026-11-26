package sn.uadb.supervision.superviseur;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La sonde est testée contre un vrai serveur HTTP : codes 503, délais, JSON invalide. */
class SondeTests {

    private HttpServer serveur;
    private String url;
    private volatile String autorisation;

    private final Sonde sonde = new Sonde(new SupervisionProperties("", Duration.ofSeconds(5), Duration.ofMillis(300),
            Duration.ofMillis(300), 2, Duration.ofMinutes(3), 10, Map.of()), "supervision", "secret");

    @BeforeEach
    void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        serveur.start();
        url = "http://localhost:" + serveur.getAddress().getPort() + "/actuator/health";
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    @Test
    void lit200Up() {
        repondre(200, "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\",\"details\":{\"x\":1}}}}", 0);
        var r = sonde.interroger(url);
        assertThat(r.sante()).isEqualTo(EtatSante.UP);
        assertThat(r.composants()).containsEntry("db", "UP");
        assertThat(autorisation).startsWith("Basic ");
    }

    @Test
    void un503EstUnEtatDownEtNonUneErreur() {
        repondre(503, "{\"status\":\"DOWN\",\"components\":{\"db\":{\"status\":\"DOWN\"},\"ping\":{\"status\":\"UP\"}}}", 0);
        var r = sonde.interroger(url);
        assertThat(r.sante()).isEqualTo(EtatSante.DOWN);
        assertThat(r.detail()).isEqualTo("db=DOWN");
    }

    @Test
    void reconnaitDegradeEtLesStatutsInconnus() {
        repondre(200, "{\"status\":\"DEGRADE\"}", 0);
        assertThat(sonde.interroger(url).sante()).isEqualTo(EtatSante.DEGRADE);
        assertThat(EtatSante.depuisActuator("BIZARRE")).isEqualTo(EtatSante.INCONNU);
    }

    @Test
    void laSondeAnonymeNEnvoieAucunIdentifiant() {
        repondre(200, "{\"status\":\"UP\"}", 0);
        assertThat(sonde.interrogerAnonyme(url).sante()).isEqualTo(EtatSante.UP);
        assertThat(autorisation).isNull();
    }

    @Test
    void connexionRefuseeDonneInjoignable() {
        serveur.stop(0);
        assertThat(sonde.interroger(url).sante()).isEqualTo(EtatSante.INJOIGNABLE);
    }

    @Test
    void reponseTropLenteDonneInjoignable() {
        repondre(200, "{\"status\":\"UP\"}", 900);
        var r = sonde.interroger(url);
        assertThat(r.sante()).isEqualTo(EtatSante.INJOIGNABLE);
        assertThat(r.latenceMs()).isLessThan(800);
    }

    @Test
    void jsonInvalideNInterromptPasLeCycle() {
        repondre(200, "<html>erreur</html>", 0);
        assertThat(sonde.interroger(url).sante().disponible()).isFalse();
    }

    private void repondre(int code, String corps, int attenteMs) {
        serveur.createContext("/actuator/health", e -> {
            autorisation = e.getRequestHeaders().getFirst("Authorization");
            try {
                Thread.sleep(attenteMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            var octets = corps.getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().add("Content-Type", "application/json");
            e.sendResponseHeaders(code, octets.length);
            e.getResponseBody().write(octets);
            e.close();
        });
    }
}
