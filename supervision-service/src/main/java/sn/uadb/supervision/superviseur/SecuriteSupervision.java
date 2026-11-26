package sn.uadb.supervision.superviseur;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import sn.uadb.supervision.commun.SecuriteConfig;

@Configuration(proxyBeanMethods = false)
class SecuriteSupervision {

    @Bean
    @Order(2)
    SecurityFilterChain chaineMetier(HttpSecurity http) throws Exception {
        return SecuriteConfig.sansEtat(http)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/supervision/**").hasRole("SUPERVISION")
                        .requestMatchers("/api/supervision/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    OpenAPI contrat() {
        return new OpenAPI()
                .info(new Info().title("API de supervision").version("1.0.0")
                        .description("Binôme 3 - Supervision et endpoint /health"))
                .components(new Components().addSecuritySchemes("basic",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basic"));
    }
}
