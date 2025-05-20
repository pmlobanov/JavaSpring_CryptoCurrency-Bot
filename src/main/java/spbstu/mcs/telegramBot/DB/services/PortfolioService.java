package spbstu.mcs.telegramBot.DB.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import spbstu.mcs.telegramBot.DB.repositories.PortfolioRepository;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.Portfolio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для управления портфелями криптовалют.
 * Обеспечивает создание, модификацию и расчет стоимости портфелей.
 *
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание и удаление портфелей</li>
 *   <li>Добавление/удаление криптовалютных активов</li>
 *   <li>Расчет текущей стоимости портфеля</li>
 *   <li>Обновление количества криптовалют в портфеле</li>
 * </ul>
 */
@Service
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final UserService userService;
    private final Map<Currency.Crypto, BigDecimal> maxAmounts;
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    @Autowired
    public PortfolioService(PortfolioRepository portfolioRepository, 
                          @Lazy UserService userService) {
        this.portfolioRepository = portfolioRepository;
        this.userService = userService;
        
        // Initialize maximum amounts for each cryptocurrency
        this.maxAmounts = new HashMap<>();
        maxAmounts.put(Currency.Crypto.BTC, new BigDecimal("1000")); // 1000 BTC
        maxAmounts.put(Currency.Crypto.ETH, new BigDecimal("10000")); // 10000 ETH
        maxAmounts.put(Currency.Crypto.SOL, new BigDecimal("100000")); // 100000 SOL
        maxAmounts.put(Currency.Crypto.XRP, new BigDecimal("1000000")); // 1000000 XRP
        maxAmounts.put(Currency.Crypto.ADA, new BigDecimal("1000000")); // 1000000 ADA
        maxAmounts.put(Currency.Crypto.DOGE, new BigDecimal("10000000")); // 10000000 DOGE
        maxAmounts.put(Currency.Crypto.AVAX, new BigDecimal("100000")); // 100000 AVAX
        maxAmounts.put(Currency.Crypto.NEAR, new BigDecimal("1000000")); // 1000000 NEAR
        maxAmounts.put(Currency.Crypto.LTC, new BigDecimal("100000")); // 100000 LTC
    }

    /**
     * Создает новый портфель и связывает его с пользователем.
     *
     * @param chatId идентификатор чата пользователя
     * @return созданный портфель
     * @throws org.springframework.dao.DataAccessException при ошибках сохранения
     */
    public Mono<Portfolio> createPortfolio(String chatId) {
        return userService.getUserByChatId(chatId)
            .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
            .flatMap(user -> {
                Portfolio portfolio = new Portfolio(chatId);
                Portfolio savedPortfolio = portfolioRepository.save(portfolio);
                return userService.addPortfolioToUser(chatId, savedPortfolio.getId())
                    .thenReturn(savedPortfolio);
            });
    }

    /**
     * Получает портфель по идентификатору.
     *
     * @param portfolioId идентификатор портфеля
     * @return найденный портфель
     * @throws NoSuchElementException если портфель не найден
     */
    public Portfolio getPortfolio(String portfolioId) {
        Optional<Portfolio> portfolio = portfolioRepository.findById(portfolioId);
        if (portfolio.isEmpty()) {
            throw new RuntimeException("Portfolio not found");
        }
        return portfolio.get();
    }

    /**
     * Находит портфель по названию.
     *
     * @param portfolioName название портфеля
     * @return найденный портфель
     * @throws NoSuchElementException если портфель не найден
     */
    public Portfolio getPortfolioId(String portfolioName) {
        return portfolioRepository.findByName(portfolioName);
    }

    /**
     * Удаляет портфель.
     *
     * @param portfolioId ID портфеля
     * @throws NoSuchElementException если портфель не найден
     */
    public void deletePortfolio(String portfolioId) {
        Portfolio portfolio = getPortfolio(portfolioId);
        portfolioRepository.delete(portfolio);
    }

    public Portfolio addCryptoToPortfolio(String portfolioId, Currency.Crypto crypto, BigDecimal amount) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Портфель не найден"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Количество должно быть положительным");
        }

        if (amount.compareTo(new BigDecimal("0.000001")) < 0) {
            throw new IllegalArgumentException("Минимальное количество должно быть не менее 0.000001");
        }

        if (amount.scale() > 6) {
            throw new IllegalArgumentException("Количество не может содержать более 6 знаков после запятой");
        }

        // Check maximum amount for the cryptocurrency
        BigDecimal maxAmount = maxAmounts.get(crypto);
        if (maxAmount == null) {
            throw new IllegalArgumentException("Неподдерживаемая криптовалюта");
        }

        // Calculate new total amount
        BigDecimal currentAmount = portfolio.getCount() == null ? BigDecimal.ZERO : portfolio.getCount();
        BigDecimal newTotal = currentAmount.add(amount);

        if (newTotal.compareTo(maxAmount) > 0) {
            StringBuilder limits = new StringBuilder();
            limits.append("Ограничения по максимальному количеству:\n");
            maxAmounts.forEach((c, max) -> limits.append(String.format("- %s: %s\n", c.getCode(), max)));
            
            throw new IllegalArgumentException(String.format(
                "Превышено максимальное количество для %s\n" +
                "Текущее количество в портфеле: %s %s\n" +
                "Попытка добавить: %s %s\n" +
                "Максимально допустимое количество: %s %s\n\n" +
                "%s",
                crypto.getCode(),
                currentAmount, crypto.getCode(),
                amount, crypto.getCode(),
                maxAmount, crypto.getCode(),
                limits.toString()
            ));
        }

        if (portfolio.getCryptoCurrency() == null || portfolio.getCryptoCurrency() == crypto) {
            portfolio.setCryptoCurrency(crypto);
            portfolio.setCount(newTotal);
        } else {
            throw new IllegalArgumentException("Портфель уже содержит другую криптовалюту");
        }

        return portfolioRepository.save(portfolio);
    }

    public Portfolio removeCryptoFromPortfolio(String portfolioId, Currency.Crypto crypto, BigDecimal amount) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Портфель не найден"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Количество должно быть положительным");
        }

        if (amount.compareTo(new BigDecimal("0.000001")) < 0) {
            throw new IllegalArgumentException("Минимальное количество должно быть не менее 0.000001");
        }

        if (amount.scale() > 6) {
            throw new IllegalArgumentException("Количество не может содержать более 6 знаков после запятой");
        }

        if (portfolio.getCryptoCurrency() != crypto) {
            throw new IllegalArgumentException("Портфель не содержит данную криптовалюту");
        }

        BigDecimal currentAmount = portfolio.getCount();
        if (currentAmount.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Недостаточное количество");
        }

        portfolio.setCount(currentAmount.subtract(amount));
        if (portfolio.getCount().compareTo(BigDecimal.ZERO) == 0) {
            portfolio.setCryptoCurrency(null);
        }

        return portfolioRepository.save(portfolio);
    }

    /**
     * Сохраняет портфель в базу данных.
     *
     * @param portfolio портфель для сохранения
     * @return сохраненный портфель
     * @throws org.springframework.dao.DataAccessException при ошибках сохранения
     */
    public Portfolio savePortfolio(Portfolio portfolio) {
        if (portfolio == null) {
            throw new IllegalArgumentException("Portfolio cannot be null");
        }
        return portfolioRepository.save(portfolio);
    }

    /**
     * Получает все портфели пользователя по идентификатору чата.
     *
     * @param chatId идентификатор чата пользователя
     * @return список портфелей пользователя
     * @throws org.springframework.dao.DataAccessException при ошибках доступа к данным
     */
    public List<Portfolio> getPortfoliosByChatId(String chatId) {
        return portfolioRepository.findByChatId(chatId);
    }

    public Mono<String> getPortfolioInfo(String portfolioId) {
        return Mono.fromCallable(() -> {
            Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("Portfolio not found"));

            StringBuilder response = new StringBuilder();
            response.append("📊 Информация о портфеле\n\n");

            if (portfolio.getCryptoCurrency() != null) {
                response.append("Криптовалюта: ").append(portfolio.getCryptoCurrency().getCode()).append("\n");
                response.append("Количество: ").append(portfolio.getCount()).append("\n");
                
                if (portfolio.getLastCryptoPrice() != null) {
                    response.append("Последняя известная цена: ")
                           .append(portfolio.getLastCryptoPrice())
                           .append("\n");
                }
            } else {
                response.append("Портфель пуст\n");
            }

            return response.toString();
        });
    }

