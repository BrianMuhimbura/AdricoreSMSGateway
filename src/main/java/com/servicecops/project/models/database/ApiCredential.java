package com.servicecops.project.models.database;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_credentials")
public class ApiCredential {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @Column(name = "api_key", unique = true, nullable = false)
    private String apiKey;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "allowed_ips")
    private String[] allowedIps;

    @Column(name = "callback_url")
    private String callbackUrl;

    @Column(columnDefinition = "jsonb")
    private String meta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // Getters and setters
}

