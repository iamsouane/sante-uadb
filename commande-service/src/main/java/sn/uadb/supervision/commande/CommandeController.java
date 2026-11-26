package sn.uadb.supervision.commande;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/commandes")
public class CommandeController {

    public record Demande(@Min(1) long produitId, @Min(1) @Max(1000) int quantite) {
    }

    private final CommandeRepository commandes;
    private final CatalogueClient catalogue;

    public CommandeController(CommandeRepository commandes, CatalogueClient catalogue) {
        this.commandes = commandes;
        this.catalogue = catalogue;
    }

    @GetMapping
    public List<Commande> lister() {
        return commandes.findTop50ByOrderByIdDesc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Commande creer(@Valid @RequestBody Demande demande) {
        var produit = reserver(demande);
        return commandes.save(new Commande(produit.id(), demande.quantite(),
                produit.prix().multiply(BigDecimal.valueOf(demande.quantite()))));
    }

    private CatalogueClient.ProduitReserve reserver(Demande demande) {
        try {
            return catalogue.reserver(demande.produitId(), demande.quantite());
        } catch (HttpClientErrorException e) {
            // 404 produit inconnu, 409 stock insuffisant : erreurs du client, on les relaie
            throw new ResponseStatusException(e.getStatusCode(), "Réservation refusée par le catalogue");
        } catch (RestClientException | IllegalStateException e) {
            // Aucune instance, connexion refusée, délai dépassé, 5xx
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalogue indisponible");
        }
    }
}