//    public Mono<String> getPortfolioValue(String portfolioId) {
//        return Mono.fromCallable(() -> {
//            Portfolio portfolio = portfolioRepository.findById(portfolioId)
//                .orElseThrow(() -> new RuntimeException("Portfolio not found"));
//
//            if (portfolio.getCryptoCurrency() == null) {
//                return Mono.just("Портфель пуст");
//            }
//
//            return priceFetcher.getCurrentPrice(portfolio.getCryptoCurrency())
//                .flatMap(priceJson -> {
//                    try {
//                        JsonNode node = objectMapper.readTree(priceJson);
//                        BigDecimal currentPrice = new BigDecimal(node.get("price").asText());
//                        BigDecimal totalValue = portfolio.getCount().multiply(currentPrice);
//
//                        portfolio.setLastCryptoPrice(currentPrice);
//                        portfolio.setLastCryptoPriceTimestamp(System.currentTimeMillis() / 1000);
//                        portfolioRepository.save(portfolio);
//
//                        return Mono.just(String.format("💰 Стоимость портфеля: %.2f",
//                            totalValue));
//                    } catch (Exception e) {
//                        return Mono.error(e);
//                    }
//                });
//        }).flatMap(mono -> mono);
//    }

    public Portfolio save(Portfolio portfolio) {
        return portfolioRepository.save(portfolio);
    }

    public Mono<Void> delete(Portfolio portfolio) {
        return Mono.fromRunnable(() -> {
            try {
                portfolioRepository.delete(portfolio);
                log.info("Successfully deleted portfolio with ID: {}", portfolio.getId());
            } catch (Exception e) {
                log.error("Error deleting portfolio with ID {}: {}", portfolio.getId(), e.getMessage());
                throw new RuntimeException("Failed to delete portfolio", e);
            }
        });
    }

    public Optional<Portfolio> findById(String portfolioId) {
        return this.portfolioRepository.findById(portfolioId);
    }

    public List<Portfolio> findByChatId(String chatId) {
        return this.portfolioRepository.findByChatId(chatId);
    }

    public void deleteById(String testPortfolioId) {
        this.portfolioRepository.deleteById(testPortfolioId);
    }
}