package com.servicecops.project.models.database;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class Route {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "provider_id")
    private Provider provider;

    private String name;

    @Column(nullable = false)
    private int priority;

    @Column(name = "filter_rules", columnDefinition = "jsonb")
    private String filterRules;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Getters and setters
}

