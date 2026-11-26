package sn.uadb.supervision.superviseur;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Lit la copie locale du registre. Si Eureka tombe, cette copie reste disponible : la
 * supervision continue de sonder les instances connues.
 */
@Component
class AnnuaireEureka implements Annuaire {

    private final ObjectProvider<EurekaClient> eureka;

    AnnuaireEureka(ObjectProvider<EurekaClient> eureka) {
        this.eureka = eureka;
    }

    @Override
    public List<InstanceDecouverte> instances() {
        var client = eureka.getIfAvailable();
        if (client == null) {
            return List.of();
        }
        return client.getApplications().getRegisteredApplications().stream()
                .flatMap(application -> application.getInstancesAsIsFromEureka().stream())
                .map(AnnuaireEureka::convertir)
                .toList();
    }

    private static InstanceDecouverte convertir(InstanceInfo i) {
        var base = "http://" + i.getHostName() + ":" + i.getPort();
        return new InstanceDecouverte(i.getAppName().toLowerCase(Locale.ROOT), i.getInstanceId(), base,
                base + "/actuator/health", i.getStatus().name());
    }
}
