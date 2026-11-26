package sn.uadb.supervision.passerelle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"SUPERVISION_PASSWORD=sup", "ADMIN_PASSWORD=adm", "EUREKA_PASSWORD=x",
        "eureka.client.enabled=false"})
@AutoConfigureMockMvc
class PasserelleTests {

    @Autowired
    MockMvc mvc;

    @Test
    void laSanteEstPubliqueEtLesMetriquesProtegees() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void sansInstanceLaPasserelleRepond503() throws Exception {
        mvc.perform(get("/api/produits")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void unCheminNonRouteEst404() throws Exception {
        mvc.perform(get("/api/inconnu")).andExpect(status().isNotFound());
    }
}
