package com.servicecops.project.service;

import com.servicecops.project.models.database.ApiCredential;
import com.servicecops.project.repositories.ApiCredentialRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiCredentialService {
    private final ApiCredentialRepository repository;

    public ApiCredentialService(ApiCredentialRepository repository) {
        this.repository = repository;
    }

    public List<ApiCredential> findAll() { return repository.findAll(); }
    public Optional<ApiCredential> findById(UUID id) { return repository.findById(id); }
    public ApiCredential save(ApiCredential credential) { return repository.save(credential); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

