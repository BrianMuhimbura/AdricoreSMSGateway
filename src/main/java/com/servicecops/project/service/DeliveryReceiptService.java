package com.servicecops.project.service;

import com.servicecops.project.models.database.DeliveryReceipt;
import com.servicecops.project.repositories.DeliveryReceiptRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeliveryReceiptService {
    private final DeliveryReceiptRepository repository;

    public DeliveryReceiptService(DeliveryReceiptRepository repository) {
        this.repository = repository;
    }

    public List<DeliveryReceipt> findAll() { return repository.findAll(); }
    public Optional<DeliveryReceipt> findById(UUID id) { return repository.findById(id); }
    public DeliveryReceipt save(DeliveryReceipt receipt) { return repository.save(receipt); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

