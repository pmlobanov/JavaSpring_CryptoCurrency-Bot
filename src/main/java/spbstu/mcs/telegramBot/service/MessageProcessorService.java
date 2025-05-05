package spbstu.mcs.telegramBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spbstu.mcs.telegramBot.DB.ApplicationContextManager;
import spbstu.mcs.telegramBot.DB.collections.User;
import spbstu.mcs.telegramBot.DB.services.UserService;

public class MessageProcessorService {
    private static final Logger logger = LoggerFactory.getLogger(MessageProcessorService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserService userService;

    public MessageProcessorService() {
        try (ApplicationContextManager contextManager = ApplicationContextManager.create()) {
            this.userService = contextManager.getUserService();
        }
    }

    public String processMessage(String jsonMessage) {
        try {
            JsonNode json = objectMapper.readTree(jsonMessage);
            String text = json.get("message").asText();
            String chatId = json.get("chatId").asText();
            String userId = json.get("userId").asText();

            logger.info("Processing Kafka message from chatId: {}, userId: {}", chatId, userId);

            User user = userService.findByUserTgName(userId);
            if (user == null) {
                return "⚠️ Пожалуйста, начните с команды /start для регистрации";
            }


            if (text.startsWith("/")) {
                String command = text.split(" ")[0].toLowerCase();
                switch (command) {
                    case "/start":
                        return BotCommand.handlerStart();
                    case "/help":
                        return BotCommand.handlerHelp();
                    default:
                        return BotCommand.handlerQ();
                }
            }
            return BotCommand.handlerQ();

        } catch (Exception e) {
            logger.error("Error processing Kafka message", e);
            return "⚠️ Error processing your request";
        }
    }
}
