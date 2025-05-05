package ru.spbstu.telematics.java.DB.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.spbstu.telematics.java.DB.collections.ApiKey;

public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {
}
