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
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    @Transactional
    public void ensureSystemAccounts() {
        systemAccount(CreditAccount.ADRICORE_CONTROL, "Adricore Control");
        systemAccount(CreditAccount.ADRICORE_OPERATIONS, "Adricore Operations");
        systemAccount(CreditAccount.ADRICORE_INCOME, "Adricore Income");
    }

    private CreditAccount createZeroBalanceAccount(Partner partner) {
        try {
            CreditAccount account = new CreditAccount();
            account.setPartner(partner);
            account.setAccountName(partner.getName());
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

    private CreditAccount systemAccount(String code, String name) {
        return repository.findByAccountCode(code).orElseGet(() -> {
            try {
                CreditAccount account = new CreditAccount();
                account.setAccountCode(code);
                account.setAccountName(name);
                account.setBalance(BigDecimal.ZERO);
                account.setReserved(BigDecimal.ZERO);
                account.setCurrency("UGX");
                account.setUpdatedAt(Instant.now());
                return repository.saveAndFlush(account);
            } catch (DataIntegrityViolationException duplicate) {
                return repository.findByAccountCode(code).orElseThrow(() -> duplicate);
            }
        });
    }

    @Transactional
    public CreditTransaction topup(Partner partner, BigDecimal amount, String reference, Map<String, Object> meta) {
        if (partner == null || partner.getId() == null) {
            throw new IllegalArgumentException("Partner is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount paid must be greater than zero");
        }
        ensureSystemAccounts();
        CreditAccount partnerAccount = ensureFor(partner);
        CreditAccount control = systemAccount(CreditAccount.ADRICORE_CONTROL, "Adricore Control");
        return post(
                control.getId(),
                partnerAccount.getId(),
                amount,
                CreditTransaction.CreditTxType.TOPUP,
                partner,
                reference,
                meta,
                false
        );
    }

    @Transactional
    public CreditTransaction consume(Partner partner, BigDecimal amount, String reference, Map<String, Object> meta) {
        return consume(partner, amount, BigDecimal.ZERO, reference, meta);
    }

    @Transactional
    public CreditTransaction consume(
            Partner partner,
            BigDecimal amount,
            BigDecimal profit,
            String reference,
            Map<String, Object> meta
    ) {
        if (partner == null || partner.getId() == null) {
            throw new IllegalArgumentException("Partner is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount consumed must be greater than zero");
        }
        ensureSystemAccounts();
        CreditAccount partnerAccount = ensureFor(partner);
        CreditAccount operations = systemAccount(CreditAccount.ADRICORE_OPERATIONS, "Adricore Operations");
        CreditAccount income = systemAccount(CreditAccount.ADRICORE_INCOME, "Adricore Income");

        CreditTransaction charged = post(
                partnerAccount.getId(),
                operations.getId(),
                amount,
                CreditTransaction.CreditTxType.CONSUMPTION,
                partner,
                reference,
                meta,
                true
        );

        BigDecimal incomeAmount = profit == null ? BigDecimal.ZERO : profit;
        if (incomeAmount.compareTo(amount) > 0) {
            incomeAmount = amount;
        }
        if (incomeAmount.compareTo(BigDecimal.ZERO) > 0) {
            post(
                    operations.getId(),
                    income.getId(),
                    incomeAmount,
                    CreditTransaction.CreditTxType.INCOME,
                    partner,
                    reference,
                    incomeMeta(meta, amount, incomeAmount),
                    true
            );
        } else if (incomeAmount.compareTo(BigDecimal.ZERO) < 0) {
            post(
                    income.getId(),
                    operations.getId(),
                    incomeAmount.abs(),
                    CreditTransaction.CreditTxType.INCOME,
                    partner,
                    reference,
                    incomeMeta(meta, amount, incomeAmount),
                    false
            );
        }
        return charged;
    }

    public BigDecimal available(Partner partner) {
        if (partner == null || partner.getId() == null) {
            return BigDecimal.ZERO;
        }
        return repository.findByPartner_Id(partner.getId())
                .map(account -> account.getBalance() == null ? BigDecimal.ZERO : account.getBalance())
                .orElse(BigDecimal.ZERO);
    }

    public Map<String, Object> ledgerSnapshot() {
        ensureSystemAccounts();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("control", snapshot(systemAccount(CreditAccount.ADRICORE_CONTROL, "Adricore Control")));
        data.put("operations", snapshot(systemAccount(CreditAccount.ADRICORE_OPERATIONS, "Adricore Operations")));
        data.put("income", snapshot(systemAccount(CreditAccount.ADRICORE_INCOME, "Adricore Income")));
        return data;
    }

    private CreditTransaction post(
            UUID debitAccountId,
            UUID creditAccountId,
            BigDecimal amount,
            CreditTransaction.CreditTxType type,
            Partner partner,
            String reference,
            Map<String, Object> meta,
            boolean requireDebitFunds
    ) {
        if (debitAccountId.equals(creditAccountId)) {
            throw new IllegalArgumentException("Debit and credit accounts must be different");
        }
        List<UUID> lockOrder = List.of(debitAccountId, creditAccountId).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        CreditAccount first = lock(lockOrder.get(0));
        CreditAccount second = lock(lockOrder.get(1));
        CreditAccount debit = first.getId().equals(debitAccountId) ? first : second;
        CreditAccount credit = first.getId().equals(creditAccountId) ? first : second;

        BigDecimal debitCurrent = nz(debit.getBalance());
        if (requireDebitFunds && debitCurrent.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient credit on partner account");
        }
        BigDecimal debitAfter = debitCurrent.subtract(amount);
        BigDecimal creditAfter = nz(credit.getBalance()).add(amount);
        Instant now = Instant.now();

        debit.setBalance(debitAfter);
        debit.setUpdatedAt(now);
        credit.setBalance(creditAfter);
        credit.setUpdatedAt(now);
        repository.save(debit);
        repository.save(credit);

        BigDecimal partnerBalanceAfter = partnerBalanceAfter(debit, credit, debitAfter, creditAfter);
        CreditTransaction tx = new CreditTransaction();
        tx.setPartner(partner);
        tx.setDebitAccount(debit);
        tx.setCreditAccount(credit);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(partnerBalanceAfter != null ? partnerBalanceAfter : creditAfter);
        tx.setDebitBalanceAfter(debitAfter);
        tx.setCreditBalanceAfter(creditAfter);
        tx.setReference(StringUtils.hasText(reference) ? reference.trim() : null);
        tx.setMeta(meta);
        tx.setCreatedAt(now);
        return creditTransactionService.save(tx);
    }

    private CreditAccount lock(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Credit account not found"));
    }

    private BigDecimal partnerBalanceAfter(
            CreditAccount debit,
            CreditAccount credit,
            BigDecimal debitAfter,
            BigDecimal creditAfter
    ) {
        if (debit.getPartner() != null) {
            return debitAfter;
        }
        if (credit.getPartner() != null) {
            return creditAfter;
        }
        return null;
    }

    private Map<String, Object> snapshot(CreditAccount account) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", account.getId());
        data.put("code", account.getAccountCode());
        data.put("name", account.label());
        data.put("balance", nz(account.getBalance()));
        data.put("currency", account.getCurrency());
        return data;
    }

    private Map<String, Object> incomeMeta(Map<String, Object> source, BigDecimal charge, BigDecimal profit) {
        Map<String, Object> meta = source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
        meta.put("charge", charge);
        meta.put("profit", profit);
        meta.put("source", meta.getOrDefault("source", "sms_income"));
        return meta;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
