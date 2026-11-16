package com.futbolix.courts.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.futbolix.courts.application.port.in.GetCourt;
import com.futbolix.courts.application.port.in.ListCourts;
import com.futbolix.courts.application.port.in.UpdateCourt;
import com.nimbusds.jose.KeySourceException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** ftx-customers-api is down: administrator requests get 503, public reads keep working. */
@WebMvcTest(controllers = {CourtController.class, AdminCourtController.class})
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class, AdministratorInterceptor.class, WebConfiguration.class,
        AdminCourtKeysUnavailableTest.KeysDown.class})
class AdminCourtKeysUnavailableTest {

    @TestConfiguration
    static class KeysDown {
        @Bean
        AdministratorTokenVerifier administratorTokenVerifier() {
            return new AdministratorTokenVerifier((selector, context) -> {
                throw new KeySourceException("connection refused");
            }, TestTokens.ISSUER);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean ListCourts listCourts;
    @MockitoBean GetCourt getCourt;
    @MockitoBean UpdateCourt updateCourt;

    @Test
    void administratorRequestsAre503AndNothingChanges() throws Exception {
        mvc.perform(patch(AdminCourtControllerTest.URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INACTIVE\"}")
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
        verify(updateCourt, never()).updateCourt(any());
    }

    @Test
    void publicReadsDoNotNeedTheKeys() throws Exception {
        Mockito.when(listCourts.listCourts()).thenReturn(List.of(CourtControllerTest.court()));
        mvc.perform(get("/api/v1/courts")).andExpect(status().isOk());
    }
}
