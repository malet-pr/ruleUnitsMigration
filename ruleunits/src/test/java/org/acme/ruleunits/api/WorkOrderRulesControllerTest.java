package org.acme.ruleunits.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.acme.ruleunits.orchestration.RuleGroupExecutor;
import org.acme.ruleunits.orchestration.RuleGroupExecutorRegistry;
import org.acme.ruleunits.snapshot.RuleExecutionUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkOrderRulesControllerTest {
    private RuleGroupExecutor executor;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        executor = mock(RuleGroupExecutor.class);
        RuleGroupExecutorRegistry registry =
                new RuleGroupExecutorRegistry(Map.of("A", executor));
        mvc = MockMvcBuilders.standaloneSetup(
                new WorkOrderRulesController(registry, 10)).build();
    }

    @Test
    void preservesFirstOccurrenceOrderAndReturnsLegacyBoolean() throws Exception {
        mvc.perform(post("/reglas/correr-reglas")
                        .param("agrupador", "A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"WO-2\",\"WO-1\",\"WO-2\",\"WO-3\",\"WO-1\"]"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(executor).execute(List.of("WO-2", "WO-1", "WO-3"));
    }

    @Test
    void validButUnconfiguredGroupReturnsNotFound() throws Exception {
        mvc.perform(post("/reglas/correr-reglas")
                        .param("agrupador", "B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"WO-1\"]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RULE_GROUP_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.group").value("B"));

        verifyNoInteractions(executor);
    }

    @Test
    void invalidGroupReturnsBadRequest() throws Exception {
        mvc.perform(post("/reglas/correr-reglas")
                        .param("agrupador", "not valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"WO-1\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RULE_GROUP"));

        verifyNoInteractions(executor);
    }

    @Test
    void validatesSubmittedListBeforeDeduplication() throws Exception {
        WorkOrderRulesController limited = new WorkOrderRulesController(
                new RuleGroupExecutorRegistry(Map.of("A", executor)), 3);
        MockMvc limitedMvc = MockMvcBuilders.standaloneSetup(limited).build();

        limitedMvc.perform(post("/reglas/correr-reglas")
                        .param("agrupador", "A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"WO-1\",\"WO-1\",\"WO-1\",\"WO-1\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORK_ORDER_LIST"))
                .andExpect(jsonPath("$.group").value("A"));

        verifyNoInteractions(executor);
    }

    @Test
    void unavailableConfiguredGroupReturnsServiceUnavailable() throws Exception {
        doThrow(new RuleExecutionUnavailableException())
                .when(executor).execute(List.of("WO-1"));

        mvc.perform(post("/reglas/correr-reglas")
                        .param("agrupador", "A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"WO-1\"]"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RULE_EXECUTION_UNAVAILABLE"))
                .andExpect(jsonPath("$.group").value("A"));
    }
}
