package spbstu.mcs.telegramBot.service;

import org.apache.kafka.clients.consumer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spbstu.mcs.telegramBot.config.Config;
import spbstu.mcs.telegramBot.config.KafkaConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaConsumerService implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    private static final String TOPIC = Config.getKafkaIncomingTopic();
    private final KafkaConsumer<String, String> consumer;
    private final TelegramBotService botService;
    private final ExecutorService processorPool = Executors.newFixedThreadPool(4);
    private volatile boolean running = true;

    public KafkaConsumerService(TelegramBotService botService) {
        this.botService = botService;
        Properties props = KafkaConfig.getConsumerConfig();
        // Добавляем дополнительные настройки для надежности
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        logger.info("Creating Kafka consumer with config: {}", props);
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(TOPIC));
        logger.info("Subscribed to topic: {}", TOPIC);
    }

    @Override
    public void run() {
        //logger.info("Starting Kafka consumer loop for topic: {}", TOPIC);
        try {
            while (running) {
                try {
                    //logger.debug("Polling for messages from topic: {}", TOPIC);
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                    if (records.isEmpty()) {
                        //logger.debug("No messages received");
                        continue;
                    }

                    logger.info("Received {} messages from topic {}", records.count(), TOPIC);

                    for (ConsumerRecord<String, String> record : records) {
                        logger.info("Processing message from partition {}, offset {}: {}",
                                record.partition(), record.offset(), record.value());

                        processorPool.execute(() -> {
                            try {
                                String response = botService.processKafkaMessage(record.value());
                                sendResponseToUser(record.value(), response);
                            } catch (Exception e) {
                                logger.error("Error processing message: {}", record.value(), e);
                            }
                        });
                    }

                    // Вручную подтверждаем обработку
                    consumer.commitAsync((offsets, exception) -> {
                        if (exception != null) {
                            logger.error("Commit failed for offsets: {}", offsets, exception);
                        } else {
                            logger.debug("Committed offsets: {}", offsets);
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error in consumer loop", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            logger.info("Shutting down Kafka consumer");
            consumer.close();
            processorPool.shutdown();
        }
    }

    private void sendResponseToUser(String messageJson, String response) {
        try {
            String chatId = extractChatIdFromMessage(messageJson);
            if (chatId != null) {
                logger.info("Sending response to chatId: {}", chatId);
                botService.sendResponseAsync(chatId, response);
            } else {
                logger.warn("No chatId found in message: {}", messageJson);
            }
        } catch (Exception e) {
            logger.error("Error sending response to user", e);
        }
    }

    private String extractChatIdFromMessage(String messageJson) {
        try {
            int start = messageJson.indexOf("\"chatId\":\"") + 10;
            int end = messageJson.indexOf("\"", start);
            return messageJson.substring(start, end);
        } catch (Exception e) {
            logger.error("Error extracting chatId from message: {}", messageJson, e);
            return null;
        }
    }

    public void stop() {
        running = false;
    }
}