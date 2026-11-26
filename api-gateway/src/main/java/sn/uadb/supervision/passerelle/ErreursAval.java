package sn.uadb.supervision.passerelle;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Sans cela, « aucune instance UP » ou « instance injoignable » remontaient en erreur 500.
 * 503 dit la vérité : le service est momentanément indisponible.
 */
@Configuration(proxyBeanMethods = false)
class ErreursAval {

    private static final Logger log = LoggerFactory.getLogger(ErreursAval.class);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    HandlerExceptionResolver serviceIndisponible() {
        return (requete, reponse, gestionnaire, e) -> {
            if (!(e instanceof HttpServerErrorException || e instanceof ResourceAccessException
                    || e instanceof IOException)) {
                return null;
            }
            log.warn("{} {} : {}", requete.getMethod(), requete.getRequestURI(), e.getMessage());
            try {
                reponse.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Service indisponible");
            } catch (IOException ignoree) {
                log.debug("Client déconnecté", ignoree);
            }
            return new ModelAndView();
        };
    }
}
