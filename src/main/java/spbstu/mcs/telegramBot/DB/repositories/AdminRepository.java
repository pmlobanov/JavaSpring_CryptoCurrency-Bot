package spbstu.mcs.telegramBot.DB.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import spbstu.mcs.telegramBot.model.Admin;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminRepository extends MongoRepository<Admin, String> {
    List<Admin> findAll();
    Optional<Admin> findByUsername(String username);
} 