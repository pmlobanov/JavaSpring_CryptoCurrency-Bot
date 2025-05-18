package spbstu.mcs.telegramBot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Утилитный класс для маскирования ID чатов в логах
 */
public class ChatIdMasker {
    private static final Logger logger = LoggerFactory.getLogger(ChatIdMasker.class);

    /**
     * Маскирует ID чата для целей логирования
     * @param chatId ID чата для маскирования
     * @return Маскированный ID чата в формате: первые 2 символа + **** + последние 2 символа
     */
    public static String maskChatId(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) {
            logger.warn("Attempted to mask null or empty chatId");
            return "****";
        }
        
        chatId = chatId.trim();
        if (chatId.length() < 4) {
            logger.warn("ChatId too short to mask properly: {}", chatId);
            return "****";
        }
        
        return chatId.substring(0, 2) + "****" + chatId.substring(chatId.length() - 2);
    }

    /**
     * Маскирует ID чата в JSON-строке для логирования
     * @param json JSON-строка, содержащая поле chatId
     * @return JSON-строка с маскированным chatId
     */
    public static String maskChatIdInJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }

        try {
            // Сначала пытаемся найти chatId в кавычках
            int start = json.indexOf("\"chatId\":\"");
            if (start >= 0) {
                start += 10;
                int end = json.indexOf("\"", start);
                if (end > start) {
                    String chatId = json.substring(start, end);
                    String maskedChatId = maskChatId(chatId);
                    return json.substring(0, start) + maskedChatId + json.substring(end);
                }
            }
            
            // Пытаемся без кавычек
            start = json.indexOf("\"chatId\":");
            if (start >= 0) {
                start += 9;
                int end = json.indexOf(",", start);
                if (end == -1) {
                    end = json.indexOf("}", start);
                }
                if (end > start) {
                    String chatId = json.substring(start, end).trim();
                    String maskedChatId = maskChatId(chatId);
                    return json.substring(0, start) + maskedChatId + json.substring(end);
                }
            }
            
            return json;
        } catch (Exception e) {
            logger.error("Error masking chatId in JSON: {}", e.getMessage());
            return json;
        }
    }
} 