package dev.canverse.stocks.platform.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.ledger.application.CashActivityCommandService;
import dev.canverse.stocks.ledger.application.CashActivityQueryService;
import dev.canverse.stocks.ledger.application.FinancialAccountLifecycleService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.application.FinancialAccountQueryService;
import dev.canverse.stocks.ledger.application.FinancialAccountSettingsService;
import dev.canverse.stocks.ledger.web.CashActivityController;
import dev.canverse.stocks.ledger.web.FinancialAccountController;
import dev.canverse.stocks.platform.error.GlobalExceptionHandler;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.reference.application.InstrumentSearchService;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.web.ManualInstrumentController;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CashActivityController.class, FinancialAccountController.class, ManualInstrumentController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, RequestBindingMvcTest.MvcSupport.class})
class RequestBindingMvcTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CashActivityCommandService cashActivityCommandService;

    @MockitoBean
    CashActivityQueryService cashActivityQueryService;

    @MockitoBean
    DeviceSessionRepository deviceSessionRepository;

    @MockitoBean
    IdGenerator idGenerator;

    @MockitoBean
    FinancialAccountOnboardingService financialAccountOnboardingService;

    @MockitoBean
    FinancialAccountQueryService financialAccountQueryService;

    @MockitoBean
    FinancialAccountSettingsService financialAccountSettingsService;

    @MockitoBean
    FinancialAccountLifecycleService financialAccountLifecycleService;

    @MockitoBean
    ManualInstrumentService manualInstrumentService;

    @MockitoBean
    InstrumentSearchService instrumentSearchService;

    @Test
    void cashActivityJsonBindingAndValidationUseTheCommonProblemContract() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", ACCOUNT_ID).contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestId\":"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", ACCOUNT_ID).contentType(MediaType.APPLICATION_JSON).content(
                """
                        {"clientRequestId":"20000000-0000-4000-8000-000000000004","activityType":"NOT_A_TYPE","amount":"1","recordingMode":"CURRENT_ACTION","effectiveAt":"2026-09-19T11:00:00Z"}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(
                post("/api/v1/accounts/{accountId}/activities", ACCOUNT_ID).contentType(MediaType.APPLICATION_JSON).content("{\"confirmPolicyBreach\":false}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", ACCOUNT_ID).contentType(MediaType.APPLICATION_JSON).content(
                """
                        {"clientRequestId":"20000000-0000-4000-8000-000000000012","activityType":"CASH_DEPOSIT","amount":"1","recordingMode":"CURRENT_ACTION","effectiveAt":"2026-09-19T11:00:00Z","confirmPolicyBreach":false,"expectedBalanceVersion":-1}
                        """))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(cashActivityCommandService);
    }

    @Test
    void accountRequestValidationRunsBeforeTheOnboardingService() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(
                """
                        {"clientRequestId":"20000000-0000-4000-8000-000000000005","name":"Missing opening","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR"}
                        """))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.errors[0].field").value("openingState"));

        verifyNoInteractions(financialAccountOnboardingService);
    }

    @Test
    void accountMutationBeanValidationRejectsMissingAndNegativeVersionsBeforeServices() throws Exception {
        var account = "/api/v1/accounts/" + ACCOUNT_ID;
        var requests = List.of(put(account).contentType(MediaType.APPLICATION_JSON).content("""
                {"clientRequestId":"20000000-0000-4000-8000-000000000006","name":"Renamed","timeZone":"UTC"}
                """), put(account + "/policy").contentType(MediaType.APPLICATION_JSON).content("""
                {"clientRequestId":"20000000-0000-4000-8000-000000000007","policy":"HARD_FLOOR"}
                """), post(account + "/archive").contentType(MediaType.APPLICATION_JSON).content("""
                {"clientRequestId":"20000000-0000-4000-8000-000000000008"}
                """),
                put(account + "/opening-state").contentType(MediaType.APPLICATION_JSON).content(
                        """
                                {"clientRequestId":"20000000-0000-4000-8000-000000000009","amount":"11","effectiveAt":"2026-08-17T11:00:00Z","correctionReason":"Missing version"}
                                """),
                put(account).contentType(MediaType.APPLICATION_JSON).content("""
                        {"clientRequestId":"20000000-0000-4000-8000-000000000010","version":-1,"name":"Negative version","timeZone":"UTC"}
                        """));

        for (var request : requests) {
            mockMvc.perform(request).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        verifyNoInteractions(financialAccountOnboardingService, financialAccountQueryService, financialAccountSettingsService,
                financialAccountLifecycleService);
    }

    @Test
    void invalidInstrumentEnumsAndPathUuidUseStableMalformedRequestProblems() throws Exception {
        mockMvc.perform(post("/api/v1/reference/instruments").contentType(MediaType.APPLICATION_JSON).content(
                """
                        {"marketId":"20000000-0000-4000-8000-000000000011","symbol":"CONTRACT","name":"Contract","instrumentType":"fund","quotationCurrency":"GBP","valuationMethod":"MANUAL_VALUE"}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/v1/reference/instruments").contentType(MediaType.APPLICATION_JSON).content(
                """
                        {"marketId":"20000000-0000-4000-8000-000000000011","symbol":"CONTRACT","name":"Contract","instrumentType":"FUND","quotationCurrency":"GBP","valuationMethod":"UNKNOWN"}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/v1/reference/instruments").contentType(MediaType.APPLICATION_JSON).content(
                """
                        {"marketId":"20000000-0000-4000-8000-000000000011","symbol":"CONTRACT","name":"Contract","instrumentType":"FUND","quotationCurrency":"GBP","valuationMethod":"MANUAL_VALUE","aliases":[{"type":"UNKNOWN","value":"alias"}]}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(get("/api/v1/reference/instruments").param("type", "fund")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(get("/api/v1/activities/not-a-uuid")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(manualInstrumentService, instrumentSearchService, cashActivityQueryService);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MvcSupport {

        @Bean
        Clock exceptionClock() {
            return Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        Tracer exceptionTracer() {
            return Tracer.NOOP;
        }
    }
}
