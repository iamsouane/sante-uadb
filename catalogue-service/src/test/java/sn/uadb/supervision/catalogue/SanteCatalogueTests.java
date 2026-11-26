package sn.uadb.supervision.catalogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import sn.uadb.supervision.commun.PanneSimulee;

@SpringBootTest(properties = {"SUPERVISION_PASSWORD=sup", "ADMIN_PASSWORD=adm", "EUREKA_PASSWORD=x",
        "eureka.client.enabled=false", "PANNE_ACTIVE=true"})
@AutoConfigureMockMvc
class SanteCatalogueTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    PanneSimulee panne;
    @Autowired
    JdbcTemplate sql;

    @AfterEach
    void retablir() {
        panne.retablir();
        sql.update("update produit set stock = 3 where id = 4");
    }

    @Test
    void anonymeVoitLeStatutSansLesDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void leRoleSupervisionVoitLesComposants() throws Exception {
        mvc.perform(get("/actuator/health").header("Authorization", sup()))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.stock.details.produitsEnRupture").value(0));
    }

    @Test
    void downRepond503EtLaVivaciteResteUp() throws Exception {
        panne.declencher(PanneSimulee.Statut.DOWN, "test", 0);
        mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void horsServiceRepond503() throws Exception {
        panne.declencher(PanneSimulee.Statut.OUT_OF_SERVICE, "maintenance", 0);
        mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
    }

    @Test
    void ruptureDeStockDegradeSansCouper() throws Exception {
        mvc.perform(post("/api/produits/4/reservations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantite\":3}")).andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(0));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADE"));
        mvc.perform(post("/api/produits/4/reservations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantite\":1}")).andExpect(status().isConflict());
    }

    @Test
    void lesEndpointsSensiblesSontProtegesOuFermes() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").header("Authorization", basic("admin", "adm")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/heapdump").header("Authorization", basic("admin", "adm")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/actuator/panne").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"DOWN\"}")
                .header("Authorization", sup())).andExpect(status().isForbidden());
        mvc.perform(delete("/actuator/panne").header("Authorization", sup())).andExpect(status().isForbidden());
        mvc.perform(post("/api/produits/4/reapprovisionnements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantite\":1}")).andExpect(status().isUnauthorized());
    }

    @Test
    void ladminInjecteUnePanneParHttp() throws Exception {
        mvc.perform(post("/actuator/panne").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"DOWN\",\"raison\":\"demo\"}")
                        .header("Authorization", basic("admin", "adm")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("DOWN"));
        mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void lApiListeLesProduitsEtNommeLInstance() throws Exception {
        mvc.perform(get("/api/produits")).andExpect(status().isOk()).andExpect(header().exists("X-Instance"))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/produits/999")).andExpect(status().isNotFound());
    }

    private static String sup() {
        return basic("supervision", "sup");
    }

    private static String basic(String u, String p) {
        return "Basic " + Base64.getEncoder().encodeToString((u + ":" + p).getBytes());
    }
}
