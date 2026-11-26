package sn.uadb.supervision.commande;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@SpringBootTest(properties = {"SUPERVISION_PASSWORD=sup", "ADMIN_PASSWORD=adm", "EUREKA_PASSWORD=x",
        "eureka.client.enabled=false"})
@AutoConfigureMockMvc
class CommandeTests {

    private static final String DEMANDE = "{\"produitId\":1,\"quantite\":2}";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    CatalogueClient catalogue;
    @MockitoBean(name = "compositeDiscoveryClient")
    DiscoveryClient annuaire;

    @Test
    void catalogueAbsentDegradeSansMettreDown() throws Exception {
        when(annuaire.getInstances("catalogue-service")).thenReturn(List.of());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DEGRADE")))
                .andExpect(jsonPath("$.components").doesNotExist());
        // La préparation au trafic n'est pas affectée : pas de panne en cascade
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void cataloguePresentDonneUp() throws Exception {
        when(annuaire.getInstances("catalogue-service")).thenReturn(List.of(mock(ServiceInstance.class)));
        mvc.perform(get("/actuator/health")).andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void creeUneCommande() throws Exception {
        when(catalogue.reserver(1, 2))
                .thenReturn(new CatalogueClient.ProduitReserve(1, "Riz", new BigDecimal("11500"), 38));
        mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON).content(DEMANDE))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(23000));
    }

    @Test
    void catalogueInjoignableDonne503() throws Exception {
        when(catalogue.reserver(anyLong(), anyInt())).thenThrow(new ResourceAccessException("refus"));
        mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON).content(DEMANDE))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void stockInsuffisantEstRelaye() throws Exception {
        when(catalogue.reserver(anyLong(), anyInt()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "", null, null, null));
        mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON).content(DEMANDE))
                .andExpect(status().isConflict());
    }

    @Test
    void quantiteInvalideRefusee() throws Exception {
        mvc.perform(post("/api/commandes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"produitId\":1,\"quantite\":0}")).andExpect(status().isBadRequest());
    }

    @Test
    void sansOptInLInjectionDePanneNExistePas() throws Exception {
        mvc.perform(get("/actuator/panne").header("Authorization", "Basic YWRtaW46YWRt"))
                .andExpect(status().isNotFound());
    }
}
