package sn.uadb.supervision.commun;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.SimpleStatusAggregator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.cloud.netflix.eureka.EurekaHealthCheckHandler;

class StatutsTests {

    private final SimpleStatusAggregator aggregateur =
            new SimpleStatusAggregator("DOWN", "OUT_OF_SERVICE", "DEGRADE", "UP", "UNKNOWN");

    @Test
    void lePireStatutLEmporte() {
        assertThat(aggregateur.getAggregateStatus(Status.UP, Statuts.DEGRADE, Status.DOWN)).isEqualTo(Status.DOWN);
        assertThat(aggregateur.getAggregateStatus(Status.UP, Statuts.DEGRADE)).isEqualTo(Statuts.DEGRADE);
        assertThat(aggregateur.getAggregateStatus(Status.UP, Status.UNKNOWN)).isEqualTo(Status.UP);
    }

    @Test
    void sansNotreClasseEurekaTraduitDegradeEnUnknown() {
        assertThat(new EurekaHealthCheckHandler(aggregateur) {
            InstanceStatus traduire(Status s) {
                return mapToInstanceStatus(s);
            }
        }.traduire(Statuts.DEGRADE)).isEqualTo(InstanceStatus.UNKNOWN);
    }

    @Test
    void avecNotreClasseDegradeResteRoutable() {
        var handler = new DegradeHealthCheckHandler(aggregateur);
        assertThat(handler.mapToInstanceStatus(Statuts.DEGRADE)).isEqualTo(InstanceStatus.UP);
        assertThat(handler.mapToInstanceStatus(Status.DOWN)).isEqualTo(InstanceStatus.DOWN);
        // Choix de Spring Cloud : côté registre, LeaseExistsRule conserve un bail OUT_OF_SERVICE,
        // l'instance ne repasserait donc pas à UP d'elle-même. DOWN est réversible.
        assertThat(handler.mapToInstanceStatus(Status.OUT_OF_SERVICE)).isEqualTo(InstanceStatus.DOWN);
        assertThat(handler.mapToInstanceStatus(Status.UP)).isEqualTo(InstanceStatus.UP);
    }
}
