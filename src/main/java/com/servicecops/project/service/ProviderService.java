package com.servicecops.project.service;

import com.servicecops.project.models.database.Provider;
import com.servicecops.project.repositories.ProviderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProviderService {
    private final ProviderRepository repository;

    public ProviderService(ProviderRepository repository) {
        this.repository = repository;
    }

    public List<Provider> findAll() { return repository.findAll(); }
    public Optional<Provider> findById(UUID id) { return repository.findById(id); }
    public Provider save(Provider provider) { return repository.save(provider); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

