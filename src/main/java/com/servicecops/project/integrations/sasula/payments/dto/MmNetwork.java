package com.servicecops.project.integrations.sasula.payments.dto;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/** Matches the XSD type name {@code mmNework}. */
@XmlType(name = "mmNework")
@XmlEnum
public enum MmNetwork {
    MTN,
    AIRTEL,
    AIRTEL_OPEN_API,
    MTN_OPEN_API
}
