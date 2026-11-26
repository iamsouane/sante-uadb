package sn.uadb.supervision.superviseur;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"SUPERVISION_PASSWORD=sup", "ADMIN_PASSWORD=adm", "EUREKA_PASSWORD=x",
        "eureka.client.enabled=false", "supervision.intervalle=1h"})
@AutoConfigureMockMvc
class SupervisionApiTests {

    private static final String PANNE = "/api/supervision/instances/x/panne";

    @Autowired
    MockMvc mvc;

    @Test
    void anonymeRefuse() throws Exception {
        mvc.perform(get("/api/supervision/etat")).andExpect(status().isUnauthorized());
    }

    @Test
    void lectureAvecLeRoleSupervision() throws Exception {
        mvc.perform(get("/api/supervision/etat").header("Authorization", basic("supervision", "sup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(4));
        mvc.perform(get("/api/supervision/evenements?limite=0").header("Authorization", basic("supervision", "sup")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/supervision/services/inconnu").header("Authorization", basic("supervision", "sup")))
                .andExpect(status().isNotFound());
    }

    @Test
    void injecterUnePanneExigeAdmin() throws Exception {
        mvc.perform(put(PANNE).contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"DOWN\"}")
                        .header("Authorization", basic("supervision", "sup")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PANNE).header("Authorization", basic("supervision", "sup")))
                .andExpect(status().isForbidden());
    }

    @Test
    void instanceInconnueRefusee() throws Exception {
        mvc.perform(put(PANNE).contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"DOWN\"}")
                        .header("Authorization", basic("admin", "adm")))
                .andExpect(status().isNotFound());
    }

    @Test
    void demandeInvalideRefusee() throws Exception {
        mvc.perform(put(PANNE).contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"DOWN\",\"latenceMs\":99999}")
                        .header("Authorization", basic("admin", "adm")))
                .andExpect(status().isBadRequest());
    }

    private static String basic(String u, String p) {
        return "Basic " + Base64.getEncoder().encodeToString((u + ":" + p).getBytes());
    }
}
