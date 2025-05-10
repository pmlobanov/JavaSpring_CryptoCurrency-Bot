package spbstu.mcs.telegramBot.DB.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import spbstu.mcs.telegramBot.DB.collections.Portfolio;
import spbstu.mcs.telegramBot.DB.repositories.PortfolioRepository;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.cryptoApi.PriceFetcher;
import spbstu.mcs.telegramBot.cryptoApi.CurrencyConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PortfolioManagement {
    private final PortfolioRepository portfolioRepository;
    private final PriceFetcher priceFetcher;
    private final CurrencyConverter currencyConverter;
    private final ObjectMapper objectMapper;

    @Autowired
    public PortfolioManagement(PortfolioRepository portfolioRepository,
                             PriceFetcher priceFetcher,
                             CurrencyConverter currencyConverter,
                             ObjectMapper objectMapper) {
        this.portfolioRepository = portfolioRepository;
        this.priceFetcher = priceFetcher;
        this.currencyConverter = currencyConverter;
        this.objectMapper = objectMapper;
    }

    public Mono<String> getPortfolioInfo(String portfolioId) {
        return Mono.fromCallable(() -> {
            Optional<Portfolio> portfolioOpt = portfolioRepository.findById(portfolioId);
            if (portfolioOpt.isEmpty()) {
                return "Портфель не найден";
            }

            Portfolio portfolio = portfolioOpt.get();
            StringBuilder response = new StringBuilder();
            response.append("📊 Информация о портфеле\n\n");

            if (portfolio.getCryptoCurrency() != null) {
                response.append("Криптовалюта: ").append(portfolio.getCryptoCurrency().getCode()).append("\n");
                response.append("Количество: ").append(portfolio.getCount()).append("\n");
                
                if (portfolio.getLastCryptoPrice() != null) {
                    response.append("Последняя известная цена: ")
                           .append(portfolio.getLastCryptoPrice())
                           .append(" ")
                           .append(portfolio.getFiatCurrency().getCode())
                           .append("\n");
                }
            } else {
                response.append("Портфель пуст\n");
            }

            return response.toString();
        });
    }

    public Mono<String> getPortfolioValue(String portfolioId) {
        return Mono.fromCallable(() -> {
            Optional<Portfolio> portfolioOpt = portfolioRepository.findById(portfolioId);
            if (portfolioOpt.isEmpty()) {
                return Mono.just("Портфель не найден");
            }

            Portfolio portfolio = portfolioOpt.get();
            if (portfolio.getCryptoCurrency() == null) {
                return Mono.just("Портфель пуст");
            }

            return priceFetcher.getCurrentPrice(portfolio.getCryptoCurrency())
                .flatMap(priceJson -> {
                    try {
                        JsonNode node = objectMapper.readTree(priceJson);
                        BigDecimal currentPrice = new BigDecimal(node.get("price").asText());
                        BigDecimal totalValue = portfolio.getCount().multiply(currentPrice);
                        
                        portfolio.setLastCryptoPrice(currentPrice);
                        portfolio.setLastCryptoPriceTimestamp(System.currentTimeMillis() / 1000);
                        portfolioRepository.save(portfolio);

                        return Mono.just(String.format("💰 Стоимость портфеля: %.2f %s", 
                            totalValue, portfolio.getFiatCurrency().getCode()));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
        }).flatMap(mono -> mono);
    }

    public List<Portfolio> getPortfoliosByChatId(String chatId) {
        return portfolioRepository.findByChatId(chatId);
    }

    public Portfolio save(Portfolio portfolio) {
        return portfolioRepository.save(portfolio);
    }
} 