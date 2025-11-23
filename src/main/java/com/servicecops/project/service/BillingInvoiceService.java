package com.servicecops.project.service;

import com.servicecops.project.models.database.BillingInvoice;
import com.servicecops.project.repositories.BillingInvoiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingInvoiceService {
    private final BillingInvoiceRepository repository;

    public BillingInvoiceService(BillingInvoiceRepository repository) {
        this.repository = repository;
    }

    public List<BillingInvoice> findAll() { return repository.findAll(); }
    public Optional<BillingInvoice> findById(UUID id) { return repository.findById(id); }
    public BillingInvoice save(BillingInvoice invoice) { return repository.save(invoice); }
    public void deleteById(UUID id) { repository.deleteById(id); }
}

