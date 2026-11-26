package sn.uadb.supervision.commun;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Indique quelle instance a répondu : rend visible la répartition de charge. */
public class InstanceHeaderFilter extends OncePerRequestFilter {

    private final String instanceId;

    public InstanceHeaderFilter(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        reponse.setHeader("X-Instance", instanceId);
        suite.doFilter(requete, reponse);
    }
}
