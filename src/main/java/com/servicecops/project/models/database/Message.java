package com.servicecops.project.models.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY)
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
    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "message_status")
    private MessageStatus status;

    @Column(name = "upstream_id")
    private String upstreamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(name = "callback_url")
    private String callbackUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response", columnDefinition = "jsonb")
    private String providerResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum MessageStatus {
        QUEUED, SENDING, SENT, ACCEPTED, DELIVERED, FAILED, REJECTED, EXPIRED
    }
}
