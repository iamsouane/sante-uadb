package sn.uadb.supervision.commun;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecuriteConfig {

    /**
     * Coût 6 au lieu de 10 : ces comptes techniques sont vérifiés à chaque sonde, et le mot de
     * passe clair est de toute façon présent dans l'environnement du processus.
     */
    @Bean
    @ConditionalOnMissingBean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(6);
    }

    @Bean
    @ConditionalOnMissingBean
    UserDetailsService comptes(SecuriteProperties p, PasswordEncoder encodeur) {
        return new InMemoryUserDetailsManager(
                User.withUsername(p.utilisateur()).password(encodeur.encode(p.motDePasse()))
                        .roles("SUPERVISION").build(),
                User.withUsername(p.admin()).password(encodeur.encode(p.motDePasseAdmin()))
                        .roles("SUPERVISION", "ADMIN").build());
    }

    /** Chaîne 1 : tout /actuator. Seul /health est anonyme (et il masque alors ses détails). */
    @Bean
    @Order(1)
    SecurityFilterChain chaineActuator(HttpSecurity http) throws Exception {
        return sansEtat(http)
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(EndpointRequest.to("health")).permitAll()
                        .requestMatchers(HttpMethod.GET).hasRole("SUPERVISION")
                        .anyRequest().hasRole("ADMIN"))
                .build();
    }

    /** Chaîne 2 : API métier, ouverte par défaut ; un service peut la redéfinir. */
    @Bean
    @Order(2)
    @ConditionalOnMissingBean(name = "chaineMetier")
    SecurityFilterChain chaineMetier(HttpSecurity http) throws Exception {
        return sansEtat(http).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }

    /**
     * CSRF désactivé car l'API est sans session ni cookie. Le point d'entrée répond 401 sans
     * WWW-Authenticate : le navigateur ne mémorise donc pas les identifiants Basic.
     */
    public static HttpSecurity sansEtat(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
    }
}
