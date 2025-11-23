package com.servicecops.project.service;

import com.servicecops.project.models.database.Partner;
import com.servicecops.project.repositories.PartnerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PartnerService {
    private final PartnerRepository repository;

    public PartnerService(PartnerRepository repository) {
        this.repository = repository;
    }

    public List<Partner> findAll() { return repository.findAll(); }
    public Optional<Partner> findById(UUID id) { return repository.findById(id); }
    public Partner save(Partner partner) { return repository.save(partner); }
    public void deleteById(UUID id) { repository.deleteById(id); }

    public Optional<Partner> findByPartnerCode(String partnerCode) {
        return repository.findByPartnerCode(partnerCode);
    }
}

