package spbstu.mcs.telegramBot.service;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import spbstu.mcs.telegramBot.config.Config;
import spbstu.mcs.telegramBot.config.KafkaConfig;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    private final KafkaProducer<String, String> producer;

    public KafkaProducerService() {
        Properties props = KafkaConfig.getAsyncProducerConfig();
        logger.info("Creating Kafka producer with properties: {}", props);
        this.producer = new KafkaProducer<>(props);
        logger.info("Kafka producer initialized");
    }

    public void sendIncomingMessageAsync(String chatId, String message) {
        String payload = String.format("{\"chatId\":\"%s\",\"message\":\"%s\"}",
                chatId, message.replace("\"", "\\\""));

        String topic = Config.getKafkaIncomingTopic();
        logger.info("Sending message to Kafka topic {}: {}", topic, payload);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, payload);

        CompletableFuture.runAsync(() -> {
            try {
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        logger.error("Failed to send message to Kafka topic {}: {}",
                                topic, exception.getMessage());
                    } else {
                        logger.info("Successfully sent message to Kafka topic {}, partition {}, offset {}",
                                metadata.topic(), metadata.partition(), metadata.offset());
                    }
                });
            } catch (Exception e) {
                logger.error("Error sending message to Kafka", e);
            }
        });
    }

    public void sendOutgoingMessageAsync(String chatId, String message) {
        try {
            // Properly encode the message to handle special characters
            String encodedMessage = message.replace("\\", "\\\\")
                                         .replace("\"", "\\\"")
                                         .replace("\n", "\\n")
                                         .replace("\r", "\\r")
                                         .replace("\t", "\\t");
            
            String payload = String.format("{\"chatId\":\"%s\",\"message\":\"%s\"}",
                    chatId, encodedMessage);

            String topic = Config.getKafkaOutgoingTopic();
            logger.info("Sending outgoing message to Kafka topic {}: {}", topic, payload);

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, payload);

            CompletableFuture.runAsync(() -> {
                try {
                    producer.send(record, (metadata, exception) -> {
                        if (exception != null) {
                            logger.error("Failed to send message to Kafka topic {}: {}",
                                    topic, exception.getMessage());
                        } else {
                            logger.info("Successfully sent outgoing message to Kafka topic {}, partition {}, offset {}",
                                    metadata.topic(), metadata.partition(), metadata.offset());
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error sending outgoing message to Kafka", e);
                }
            });
        } catch (Exception e) {
            logger.error("Error preparing message for Kafka", e);
        }
    }

    public void close() {
        logger.info("Closing Kafka producer");
        producer.flush();
        producer.close();
        logger.info("Kafka producer closed");
    }
}
