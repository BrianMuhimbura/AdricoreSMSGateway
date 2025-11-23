package com.servicecops.project.service;

import com.servicecops.project.models.database.CreditTransaction;
import com.servicecops.project.repositories.CreditTransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditTransactionService {
    private final CreditTransactionRepository repository;

    public CreditTransactionService(CreditTransactionRepository repository) {
        this.repository = repository;
    }

    public List<CreditTransaction> findAll() { return repository.findAll(); }
    public Optional<CreditTransaction> findById(UUID id) { return repository.findById(id); }
    public CreditTransaction save(CreditTransaction tx) { return repository.save(tx); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

