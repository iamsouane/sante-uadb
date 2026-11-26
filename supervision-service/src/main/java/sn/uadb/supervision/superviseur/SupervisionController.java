package sn.uadb.supervision.superviseur;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/supervision")
@Tag(name = "Supervision", description = "États UP/DOWN des services, croisés entre Eureka et Actuator")
public class SupervisionController {

    private final Superviseur superviseur;
    private final InjecteurPanne injecteur;

    public SupervisionController(Superviseur superviseur, InjecteurPanne injecteur) {
        this.superviseur = superviseur;
        this.injecteur = injecteur;
    }

    @GetMapping("/etat")
    @Operation(summary = "État global, par service et par instance")
    public Vues.Etat etat() {
        return superviseur.etat();
    }

    @GetMapping("/services/{nom}")
    @Operation(summary = "État d'un service")
    public Vues.Service service(@PathVariable String nom) {
        return superviseur.etat().services().stream().filter(s -> s.nom().equals(nom)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service inconnu"));
    }

    @GetMapping("/evenements")
    @Operation(summary = "Historique des changements d'état, du plus récent au plus ancien")
    public List<Vues.Evenement> evenements(@RequestParam(required = false) String service,
                                           @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limite) {
        return superviseur.evenements(service, limite);
    }

    @PutMapping("/instances/{id}/panne")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Injecte une panne sur une instance (rôle ADMIN, démonstration)")
    public void injecter(@PathVariable String id, @Valid @RequestBody Vues.DemandePanne demande) {
        injecteur.injecter(trouver(id), demande);
    }

    @DeleteMapping("/instances/{id}/panne")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Rétablit une instance (rôle ADMIN, démonstration)")
    public void retablir(@PathVariable String id) {
        injecteur.retablir(trouver(id));
    }

    /** L'identifiant doit correspondre à une instance connue : l'appelant ne fournit jamais d'URL (SSRF). */
    private InstanceDecouverte trouver(String id) {
        return superviseur.instance(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instance inconnue"));
    }
}
