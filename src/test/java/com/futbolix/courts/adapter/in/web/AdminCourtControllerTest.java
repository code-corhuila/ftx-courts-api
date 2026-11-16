package com.futbolix.courts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.futbolix.courts.application.CourtNameTakenException;
import com.futbolix.courts.application.port.in.GetCourt;
import com.futbolix.courts.application.port.in.ListCourts;
import com.futbolix.courts.application.port.in.UpdateCourt;
import com.futbolix.courts.application.port.in.UpdateCourtCommand;
import com.futbolix.courts.domain.CourtStatus;
import com.futbolix.courts.domain.InvalidCourtStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = {CourtController.class, AdminCourtController.class})
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class, AdministratorInterceptor.class, WebConfiguration.class,
        TestVerifierConfiguration.class})
class AdminCourtControllerTest {

    static final String URL = "/api/v1/admin/courts/" + CourtControllerTest.COURT_1;

    @Autowired MockMvc mvc;
    @MockitoBean ListCourts listCourts;
    @MockitoBean GetCourt getCourt;
    @MockitoBean UpdateCourt updateCourt;

    static final String ADMIN_ID = "6f1c2b7e-0000-4000-8000-000000000007";

    static MockHttpServletRequestBuilder asAdministrator(String body) {
        return patch(URL).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", "Bearer " + TestTokens.administrator(ADMIN_ID));
    }

    static MockHttpServletRequestBuilder withToken(String token) {
        return patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"INACTIVE\"}")
                .header("Authorization", "Bearer " + token);
    }

    @Test
    void anAdministratorChangesPriceAndStatus() throws Exception {
        when(updateCourt.updateCourt(any())).thenReturn(CourtControllerTest.court());

        mvc.perform(asAdministrator("""
                        {"hourlyPrice": {"amount": 70000, "currency": "COP"}, "status": "UNDER_MAINTENANCE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CourtControllerTest.COURT_1.toString()));

        ArgumentCaptor<UpdateCourtCommand> sent = ArgumentCaptor.forClass(UpdateCourtCommand.class);
        verify(updateCourt).updateCourt(sent.capture());
        assertThat(sent.getValue().administratorId()).isEqualTo(ADMIN_ID);
        assertThat(sent.getValue().hourlyPrice()).get().extracting(m -> m.amount()).isEqualTo(70000L);
        assertThat(sent.getValue().status()).contains(CourtStatus.UNDER_MAINTENANCE);
        assertThat(sent.getValue().name()).isEmpty();
    }

    // M1: same request, two identities. Only the administrator gets through.
    @Test
    void anotherRoleIsForbiddenAndNothingChanges() throws Exception {
        mvc.perform(withToken(TestTokens.withRole("user-2", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        verify(updateCourt, never()).updateCourt(any());
    }

    @Test
    void withoutIdentityItIs401() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"INACTIVE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verify(updateCourt, never()).updateCourt(any());
    }

    /**
     * Regression: the API used to trust X-User-Id / X-User-Role, and its port is published on the
     * host, so anyone could change a price by sending those two headers.
     */
    @Test
    void spoofedIdentityHeadersAreIgnored() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"hourlyPrice\": {\"amount\": 1000, \"currency\": \"COP\"}}")
                        .header("X-User-Id", "attacker").header("X-User-Role", "ADMINISTRATOR"))
                .andExpect(status().isUnauthorized());
        // A real customer-role token plus a spoofed administrator header is still forbidden.
        mvc.perform(withToken(TestTokens.withRole("user-2", "CUSTOMER")).header("X-User-Role", "ADMINISTRATOR"))
                .andExpect(status().isForbidden());
        verify(updateCourt, never()).updateCourt(any());
    }

    @Test
    void expiredForeignOrForgedTokensAre401() throws Exception {
        for (String token : new String[] {
                TestTokens.expired(ADMIN_ID),
                TestTokens.fromIssuer(ADMIN_ID, "someone-else"),
                TestTokens.signedByAnotherKey(ADMIN_ID),
                "not-a-jwt"}) {
            mvc.perform(withToken(token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"INACTIVE\"}")
                        .header("Authorization", "Basic YWRtaW46YWRtaW4="))
                .andExpect(status().isUnauthorized());
        verify(updateCourt, never()).updateCourt(any());
    }

    @Test
    void unknownFieldsAreRejected() throws Exception {
        mvc.perform(asAdministrator("{\"color\": \"green\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("color"));
    }

    @Test
    void anUnknownStatusIsRejected() throws Exception {
        mvc.perform(asAdministrator("{\"status\": \"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    void aPriceInAnotherCurrencyIsRejected() throws Exception {
        mvc.perform(asAdministrator("{\"hourlyPrice\": {\"amount\": 70000, \"currency\": \"USD\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("hourlyPrice"));
        verify(updateCourt, never()).updateCourt(any());
    }

    @Test
    void aForbiddenTransitionIs422() throws Exception {
        when(updateCourt.updateCourt(any()))
                .thenThrow(new InvalidCourtStatusTransitionException(CourtStatus.INACTIVE, CourtStatus.UNDER_MAINTENANCE));
        mvc.perform(asAdministrator("{\"status\": \"UNDER_MAINTENANCE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_COURT_STATUS_TRANSITION"));
    }

    @Test
    void aDuplicateNameIs409() throws Exception {
        when(updateCourt.updateCourt(any())).thenThrow(new CourtNameTakenException("Cancha 2"));
        mvc.perform(asAdministrator("{\"name\": \"Cancha 2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }
}
