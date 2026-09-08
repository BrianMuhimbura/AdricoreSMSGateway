package com.servicecops.project.service;

import com.servicecops.project.models.database.CreditAccount;
import com.servicecops.project.models.database.CreditTransaction;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.repositories.CreditAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditAccountService {
    private final CreditAccountRepository repository;
    private final CreditTransactionService creditTransactionService;

    public CreditAccountService(CreditAccountRepository repository, CreditTransactionService creditTransactionService) {
        this.repository = repository;
        this.creditTransactionService = creditTransactionService;
    }

    public List<CreditAccount> findAll() { return repository.findAll(); }
    public Optional<CreditAccount> findById(UUID id) { return repository.findById(id); }
    public CreditAccount save(CreditAccount account) { return repository.save(account); }
    public void deleteById(UUID id) { repository.deleteById(id); }
    public Optional<CreditAccount> findByPartner(Partner partner) {
        return repository.findByPartner(partner);
    }

    @Transactional
    public CreditAccount ensureFor(Partner partner) {
        if (partner == null || partner.getId() == null) {
            throw new IllegalArgumentException("Partner is required");
        }
        return repository.findByPartner_Id(partner.getId()).orElseGet(() -> createZeroBalanceAccount(partner));
    }

    private CreditAccount createZeroBalanceAccount(Partner partner) {
        try {
            CreditAccount account = new CreditAccount();
            account.setPartner(partner);
            account.setBalance(BigDecimal.ZERO);
            account.setReserved(BigDecimal.ZERO);
            account.setCurrency(partner.getCurrency() == null || partner.getCurrency().isBlank()
                    ? "UGX"
                    : partner.getCurrency());
            account.setUpdatedAt(Instant.now());
            return repository.saveAndFlush(account);
        } catch (DataIntegrityViolationException duplicate) {
            return repository.findByPartner_Id(partner.getId()).orElseThrow(() -> duplicate);
        }
    }

    @Transactional
    public CreditTransaction topup(Partner partner, BigDecimal amount, String reference, Map<String, Object> meta) {
        if (partner == null || partner.getId() == null) {
            throw new IllegalArgumentException("Partner is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount paid must be greater than zero");
        }
        ensureFor(partner);
        CreditAccount account = repository.findByPartnerIdForUpdate(partner.getId())
                .orElseThrow(() -> new IllegalStateException("Credit account not found for partner"));
        BigDecimal current = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        BigDecimal newBalance = current.add(amount);
        account.setBalance(newBalance);
        account.setUpdatedAt(Instant.now());
        repository.save(account);

        CreditTransaction tx = new CreditTransaction();
        tx.setPartner(partner);
        tx.setType(CreditTransaction.CreditTxType.TOPUP);
        tx.setAmount(amount);
        tx.setBalanceAfter(newBalance);
        tx.setReference(StringUtils.hasText(reference) ? reference.trim() : null);
        tx.setMeta(meta);
        tx.setCreatedAt(Instant.now());
        return creditTransactionService.save(tx);
    }
}
