package com.parth.mealtrack.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parth.mealtrack.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CentreApiTest extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    private static String json(String code, String type, String district, int meals) {
        return """
                {"code":"%s","name":"Test centre","type":"%s","district":"%s","block":"B1","expectedMeals":%d}
                """.formatted(code, type, district, meals);
    }

    private void create(String code, String district, int meals) throws Exception {
        mvc.perform(post("/api/centres").header("X-API-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                .content(json(code, "SCHOOL", district, meals))).andExpect(status().isCreated());
    }

    @Test
    void createsAndFetchesCentre() throws Exception {
        var res = mvc.perform(post("/api/centres").header("X-API-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(json("T-0001", "ANGANWADI", "Rohtak", 40)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("T-0001"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id").toString();
        mvc.perform(get("/api/centres/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedMeals").value(40));
    }

    @Test
    void duplicateCodeIsConflict() throws Exception {
        create("T-0002", "Rohtak", 100);
        mvc.perform(post("/api/centres").header("X-API-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(json("T-0002", "SCHOOL", "Rohtak", 100)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/api/centres"));
    }

    @Test
    void invalidBodyIsBadRequestWithConsistentShape() throws Exception {
        mvc.perform(post("/api/centres").header("X-API-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(json("", "SCHOOL", "Rohtak", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unknownEnumValueIsBadRequest() throws Exception {
        mvc.perform(post("/api/centres").header("X-API-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(json("T-0003", "FACTORY", "Rohtak", 10)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCentreIsNotFoundWithoutStackTrace() throws Exception {
        mvc.perform(get("/api/centres/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Centre not found: 999999"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void listFiltersByDistrictAndPages() throws Exception {
        create("D-0001", "Sonipat-Test", 100);
        create("D-0002", "Sonipat-Test", 100);
        create("D-0003", "Other-Test", 100);
        mvc.perform(get("/api/centres").param("district", "Sonipat-Test").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void listFiltersByActive() throws Exception {
        create("A-0001", "Active-Test", 100);
        mvc.perform(get("/api/centres").param("district", "Active-Test").param("active", "false"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
