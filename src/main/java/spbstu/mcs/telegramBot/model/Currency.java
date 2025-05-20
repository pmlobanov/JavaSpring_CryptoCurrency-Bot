package spbstu.mcs.telegramBot.model;
/**
 * Перечисления для криптовалют и фиатных валют
 */
public class Currency {
    /**
     * Фиатные валюты
     */
    public enum Fiat {
        USD("USD"),
        EUR("EUR"),
        JPY("JPY"),
        GBP("GBP"),
        RUB("RUB"),
        CNY("CNY");
        
        private final String code;
        
        Fiat(String code) {
            this.code = code;
        }
        
        public String getCode() {
            return code;
        }
        
    }
    
    /**
     * Криптовалюты
     */
    public enum Crypto {
        BTC("BTC", "Биткоин"),
        ETH("ETH", "Эфириум"),
        SOL("SOL", "Солана"),
        XRP("XRP", "Рипл"),
        ADA("ADA", "Кардано"),
        DOGE("DOGE", "Догикоин"),
        AVAX("AVAX", "Аваланч"),
        NEAR("NEAR", "Нир"),
        LTC("LTC", "Лайткоин");
        
        private final String code;
        private final String name;
        
        Crypto(String code, String name) {
            this.code = code;
            this.name = name;
        }
        
        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
        

        public static Crypto fromSymbol(String symbol) {
            String cryptoCode = symbol.split("-")[0];
            for (Crypto crypto : values()) {
                if (crypto.code.equals(cryptoCode)) {
                    return crypto;
                }
            }
            throw new IllegalArgumentException("Неизвестная криптовалюта: " + cryptoCode);
        }
    }
} 