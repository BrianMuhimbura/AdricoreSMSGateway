package com.servicecops.project.integrations.sasula.payments.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "debitRequest", propOrder = {
        "amount",
        "callbackUrl",
        "channelCode",
        "channepPassword",
        "customerPhone",
        "externalTransactionId",
        "network",
        "reason",
        "targetAccount",
        "targetAccountPassword"
})
public class DebitRequest {
    @XmlElement(namespace = "")
    private BigDecimal amount;
    @XmlElement(namespace = "")
    private String callbackUrl;
    @XmlElement(namespace = "")
    private String channelCode;
    /** XSD field name is {@code channepPassword}. */
    @XmlElement(namespace = "")
    private String channepPassword;
    @XmlElement(namespace = "")
    private String customerPhone;
    @XmlElement(namespace = "")
    private String externalTransactionId;
    @XmlElement(namespace = "")
    private MmNetwork network;
    @XmlElement(namespace = "")
    private String reason;
    @XmlElement(namespace = "")
    private String targetAccount;
    @XmlElement(namespace = "")
    private String targetAccountPassword;
}
