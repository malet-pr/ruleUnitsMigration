package org.acme.ruleunits.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.acme.ruleunits.refresh.RuleSetRefreshPhase;
import org.acme.ruleunits.refresh.RuleSetRefreshResult;
import org.acme.ruleunits.refresh.RuleSetRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleSetRefreshControllerTest {
    private RuleSetRuntimeService runtime;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        runtime = mock(RuleSetRuntimeService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RuleSetRefreshController(runtime)).build();
    }

    @Test
    void returnsPublishedSnapshotDetails() throws Exception {
        when(runtime.refresh()).thenReturn(RuleSetRefreshResult.published(
                "ACTIVITY_RULES", 17L, "published-correlation"));

        mvc.perform(post("/admin/rules/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleSetName").value("ACTIVITY_RULES"))
                .andExpect(jsonPath("$.attemptedVersion").value(17))
                .andExpect(jsonPath("$.correlationId").value("published-correlation"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(runtime).refresh();
    }

    @Test
    void returnsSanitizedFailureWithoutRawDiagnostic() throws Exception {
        when(runtime.refresh()).thenReturn(RuleSetRefreshResult.failed(
                "ACTIVITY_RULES",
                18L,
                "failed-correlation",
                RuleSetRefreshPhase.COMPILE,
                new IllegalStateException("sensitive compiler diagnostic")));

        String response = mvc.perform(post("/admin/rules/refresh"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.ruleSetName").value("ACTIVITY_RULES"))
                .andExpect(jsonPath("$.attemptedVersion").value(18))
                .andExpect(jsonPath("$.correlationId").value("failed-correlation"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failurePhase").value("COMPILE"))
                .andExpect(jsonPath("$.failureType").value("IllegalStateException"))
                .andExpect(jsonPath("$.summary").value("Failed to compile assembled rule set"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("sensitive compiler diagnostic");
        verify(runtime).refresh();
    }
}
