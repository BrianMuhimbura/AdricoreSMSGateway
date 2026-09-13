package com.servicecops.project.integrations.sasula.payments.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "debitResponse", propOrder = {
        "externalChannelPaymentReference",
        "finalProcessMessage",
        "mmTransactionId",
        "processingNumber",
        "requestId",
        "status",
        "uuid"
})
public class DebitResponse extends OperationReturn {
    @XmlElement(namespace = "")
    private String externalChannelPaymentReference;
    @XmlElement(namespace = "")
    private String finalProcessMessage;
    @XmlElement(namespace = "")
    private String mmTransactionId;
    @XmlElement(namespace = "")
    private String processingNumber;
    @XmlElement(namespace = "")
    private Long requestId;
    @XmlElement(namespace = "")
    private RequestStatus status;
    @XmlElement(namespace = "")
    private String uuid;
}
