package sn.uadb.supervision.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }

    /** Un registre ouvert permettrait d'enregistrer une fausse instance et de détourner le trafic. */
    @Bean
    SecurityFilterChain securite(HttpSecurity http) throws Exception {
        return http
                // Les clients Eureka n'envoient pas de jeton CSRF
                .csrf(c -> c.ignoringRequestMatchers("/eureka/**"))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(EndpointRequest.to("health")).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
