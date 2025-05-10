package spbstu.mcs.telegramBot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import spbstu.mcs.telegramBot.config.Config;

@Configuration
public class TelegramConfig {
    @Bean
    public TelegramBotsApi telegramBotsApi() throws Exception {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @Bean
    public String botToken() {
        return Config.getTelegramBotToken();
    }
    
    @Bean
    public String botUsername() {
        return Config.getTelegramBotUsername();
    }
}