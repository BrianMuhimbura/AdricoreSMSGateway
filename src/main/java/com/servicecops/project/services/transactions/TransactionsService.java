package com.servicecops.project.services.transactions;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.CreditAccount;
import com.servicecops.project.models.database.CreditTransaction;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.repositories.CreditTransactionRepository;
import com.servicecops.project.service.CreditAccountService;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.utils.OperationReturnObject;
import com.servicecops.project.utils.PagedQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class TransactionsService extends BaseWebActionsService {
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "amount", "balanceAfter", "type", "reference");

    private final CreditTransactionRepository creditTransactionRepository;
    private final CreditAccountService creditAccountService;

    protected OperationReturnObject list(JSONObject request) {
        UUID scopedPartnerId = authorizeAndScope();
        PagedQuery.Request pageRequest = PagedQuery.from(request, "createdAt", -1);
        String status = request == null ? null : request.getString("status");
        Specification<CreditTransaction> spec = transactionSpec(pageRequest.query(), status, scopedPartnerId);
        Page<CreditTransaction> page = creditTransactionRepository.findAll(
                spec,
                PagedQuery.toPageable(pageRequest, SORT_FIELDS, "createdAt")
        );

        Map<String, Object> payload = PagedQuery.payload(
                page.getContent().stream().map(this::toPublicTransaction).toList(),
                page,
                pageRequest
        );
        payload.put("totalCount", creditTransactionRepository.count(transactionSpec(null, "all", scopedPartnerId)));
        payload.put("topupCount", creditTransactionRepository.count(transactionSpec(null, "TOPUP", scopedPartnerId)));
        payload.put("consumptionCount", creditTransactionRepository.count(transactionSpec(null, "CONSUMPTION", scopedPartnerId)));
        payload.put("incomeCount", creditTransactionRepository.count(transactionSpec(null, "INCOME", scopedPartnerId)));
        if (scopedPartnerId == null) {
            payload.put("ledger", creditAccountService.ledgerSnapshot());
        }
        return success("Transactions found", payload);
    }

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        return switch (action) {
            case "list" -> list(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private UUID authorizeAndScope() {
        requiresAuth();
        SystemUserModel user = authenticatedUser();
        can(user.getUsername(), "VIEW_TRANSACTIONS");
        return user.getPartnerId();
    }

    private Specification<CreditTransaction> transactionSpec(String query, String status, UUID partnerId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<CreditTransaction, Partner> partner = root.join("partner", partnerId != null ? JoinType.INNER : JoinType.LEFT);
            Join<CreditTransaction, CreditAccount> debit = root.join("debitAccount", JoinType.LEFT);
            Join<CreditTransaction, CreditAccount> credit = root.join("creditAccount", JoinType.LEFT);
            if (partnerId != null) {
                predicates.add(cb.equal(partner.get("id"), partnerId));
                predicates.add(cb.notEqual(root.get("type"), CreditTransaction.CreditTxType.INCOME));
            }
            CreditTransaction.CreditTxType type = typeFor(status);
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("reference"), "")), like),
                        cb.like(cb.lower(cb.coalesce(partner.get("name"), "")), like),
                        cb.like(cb.lower(cb.coalesce(partner.get("partnerCode"), "")), like),
                        cb.like(cb.lower(cb.coalesce(debit.get("accountCode"), "")), like),
                        cb.like(cb.lower(cb.coalesce(debit.get("accountName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(credit.get("accountCode"), "")), like),
                        cb.like(cb.lower(cb.coalesce(credit.get("accountName"), "")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private CreditTransaction.CreditTxType typeFor(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status.trim())) {
            return null;
        }
        try {
            return CreditTransaction.CreditTxType.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown transaction type");
        }
    }

    private Map<String, Object> toPublicTransaction(CreditTransaction tx) {
        Partner partner = tx.getPartner();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", tx.getId());
        data.put("partnerId", partner == null ? null : partner.getId());
        data.put("partnerCode", partner == null ? null : partner.getPartnerCode());
        data.put("partnerName", partner == null ? null : partner.getName());
        data.put("type", tx.getType() == null ? null : tx.getType().name());
        data.put("amount", tx.getAmount());
        data.put("balanceAfter", tx.getBalanceAfter());
        data.put("debitAccount", toPublicAccount(tx.getDebitAccount()));
        data.put("creditAccount", toPublicAccount(tx.getCreditAccount()));
        data.put("debitBalanceAfter", tx.getDebitBalanceAfter());
        data.put("creditBalanceAfter", tx.getCreditBalanceAfter());
        data.put("reference", tx.getReference());
        data.put("createdAt", tx.getCreatedAt());
        return data;
    }

    private Map<String, Object> toPublicAccount(CreditAccount account) {
        if (account == null) {
            return null;
        }
        Partner partner = account.getPartner();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", account.getId());
        data.put("code", account.getAccountCode());
        data.put("name", account.label());
        data.put("partnerCode", partner == null ? null : partner.getPartnerCode());
        return data;
    }

    private OperationReturnObject success(String message, Object payload) {
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, message, payload);
        return res;
    }
}
