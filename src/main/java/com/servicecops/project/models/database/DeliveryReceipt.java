package com.servicecops.project.models.database;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_receipts")
public class DeliveryReceipt {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "message_id")
    private Message message;

    @Column(name = "provider_status")
    private String providerStatus;

    @Column(name = "provider_code")
    private String providerCode;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    // Getters and setters
}

