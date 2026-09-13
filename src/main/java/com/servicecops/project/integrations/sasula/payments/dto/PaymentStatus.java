package com.servicecops.project.integrations.sasula.payments.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name = "PaymentStatus", namespace = SasulaPaymentsNamespace.URI)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PaymentStatus", propOrder = {"debitPaymentStatus"})
public class PaymentStatus {
    @XmlElement(namespace = "")
    private DebitRequest debitPaymentStatus;
}
