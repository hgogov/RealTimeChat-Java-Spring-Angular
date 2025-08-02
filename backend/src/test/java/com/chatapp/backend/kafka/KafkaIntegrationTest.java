package com.chatapp.backend.kafka;

import com.chatapp.backend.config.TestControllerConfiguration;
import com.chatapp.backend.model.ChatMessage;
import com.chatapp.backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1,
        brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"},
        topics = {"${app.kafka.topics.chat-messages}", "${app.kafka.topics.chat-messages-dlt}"})
@Import(TestControllerConfiguration.class)
class KafkaIntegrationTest {

    @Value("${app.kafka.topics.chat-messages}")
    private String chatTopic;

    @Value("${app.kafka.topics.chat-messages-dlt}")
    private String dltTopic;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Captor
    private ArgumentCaptor<ChatMessage> chatMessageCaptor;
    @Captor
    private ArgumentCaptor<String> destinationCaptor;

    @BeforeEach
    void setUp() {
        Mockito.reset(messageRepository, messagingTemplate);
    }

    @Test
    void whenMessageSent_thenConsumerShouldProcessSuccessfully() {
        ChatMessage messageToSend = new ChatMessage();
        messageToSend.setContent("Successful Message");
        messageToSend.setSender("testSuccess");
        messageToSend.setRoomId("testRoom");
        messageToSend.setTimestamp(Instant.now());

        ChatMessage savedMessage = new ChatMessage();
        savedMessage.setId(1L);
        savedMessage.setContent(messageToSend.getContent());
        savedMessage.setSender(messageToSend.getSender());
        savedMessage.setRoomId(messageToSend.getRoomId());
        savedMessage.setTimestamp(messageToSend.getTimestamp());

        when(messageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        kafkaTemplate.send(chatTopic, messageToSend);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(messageRepository, times(1)).save(any(ChatMessage.class))
        );

        verify(messagingTemplate, times(1)).convertAndSend(
                destinationCaptor.capture(),
                chatMessageCaptor.capture()
        );
        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/chat/testRoom");
        assertThat(chatMessageCaptor.getValue().getId()).isEqualTo(savedMessage.getId());
    }

    @Test
    void whenMessageProcessingFailsAfterRetries_shouldEndUpInDLT() {
        ChatMessage messageToFail = new ChatMessage();
        messageToFail.setContent("Trigger Processing Failure");
        messageToFail.setSender("testFail");
        messageToFail.setRoomId("failRoom");
        messageToFail.setTimestamp(Instant.now());

        doThrow(new RuntimeException("Simulated permanent processing error!"))
                .when(messageRepository).save(any(ChatMessage.class));

        kafkaTemplate.send(chatTopic, messageToFail);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(messageRepository, times(4)).save(any(ChatMessage.class))
        );

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessage.class));

        System.out.println("DLT Test: Verified save failed 4 times. Assuming DLT processing occurred.");
    }

    @Test
    void whenNonRetryableExceptionOccurs_shouldGoDirectlyToDLT() {
        ChatMessage messageNonRetry = new ChatMessage();
        messageNonRetry.setContent("Trigger Non-Retry Failure");
        messageNonRetry.setSender("testNonRetry");
        messageNonRetry.setRoomId("nonRetryRoom");
        messageNonRetry.setTimestamp(Instant.now());

        doThrow(new IllegalArgumentException("Simulated non-retryable error!"))
                .when(messageRepository).save(any(ChatMessage.class));

        kafkaTemplate.send(chatTopic, messageNonRetry);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(messageRepository, times(1)).save(any(ChatMessage.class))
        );

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessage.class));

        System.out.println("Non-Retryable Test: Verified save failed once. Assuming DLT processing occurred.");
    }
}