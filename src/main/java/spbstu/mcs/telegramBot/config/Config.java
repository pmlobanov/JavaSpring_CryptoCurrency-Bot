package spbstu.mcs.telegramBot.config;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.Map;

public class Config {
    private static final Map<String, Object> config;

    static {
        Yaml yaml = new Yaml();
        InputStream inputStream = Config.class
                .getClassLoader()
                .getResourceAsStream("application.yml");
        config = yaml.load(inputStream);
    }

    public static String getTelegramBotToken() {
        return getNestedValue("telegram", "bot", "token");
    }

    public static String getTelegramBotUsername() {
        return getNestedValue("telegram", "bot", "username");
    }

    public static String getKafkaBootstrapServers() {
        return getNestedValue("kafka", "bootstrap-servers");
    }

    public static String getKafkaConsumerGroupId() {
        return getNestedValue("kafka", "consumer", "group-id");
    }

    public static String getKafkaIncomingTopic() {
        return getNestedValue("kafka", "topics", "incoming");
    }

    public static String getKafkaOutgoingTopic() {
        return getNestedValue("kafka", "topics", "outgoing");
    }

    @SuppressWarnings("unchecked")
    private static String getNestedValue(String... keys) {
        Map<String, Object> current = config;
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.get(keys[i]);
            if (current == null) {
                throw new RuntimeException("Config key not found: " + keys[i]);
            }
        }
        return (String) current.get(keys[keys.length - 1]);
    }
}