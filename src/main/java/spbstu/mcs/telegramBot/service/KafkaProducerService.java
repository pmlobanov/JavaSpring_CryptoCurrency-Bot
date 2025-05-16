package spbstu.mcs.telegramBot.service;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Properties;
import java.util.UUID;
import spbstu.mcs.telegramBot.util.ChatIdMasker;

@Service
public class KafkaProducerService {
    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private final KafkaProducer<String, String> producer;
    private final String outgoingTopic;
    private final String incomingTopic;

    public KafkaProducerService(
            @Value("${kafka.bootstrap-servers:localhost:29092}") String bootstrapServers,
            @Value("${kafka.topics.outgoing:telegram-outgoing-messages}") String outgoingTopic,
            @Value("${kafka.topics.incoming:telegram-incoming-messages}") String incomingTopic) {
        this.outgoingTopic = outgoingTopic;
        this.incomingTopic = incomingTopic;
        
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        
        this.producer = new KafkaProducer<>(props);
        log.info("Kafka producer initialized with bootstrap servers: {}, incoming topic: {}, outgoing topic: {}", 
                bootstrapServers, incomingTopic, outgoingTopic);
    }

    /**
     * Отправляет входящее сообщение от пользователя в Kafka
     * @param chatId ID чата
     * @param message Сообщение пользователя
     */
    public void sendIncomingMessageAsync(String chatId, String message) {
        try {
            String messageId = UUID.randomUUID().toString();
            String maskedChatId = ChatIdMasker.maskChatId(chatId);
            
            // Создаем JSON с замаскированным chat_id для логирования
            String logMessage = String.format(
                "{\"messageId\":\"%s\",\"chatId\":\"%s\",\"message\":\"%s\"}",
                messageId, maskedChatId, message
            );
            
            // Создаем JSON с реальным chat_id для отправки
            String kafkaMessage = String.format(
                "{\"messageId\":\"%s\",\"chatId\":\"%s\",\"message\":\"%s\"}",
                messageId, chatId, message
            );
            
            log.info("Sending incoming message to Kafka: {}", logMessage);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(incomingTopic, kafkaMessage);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Error sending incoming message to Kafka for user {}: {}", maskedChatId, exception.getMessage());
                } else {
                    log.info("Incoming message sent successfully to Kafka for user: {}", maskedChatId);
                }
            });
        } catch (Exception e) {
            log.error("Error sending incoming message to Kafka for user {}: {}", ChatIdMasker.maskChatId(chatId), e.getMessage());
            throw new RuntimeException("Failed to send incoming message to Kafka", e);
        }
    }

    public void sendOutgoingMessageAsync(String chatId, String message) {
        try {
            String messageId = UUID.randomUUID().toString();
            String maskedChatId = ChatIdMasker.maskChatId(chatId);
            
            // Создаем JSON с замаскированным chat_id для логирования
            String logMessage = String.format(
                "{\"messageId\":\"%s\",\"chatId\":\"%s\",\"message\":\"%s\"}",
                messageId, maskedChatId, message
            );
            
            // Создаем JSON с реальным chat_id для отправки
            String kafkaMessage = String.format(
                "{\"messageId\":\"%s\",\"chatId\":\"%s\",\"message\":\"%s\"}",
                messageId, chatId, message
            );
            
            log.info("Sending outgoing message to Kafka: {}", logMessage);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(outgoingTopic, kafkaMessage);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Error sending outgoing message to Kafka for user {}: {}", maskedChatId, exception.getMessage());
                } else {
                    log.info("Outgoing message sent successfully to Kafka for user: {}", maskedChatId);
                }
            });
        } catch (Exception e) {
            log.error("Error sending outgoing message to Kafka for user {}: {}", ChatIdMasker.maskChatId(chatId), e.getMessage());
            throw new RuntimeException("Failed to send outgoing message to Kafka", e);
        }
    }

    public void close() {
        log.info("Closing Kafka producer");
        producer.flush();
        producer.close();
        log.info("Kafka producer closed");
    }
}
