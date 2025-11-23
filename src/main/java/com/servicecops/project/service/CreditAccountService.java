package com.servicecops.project.service;

import com.servicecops.project.models.database.CreditAccount;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.repositories.CreditAccountRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditAccountService {
    private final CreditAccountRepository repository;

    public CreditAccountService(CreditAccountRepository repository) {
        this.repository = repository;
    }

    public List<CreditAccount> findAll() { return repository.findAll(); }
    public Optional<CreditAccount> findById(UUID id) { return repository.findById(id); }
    public CreditAccount save(CreditAccount account) { return repository.save(account); }
    public void deleteById(UUID id) { repository.deleteById(id); }
    public Optional<CreditAccount> findByPartner(Partner partner) {
        return repository.findByPartner(partner);
    }
}
