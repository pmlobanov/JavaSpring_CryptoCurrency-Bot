package spbstu.mcs.telegramBot.cryptoApi.model;
/**
 * Типы алертов
 */
public enum AlertType {
    PRICE,  // Алерт по конкретным ценам
    PERCENT, // Алерт по процентам
    EMA     // Алерт по экспоненциальному скользящему среднему
} 