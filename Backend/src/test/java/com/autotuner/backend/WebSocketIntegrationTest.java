// Integration tests for WebSocket/STOMP tuner message flow; it provides assertions that live update messaging behaves correctly.

package com.autotuner.backend;

import com.autotuner.backend.service.TunerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = AutoTunerBackendApplication.class
)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    // Prevents @PostConstruct from launching the real Python process
    @MockBean
    private TunerService tunerService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setUp() throws Exception {
        List<Transport> transports = Collections.singletonList(
                new WebSocketTransport(new StandardWebSocketClient())
        );
        stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(new StringMessageConverter());

        session = stompClient
            .connectAsync("http://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();
    }

    @Test
    void client_canConnectToWebSocketEndpoint() {
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    void client_receivesMessagePublishedToTuningTopic() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        session.subscribe("/topic/tuning", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((String) payload);
            }
        });

        // Allow subscription to be registered on the broker
        Thread.sleep(500);

        String expected = "{\"frequency\":82.4,\"note\":\"E2\"}";
        messagingTemplate.convertAndSend("/topic/tuning", expected);

        String actual = received.poll(5, TimeUnit.SECONDS);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void client_receivesMultipleMessages() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        session.subscribe("/topic/tuning", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((String) payload);
            }
        });

        Thread.sleep(500);

        String msg1 = "{\"frequency\":82.4,\"note\":\"E2\"}";
        String msg2 = "{\"frequency\":110.0,\"note\":\"A2\"}";
        messagingTemplate.convertAndSend("/topic/tuning", msg1);
        Thread.sleep(100); // Ensure messages are sent in order
        messagingTemplate.convertAndSend("/topic/tuning", msg2);

        assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo(msg1);
        assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo(msg2);
    }
}
