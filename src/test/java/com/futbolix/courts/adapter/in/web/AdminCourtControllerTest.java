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
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class, AdministratorInterceptor.class, WebConfiguration.class})
class AdminCourtControllerTest {

    static final String URL = "/api/v1/admin/courts/" + CourtControllerTest.COURT_1;

    @Autowired MockMvc mvc;
    @MockitoBean ListCourts listCourts;
    @MockitoBean GetCourt getCourt;
    @MockitoBean UpdateCourt updateCourt;

    static MockHttpServletRequestBuilder asAdministrator(String body) {
        return patch(URL).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-User-Id", "admin-7").header("X-User-Role", "ADMINISTRATOR");
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
        assertThat(sent.getValue().administratorId()).isEqualTo("admin-7");
        assertThat(sent.getValue().hourlyPrice()).get().extracting(m -> m.amount()).isEqualTo(70000L);
        assertThat(sent.getValue().status()).contains(CourtStatus.UNDER_MAINTENANCE);
        assertThat(sent.getValue().name()).isEmpty();
    }

    // M1: same request, two identities. Only the administrator gets through.
    @Test
    void anotherRoleIsForbiddenAndNothingChanges() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"INACTIVE\"}")
                        .header("X-User-Id", "user-2").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        verify(updateCourt, never()).updateCourt(any());
    }

    @Test
    void withoutIdentityItIs401() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"INACTIVE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"INACTIVE\"}")
                        .header("X-User-Role", "ADMINISTRATOR"))
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
