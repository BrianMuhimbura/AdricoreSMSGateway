package com.servicecops.project.service;

import com.servicecops.project.models.database.SmsPricingProfile;
import com.servicecops.project.repositories.SmsPricingProfileRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SmsPricingProfileService {
    private final SmsPricingProfileRepository repository;

    public SmsPricingProfileService(SmsPricingProfileRepository repository) {
        this.repository = repository;
    }

    public List<SmsPricingProfile> findAll() { return repository.findAll(); }
    public Optional<SmsPricingProfile> findById(UUID id) { return repository.findById(id); }
    public SmsPricingProfile save(SmsPricingProfile profile) { return repository.save(profile); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

