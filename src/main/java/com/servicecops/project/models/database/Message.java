package com.servicecops.project.models.database;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @ManyToOne
    @JoinColumn(name = "credential_id")
    private ApiCredential credential;

    @Column(name = "from_addr")
    private String fromAddr;

    @Column(name = "to_addr", nullable = false)
    private String toAddr;

    @Column(nullable = false)
    private String text;

    private String encoding;
    private Integer segments;
    private java.math.BigDecimal cost;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    @Column(name = "upstream_id")
    private String upstreamId;

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(name = "callback_url")
    private String callbackUrl;

    @Column(name = "provider_response", columnDefinition = "jsonb")
    private String providerResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum MessageStatus {
        QUEUED, SENDING, SENT, ACCEPTED, DELIVERED, FAILED, REJECTED, EXPIRED
    }

    // Getters and setters
}

