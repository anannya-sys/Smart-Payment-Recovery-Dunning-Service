package com.subscription.recovery.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end tests of the CRUD API: HTTP -> controller -> service -> JPA -> PostgreSQL and back. */
class CrudApiIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void createsCustomerPlanAndSubscription_andRejectsIllegalTransition() throws Exception {
        long customerId = idOf(mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Asha Rao","email":"asha.crud@example.in","signupDate":"2024-01-10"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/customers/")))
                .andExpect(jsonPath("$.pastFailureCount").value(0))
                .andReturn().getResponse().getContentAsString());

        long planId = idOf(mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CRUD Premium","monthlyPrice":499.00,"billingCycle":"MONTHLY"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        long subId = idOf(mvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"planId":%d,"paymentMethod":"UPI_AUTOPAY"}"""
                                .formatted(customerId, planId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.planName").value("CRUD Premium"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(patch("/api/subscriptions/" + subId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // CANCELLED is terminal: the state machine refuses to resume it.
        mvc.perform(patch("/api/subscriptions/" + subId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot move subscription from CANCELLED to ACTIVE"));

        mvc.perform(get("/api/customers/" + customerId + "/risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.band").exists());
    }

    @Test
    void invalidInputReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","signupDate":"2099-01-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.signupDate").exists());
    }

    @Test
    void duplicateEmailReturns409_andUnknownIdReturns404() throws Exception {
        String body = """
                {"name":"Dup","email":"dup@example.in","signupDate":"2024-01-10"}""";
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/customers/999999")).andExpect(status().isNotFound());
    }

    private long idOf(String responseBody) throws Exception {
        JsonNode node = json.readTree(responseBody);
        return node.get("id").asLong();
    }
}
