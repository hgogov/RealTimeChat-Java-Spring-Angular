package com.chatapp.backend.kafka;

import com.chatapp.backend.model.ChatMessage;
import com.chatapp.backend.repository.MessageRepository;
import com.chatapp.backend.service.KafkaProducerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@SpringBootTest
@ActiveProfiles("kafka-test")
@DirtiesContext
@EmbeddedKafka(partitions = 1, brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" }, topics = { "chat-messages", "chat-messages-dlt" })
class KafkaIntegrationTest {

    @TestConfiguration
    @ActiveProfiles("kafka-test")
    static class KafkaTestConfig {
        @Bean
        @Primary
        public MessageRepository mockMessageRepository() {
            System.out.println("--- Providing Mock MessageRepository Bean ---");
            return Mockito.mock(MessageRepository.class);
        }

        @Bean
        @Primary
        public SimpMessagingTemplate mockSimpMessagingTemplate() {
            System.out.println("--- Providing Mock SimpMessagingTemplate Bean ---");
            return Mockito.mock(SimpMessagingTemplate.class);
        }
    }

    @Autowired
    private KafkaProducerService producerService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void whenMessageSent_thenConsumerShouldSaveAndBroadcast() {
        // Arrange
        ChatMessage messageToSend = new ChatMessage();
        messageToSend.setContent("Integration Test Message");
        messageToSend.setSender("testProducer");
        messageToSend.setRoomId("testRoom");
        messageToSend.setTimestamp(Instant.now());

        ChatMessage savedMessage = new ChatMessage();
        savedMessage.setId(1L);
        savedMessage.setContent(messageToSend.getContent());
        savedMessage.setSender(messageToSend.getSender());
        savedMessage.setRoomId(messageToSend.getRoomId());
        savedMessage.setTimestamp(messageToSend.getTimestamp());

        // Configure the injected mock beans
        when(messageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        // Act
        producerService.sendMessage(messageToSend);

        // Assert
        ArgumentCaptor<ChatMessage> chatMessageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

        // Verify the MOCKED MessageRepository was called
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(messageRepository, times(1)).save(chatMessageCaptor.capture())
        );

        ChatMessage capturedForSave = chatMessageCaptor.getValue();
        assertThat(capturedForSave.getContent()).isEqualTo("Integration Test Message");

        // Verify the MOCKED SimpMessagingTemplate was called
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/chat/testRoom"),
                eq(savedMessage)
        );
    }

    // TODO: Add tests for error handling and DLT flow
}