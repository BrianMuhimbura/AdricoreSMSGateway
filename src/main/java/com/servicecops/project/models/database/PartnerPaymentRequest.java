package com.servicecops.project.models.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "payment_requests")
public class PartnerPaymentRequest {
    public static final String PENDING = "PENDING";
    public static final String REQUESTED = "REQUESTED";
    public static final String SUCCESSFUL = "SUCCESSFUL";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private BillingInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal amount;

    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    @Column(name = "external_transaction_id", nullable = false, unique = true, length = 80)
    private String externalTransactionId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "sasula_request_id")
    private Long sasulaRequestId;

    @Column(name = "mm_transaction_id")
    private String mmTransactionId;

    @Column(name = "processing_number")
    private String processingNumber;

    @Column(name = "return_code")
    private Integer returnCode;

    @Column(name = "return_message")
    private String returnMessage;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "credited_at")
    private Instant creditedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
