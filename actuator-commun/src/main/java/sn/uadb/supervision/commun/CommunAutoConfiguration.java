package sn.uadb.supervision.commun;

import com.netflix.appinfo.HealthCheckHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Value;

/**
 * Socle de supervision partagé : sécurité d'Actuator, traduction des statuts vers Eureka
 * et, pour la démonstration, injection de panne.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"})
@EnableConfigurationProperties(SecuriteProperties.class)
@Import(SecuriteConfig.class)
public class CommunAutoConfiguration {

    @Bean
    @ConditionalOnProperty("eureka.client.healthcheck.enabled")
    HealthCheckHandler healthCheckHandler(StatusAggregator aggregateur) {
        return new DegradeHealthCheckHandler(aggregateur);
    }

    @Bean
    @ConditionalOnProperty(name = "supervision.entete-instance", matchIfMissing = true)
    InstanceHeaderFilter instanceHeaderFilter(@Value("${eureka.instance.instance-id:inconnue}") String id) {
        return new InstanceHeaderFilter(id);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty("supervision.panne.active")
    static class PanneConfig {

        @Bean
        PanneSimulee panneSimulee() {
            return new PanneSimulee();
        }

        @Bean
        PanneSimuleeHealthIndicator panneSimuleeHealthIndicator(PanneSimulee panne) {
            return new PanneSimuleeHealthIndicator(panne);
        }

        @Bean
        PanneEndpoint panneEndpoint(PanneSimulee panne) {
            return new PanneEndpoint(panne);
        }
    }
}
