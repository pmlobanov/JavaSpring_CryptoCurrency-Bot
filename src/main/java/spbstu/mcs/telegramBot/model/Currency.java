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
        private static Fiat currentFiat = USD;
        
        Fiat(String code) {
            this.code = code;
        }
        
        public String getCode() {
            return code;
        }
        
        public static Fiat getCurrentFiat() {
            return currentFiat;
        }
        
        public static void setCurrentFiat(Fiat fiat) {
            currentFiat = fiat;
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
        private static Crypto currentCrypto = BTC;
        
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
        
        public static Crypto getCurrentCrypto() {
            return currentCrypto;
        }
        
        public static void setCurrentCrypto(Crypto crypto) {
            currentCrypto = crypto;
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