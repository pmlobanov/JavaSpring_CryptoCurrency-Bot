package spbstu.mcs.telegramBot.DB.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import spbstu.mcs.telegramBot.DB.repositories.PortfolioRepository;
import spbstu.mcs.telegramBot.DB.repositories.UserRepository;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.Portfolio;
import spbstu.mcs.telegramBot.model.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

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
    private final UserRepository userRepository;
    private final UserService userService;
 //   private final PriceFetcher priceFetcher;
//    private final CurrencyConverter currencyConverter;
    private final ObjectMapper objectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    public PortfolioService(PortfolioRepository portfolioRepository, 
                          UserRepository userRepository,
                          @Lazy UserService userService,
                         // PriceFetcher priceFetcher,
                          //CurrencyConverter currencyConverter,
                          ObjectMapper objectMapper) {
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    //    this.priceFetcher = priceFetcher;
    //    this.currencyConverter = currencyConverter;
        this.objectMapper = objectMapper;
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
                // Добавляем portfolioId пользователю и обновляем userTgName
                return userService.addPortfolioToUser(chatId, savedPortfolio.getId(), user.getUserTgName())
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

    public List<Portfolio> getUserPortfolios(String userTgName) {
        User user = userRepository.findByUserTgName(userTgName);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return portfolioRepository.findAllById(user.getPortfolioIds());
    }

    public Portfolio addCryptoToPortfolio(String portfolioId, Currency.Crypto crypto, BigDecimal amount) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Portfolio not found"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (portfolio.getCryptoCurrency() == null || portfolio.getCryptoCurrency() == crypto) {
            portfolio.setCryptoCurrency(crypto);
            portfolio.setCount(portfolio.getCount() == null ? amount : portfolio.getCount().add(amount));
        } else {
            throw new IllegalArgumentException("Portfolio already contains a different cryptocurrency");
        }

        return portfolioRepository.save(portfolio);
    }

    public Portfolio removeCryptoFromPortfolio(String portfolioId, Currency.Crypto crypto, BigDecimal amount) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Portfolio not found"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (portfolio.getCryptoCurrency() != crypto) {
            throw new IllegalArgumentException("Portfolio does not contain this cryptocurrency");
        }

        BigDecimal currentAmount = portfolio.getCount();
        if (currentAmount.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient amount");
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

    public Portfolio delete(Portfolio portfolio) {
        portfolioRepository.delete(portfolio);
        return portfolio;
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