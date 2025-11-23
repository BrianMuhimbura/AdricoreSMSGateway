package com.servicecops.project.service;

import com.servicecops.project.models.database.Organization;
import com.servicecops.project.repositories.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrganizationService {
    private final OrganizationRepository repository;

    public OrganizationService(OrganizationRepository repository) {
        this.repository = repository;
    }

    public List<Organization> findAll() { return repository.findAll(); }
    public Optional<Organization> findById(UUID id) { return repository.findById(id); }
    public Organization save(Organization org) { return repository.save(org); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

