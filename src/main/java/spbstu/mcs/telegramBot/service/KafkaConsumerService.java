package spbstu.mcs.telegramBot.service;

import org.apache.kafka.clients.consumer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import spbstu.mcs.telegramBot.util.ChatIdMasker;

@Service
public class KafkaConsumerService implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final String incomingTopic;
    private final String outgoingTopic;
    private final KafkaConsumer<String, String> incomingConsumer;
    private final KafkaConsumer<String, String> outgoingConsumer;
    private final TelegramBotService botService;
    private final ExecutorService processorPool = Executors.newFixedThreadPool(4);
    private volatile boolean running = true;

    @Autowired
    public KafkaConsumerService(
            TelegramBotService botService,
            @Value("${kafka.bootstrap-servers:localhost:29092}") String bootstrapServers,
            @Value("${kafka.consumer.group-id:telegram-bot-group}") String groupId,
            @Value("${kafka.topics.incoming:telegram-incoming-messages}") String incomingTopic,
            @Value("${kafka.topics.outgoing:telegram-outgoing-messages}") String outgoingTopic) {
        this.botService = botService;
        this.incomingTopic = incomingTopic;
        this.outgoingTopic = outgoingTopic;
        
        // Создаем consumer для входящих сообщений
        Properties incomingProps = new Properties();
        incomingProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        incomingProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        incomingProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        incomingProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        incomingProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        incomingProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-incoming");

        logger.info("Creating Kafka consumer for incoming messages with bootstrap servers: {}", bootstrapServers);
        this.incomingConsumer = new KafkaConsumer<>(incomingProps);
        this.incomingConsumer.subscribe(Collections.singletonList(incomingTopic));
        logger.info("Subscribed to incoming messages topic: {}", incomingTopic);
        
        // Создаем consumer для исходящих сообщений
        Properties outgoingProps = new Properties();
        outgoingProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        outgoingProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        outgoingProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        outgoingProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        outgoingProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        outgoingProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-outgoing");

        logger.info("Creating Kafka consumer for outgoing messages with bootstrap servers: {}", bootstrapServers);
        this.outgoingConsumer = new KafkaConsumer<>(outgoingProps);
        this.outgoingConsumer.subscribe(Collections.singletonList(outgoingTopic));
        logger.info("Subscribed to outgoing messages topic: {}", outgoingTopic);
    }

    @Override
    public void run() {
        // Запускаем обработку входящих сообщений в отдельном потоке
        Thread incomingThread = new Thread(this::processIncomingMessages, "incoming-messages-thread");
        incomingThread.setDaemon(true);
        incomingThread.start();
        
        // Запускаем обработку исходящих сообщений в этом потоке
        processOutgoingMessages();
    }
    
    private void processIncomingMessages() {
        logger.info("Starting Kafka consumer loop for incoming messages topic: {}", incomingTopic);
        try {
            while (running) {
                try {
                    ConsumerRecords<String, String> records = incomingConsumer.poll(Duration.ofMillis(1000));

                    if (records.isEmpty()) {
                        continue;
                    }

                    logger.info("Received {} messages from topic {}", records.count(), incomingTopic);

                    for (ConsumerRecord<String, String> record : records) {
                        logger.info("Processing incoming message from partition {}, offset {}: {}",
                                record.partition(), record.offset(), ChatIdMasker.maskChatIdInJson(record.value()));

                        processorPool.execute(() -> {
                            try {
                                botService.processKafkaMessage(record.value())
                                    .subscribe(response -> sendResponseToUser(record.value(), response));
                            } catch (Exception e) {
                                logger.error("Error processing message: {}", ChatIdMasker.maskChatIdInJson(record.value()), e);
                            }
                        });
                    }

                    // Вручную подтверждаем обработку
                    incomingConsumer.commitAsync((offsets, exception) -> {
                        if (exception != null) {
                            logger.error("Commit failed for offsets: {}", offsets, exception);
                        } else {
                            logger.debug("Committed offsets: {}", offsets);
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error in incoming consumer loop", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            logger.info("Shutting down Kafka consumer for incoming messages");
            incomingConsumer.close();
        }
    }
    
    private void processOutgoingMessages() {
        logger.info("Starting Kafka consumer loop for outgoing messages topic: {}", outgoingTopic);
        try {
            while (running) {
                try {
                    ConsumerRecords<String, String> records = outgoingConsumer.poll(Duration.ofMillis(1000));

                    if (records.isEmpty()) {
                        continue;
                    }

                    logger.info("Received {} messages from topic {}", records.count(), outgoingTopic);

                    for (ConsumerRecord<String, String> record : records) {
                        logger.info("Processing outgoing message from partition {}, offset {}: {}",
                                record.partition(), record.offset(), ChatIdMasker.maskChatIdInJson(record.value()));

                        processorPool.execute(() -> {
                            try {
                                // Извлекаем chatId и сообщение из JSON
                                String chatId = extractChatIdFromJson(record.value());
                                String message = extractMessageFromJson(record.value());
                                
                                if (chatId != null && message != null) {
                                    // Декодируем сообщение
                                    String decodedMessage = message.replace("\\n", "\n")
                                                                 .replace("\\r", "\r")
                                                                 .replace("\\t", "\t")
                                                                 .replace("\\\"", "\"")
                                                                 .replace("\\\\", "\\");
                                    
                                    String maskedChatId = ChatIdMasker.maskChatId(chatId);
                                    logger.info("Sending outgoing message to Telegram for chatId: {}", maskedChatId);
                                    SendMessage sendMessage = new SendMessage();
                                    sendMessage.setChatId(chatId);
                                    sendMessage.setText(decodedMessage);
                                    botService.execute(sendMessage);
                                } else {
                                    logger.warn("Invalid outgoing message format: {}", record.value());
                                }
                            } catch (Exception e) {
                                logger.error("Error processing outgoing message: {}", record.value(), e);
                            }
                        });
                    }

                    // Вручную подтверждаем обработку
                    outgoingConsumer.commitAsync((offsets, exception) -> {
                        if (exception != null) {
                            logger.error("Commit failed for offsets: {}", offsets, exception);
                        } else {
                            logger.debug("Committed outgoing message offsets: {}", offsets);
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error in outgoing consumer loop", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            logger.info("Shutting down Kafka consumer for outgoing messages");
            outgoingConsumer.close();
            processorPool.shutdown();
        }
    }

    private void sendResponseToUser(String messageJson, String response) {
        try {
            String chatId = extractChatIdFromJson(messageJson);
            if (chatId != null) {
                String maskedChatId = ChatIdMasker.maskChatId(chatId);
                logger.info("Sending response to chatId: {}", maskedChatId);
                botService.sendResponseAsync(chatId, response);
            } else {
                logger.warn("No chatId found in message: {}", ChatIdMasker.maskChatIdInJson(messageJson));
            }
        } catch (Exception e) {
            logger.error("Error sending response to user", e);
        }
    }

    private String extractChatIdFromJson(String messageJson) {
        try {
            // Сначала пытаемся найти chatId в кавычках
            int start = messageJson.indexOf("\"chatId\":\"") + 10;
            if (start < 10) {
                // Если не найдено в кавычках, пробуем без кавычек
                start = messageJson.indexOf("\"chatId\":") + 9;
                if (start < 9) {
                    logger.error("Не удалось найти chatId в сообщении: {}", ChatIdMasker.maskChatIdInJson(messageJson));
                    return null;
                }
                // Находим конец числа
                int end = messageJson.indexOf(",", start);
                if (end == -1) {
                    end = messageJson.indexOf("}", start);
                }
                if (end == -1) {
                    logger.error("Не удалось найти конец chatId в сообщении: {}", ChatIdMasker.maskChatIdInJson(messageJson));
                    return null;
                }
                return messageJson.substring(start, end).trim();
            }
            // Если найдено в кавычках, находим закрывающую кавычку
            int end = messageJson.indexOf("\"", start);
            if (end == -1) {
                logger.error("Не удалось найти конец chatId в кавычках: {}", ChatIdMasker.maskChatIdInJson(messageJson));
                return null;
            }
            return messageJson.substring(start, end);
        } catch (Exception e) {
            logger.error("Ошибка при извлечении chatId: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractMessageFromJson(String messageJson) {
        try {
            int start = messageJson.indexOf("\"message\":\"") + 11;
            int end = messageJson.lastIndexOf("\"");
            return messageJson.substring(start, end);
        } catch (Exception e) {
            logger.error("Error extracting message from JSON: {}", messageJson, e);
            return null;
        }
    }

    public void stop() {
        running = false;
    }
}