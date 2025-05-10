package spbstu.mcs.telegramBot.commands;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import spbstu.mcs.telegramBot.DB.collections.Notification;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.cryptoApi.PriceFetcher;
import spbstu.mcs.telegramBot.cryptoApi.CurrencyConverter;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class BotCommand {
    private final NotificationService notificationService;
    private final PriceFetcher priceFetcher;
    private final CurrencyConverter currencyConverter;

    public BotCommand(NotificationService notificationService, 
                     PriceFetcher priceFetcher,
                     CurrencyConverter currencyConverter) {
        this.notificationService = notificationService;
        this.priceFetcher = priceFetcher;
        this.currencyConverter = currencyConverter;
    }

    private Mono<SendMessage> handlerMyAlerts(String chatId) {
        return notificationService.getAllActiveAlerts(chatId)
            .collectList()
            .flatMap(notifications -> {
                if (notifications.isEmpty()) {
                    return Mono.just(new SendMessage(chatId, "У вас нет активных алертов"));
                }

                StringBuilder message = new StringBuilder("🔔 Ваши алерты:\n\n");
                return Flux.fromIterable(notifications)
                    .flatMap(notification -> {
                        String crypto = notification.getCryptoCurrency().toString();
                        String fiat = notification.getFiatCurrency().getCode();
                        String type = notification.getThresholdType().toString();
                        
                        StringBuilder alertMessage = new StringBuilder();
                        alertMessage.append(String.format("💰 %s (%s)\n", crypto, type));
                        
                        // Получаем текущую цену в USD и конвертируем в нужную валюту
                        return Mono.zip(
                            priceFetcher.getCurrentPrice(notification.getCryptoCurrency()),
                            currencyConverter.getUsdToFiatRate(notification.getFiatCurrency())
                        ).map(tuple -> {
                            String priceJson = tuple.getT1();
                            BigDecimal conversionRate = tuple.getT2();
                            
                            // Парсим текущую цену в USD
                            BigDecimal currentPriceUSD = new BigDecimal(priceJson.split("\"price\":\"")[1].split("\"")[0]);
                            // Конвертируем в целевую валюту
                            BigDecimal currentPrice = currentPriceUSD.multiply(conversionRate)
                                .setScale(2, RoundingMode.HALF_UP);
                            
                            alertMessage.append(String.format("   Текущая цена: %.2f %s\n", 
                                currentPrice, fiat));
                            
                            switch (type) {
                                case "VALUE" -> {
                                    alertMessage.append(String.format("   Верхняя граница: %.2f %s\n", 
                                        notification.getUpperBoundary(), fiat));
                                    alertMessage.append(String.format("   Нижняя граница: %.2f %s\n", 
                                        notification.getLowerBoundary(), fiat));
                                    alertMessage.append(String.format("   Начальная цена: %.2f %s\n", 
                                        notification.getStartPrice(), fiat));
                                    alertMessage.append("   Статус: ✅\n");
                                }
                                case "PERCENT" -> {
                                    alertMessage.append(String.format("   Верхняя граница: %.2f %s\n", 
                                        notification.getUpperBoundary(), fiat));
                                    alertMessage.append(String.format("   Нижняя граница: %.2f %s\n", 
                                        notification.getLowerBoundary(), fiat));
                                    alertMessage.append(String.format("   Начальная цена: %.2f %s\n", 
                                        notification.getStartPrice(), fiat));
                                }
                                case "EMA" -> {
                                    alertMessage.append(String.format("   Начальное EMA: %.2f %s\n", 
                                        notification.getStartEMA(), fiat));
                                    alertMessage.append(String.format("   Текущее EMA: %.2f %s\n", 
                                        notification.getCurrentEMA(), fiat));
                                    alertMessage.append(String.format("   Начальная цена: %.2f %s\n", 
                                        notification.getStartPrice(), fiat));
                                }
                            }
                            
                            // Добавляем время работы алерта
                            long duration = System.currentTimeMillis() / 1000 - notification.getStartTimestamp();
                            long minutes = duration / 60;
                            long seconds = duration % 60;
                            alertMessage.append(String.format("   Время работы: %d мин. %d сек.\n", minutes, seconds));
                            
                            return alertMessage.toString();
                        });
                    })
                    .collectList()
                    .map(alertMessages -> {
                        message.append(String.join("\n", alertMessages));
                        return new SendMessage(chatId, message.toString());
                    });
            });
    }
} 