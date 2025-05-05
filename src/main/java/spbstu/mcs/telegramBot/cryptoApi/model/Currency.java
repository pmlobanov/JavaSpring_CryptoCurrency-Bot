package spbstu.mcs.telegramBot.cryptoApi.model;
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
        BTC("BTC"),
        ETH("ETH"),
        SOL("SOL"),
        MATIC("MATIC"),
        XRP("XRP"),
        ADA("ADA"),
        DOGE("DOGE"),
        AVAX("AVAX"),
        NEAR("NEAR"),
        LTS("LTS");
        
        private final String code;
        private static Crypto currentCrypto = BTC;
        
        Crypto(String code) {
            this.code = code;
        }
        
        public String getCode() {
            return code;
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