package com.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "payment.mock.total-count=1000",
        "payment.mock.plans[0].attempt=1",
        "payment.mock.plans[0].latency-ms=200",
        "payment.mock.plans[0].success-count=1000",
        "payment.mock.plans[0].fail-count=0"
})
class PaymentApplicationTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).build();
    }

    @Test
    void confirmPaymentUsesAsyncResponse() throws Exception {
        long startedAt = System.nanoTime();

        MvcResult mvcResult = mockMvc.perform(post("/v1/payments/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "order-1",
                                  "paymentKey": "payment-1",
                                  "amount": 1000
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.paymentKey").value("payment-1"))
                .andExpect(jsonPath("$.status").value("OK"));

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(150L);
    }

    @Test
    void duplicatePaymentAlsoUsesConfiguredLatency() throws Exception {
        MvcResult firstResult = mockMvc.perform(post("/v1/payments/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "order-1",
                                  "paymentKey": "payment-dup",
                                  "amount": 1000
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(firstResult))
                .andExpect(status().isOk());

        long startedAt = System.nanoTime();

        MvcResult duplicateResult = mockMvc.perform(post("/v1/payments/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "order-2",
                                  "paymentKey": "payment-dup",
                                  "amount": 1000
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(duplicateResult))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PROCESSED_PAYMENT"));

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(150L);
    }
}
