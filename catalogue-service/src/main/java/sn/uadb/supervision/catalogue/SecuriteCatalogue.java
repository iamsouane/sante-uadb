package sn.uadb.supervision.catalogue;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import sn.uadb.supervision.commun.SecuriteConfig;

@Configuration(proxyBeanMethods = false)
class SecuriteCatalogue {

    @Bean
    @Order(2)
    SecurityFilterChain chaineMetier(HttpSecurity http) throws Exception {
        return SecuriteConfig.sansEtat(http)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/produits/*/reapprovisionnements").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .build();
    }
}
