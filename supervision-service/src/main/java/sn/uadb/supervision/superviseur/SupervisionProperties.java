package sn.uadb.supervision.superviseur;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("supervision")
public record SupervisionProperties(
        String eurekaSanteUrl,
        Duration intervalle,
        Duration delaiConnexion,
        Duration delaiReponse,
        int seuilEchecs,
        Duration conservation,
        int historique,
        Map<String, Integer> servicesAttendus) {
}
