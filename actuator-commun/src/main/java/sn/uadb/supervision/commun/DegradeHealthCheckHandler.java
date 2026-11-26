package sn.uadb.supervision.commun;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.cloud.netflix.eureka.EurekaHealthCheckHandler;

/**
 * Traduit le statut Actuator en statut Eureka.
 *
 * <p>La classe de base ne connaît que UP, DOWN, OUT_OF_SERVICE et UNKNOWN : tout statut
 * personnalisé devient UNKNOWN, et une instance UNKNOWN n'est plus routée. Sans cette classe,
 * une simple rupture de stock (DEGRADE) sortirait donc le catalogue du trafic.
 */
public class DegradeHealthCheckHandler extends EurekaHealthCheckHandler {

    public DegradeHealthCheckHandler(StatusAggregator aggregateur) {
        super(aggregateur);
    }

    @Override
    protected InstanceStatus mapToInstanceStatus(Status statut) {
        return Statuts.DEGRADE.equals(statut) ? InstanceStatus.UP : super.mapToInstanceStatus(statut);
    }
}
