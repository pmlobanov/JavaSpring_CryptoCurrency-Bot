package spbstu.mcs.telegramBot.service;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spbstu.mcs.telegramBot.config.Config;
import spbstu.mcs.telegramBot.config.KafkaConfig;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    private static final String TOPIC = Config.getKafkaIncomingTopic();
    private final KafkaProducer<String, String> producer;

    public KafkaProducerService() {
        Properties props = KafkaConfig.getAsyncProducerConfig();
        logger.info("Creating Kafka producer with properties: {}", props);
        this.producer = new KafkaProducer<>(props);
        logger.info("Kafka producer initialized for topic: {}", TOPIC);
    }

    public void sendMessageAsync(String chatId, String userId, String message) {
        String payload = String.format("{\"chatId\":\"%s\",\"userId\":\"%s\",\"message\":\"%s\"}",
                chatId, userId, message.replace("\"", "\\\""));

        logger.info("Sending message to Kafka topic {}: {}", TOPIC, payload);

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, payload);

        CompletableFuture.runAsync(() -> {
            try {
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        logger.error("Failed to send message to Kafka topic {}: {}",
                                TOPIC, exception.getMessage());
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

    public void close() {
        logger.info("Closing Kafka producer");
        producer.flush();
        producer.close();
        logger.info("Kafka producer closed");
    }
}