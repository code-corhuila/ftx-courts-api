package com.futbolix.courts;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.futbolix.courts.adapter.in.web.AdministratorTokenVerifier;
import com.futbolix.courts.adapter.in.web.TestTokens;
import com.futbolix.courts.adapter.out.persistence.JdbcCourtRepositoryIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole application (configuration, wiring, transaction, JDBC adapter, web layer) against the
 * real courts schema, connected as courts_app. Reuses the database prepared by the integration
 * test; skipped under the same conditions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourtsApiApplicationTest {

    static final String COURT_4 = "c0000000-0000-4000-8000-000000000004";

    @Autowired MockMvc mvc;

    /** Trusts the key of TestTokens instead of fetching the JWKS of ftx-customers-api. */
    @TestBean AdministratorTokenVerifier administratorTokenVerifier;

    static AdministratorTokenVerifier administratorTokenVerifier() {
        return TestTokens.verifier();
    }

    @BeforeAll
    static void database() {
        JdbcCourtRepositoryIntegrationTest.migrateRealSchema();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JdbcCourtRepositoryIntegrationTest.url);
        registry.add("spring.datasource.username", () -> "courts_app");
        registry.add("spring.datasource.password", () -> JdbcCourtRepositoryIntegrationTest.appPassword);
    }

    @Test
    void listsTheFourCourtsFromTheDatabase() throws Exception {
        mvc.perform(get("/api/v1/courts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].name").value("Cancha 1"));
    }

    @Test
    void anAdministratorChangeIsStoredAndAForbiddenOneIsNot() throws Exception {
        mvc.perform(patch("/api/v1/admin/courts/" + COURT_4).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-1"))
                        .content("{\"hourlyPrice\": {\"amount\": 95000, \"currency\": \"COP\"}, \"status\": \"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hourlyPrice.amount").value(95000))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mvc.perform(patch("/api/v1/admin/courts/" + COURT_4).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-1"))
                        .content("{\"hourlyPrice\": {\"amount\": 1000, \"currency\": \"COP\"}, \"status\": \"UNDER_MAINTENANCE\"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/v1/courts/" + COURT_4))
                .andExpect(jsonPath("$.hourlyPrice.amount").value(95000))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void healthReportsTheDatabase() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
