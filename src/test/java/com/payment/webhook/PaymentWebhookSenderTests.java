package com.payment.webhook;

import com.payment.config.PaymentWebhookProperties;
import com.payment.controller.dto.PaymentPayload;
import com.payment.controller.dto.SuccessResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentWebhookSenderTests {

    @Test
    void sendDonePostsPaymentStatusChangedWebhook() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<List<String>> transmissionIdHeaders = new AtomicReference<>();

        HttpServer server = startServer(received, bodyRef, transmissionIdHeaders);
        try {
            int port = server.getAddress().getPort();
            PaymentWebhookSender sender = new PaymentWebhookSender(
                    new PaymentWebhookProperties(
                            true,
                            "http://localhost:" + port + "/payments/webhooks/toss",
                            0,
                            10,
                            1,
                            10,
                            1,
                            500,
                            1_000
                    ),
                    RestClient.builder()
            );

            PaymentPayload payload = new PaymentPayload("order-1", "payment-1", 1000L);
            sender.sendDone(payload, SuccessResult.create(payload));

            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(bodyRef.get())
                    .contains("\"eventType\":\"PAYMENT_STATUS_CHANGED\"")
                    .contains("\"orderId\":\"order-1\"")
                    .contains("\"paymentKey\":\"payment-1\"")
                    .contains("\"status\":\"DONE\"")
                    .contains("\"totalAmount\":1000");
            assertThat(transmissionIdHeaders.get()).isNotEmpty();

            sender.shutdown();
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(
            CountDownLatch received,
            AtomicReference<String> bodyRef,
            AtomicReference<List<String>> transmissionIdHeaders
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/payments/webhooks/toss", exchange -> {
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            transmissionIdHeaders.set(exchange.getRequestHeaders().get("tosspayments-webhook-transmission-id"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            received.countDown();
        });
        server.start();
        return server;
    }
}
