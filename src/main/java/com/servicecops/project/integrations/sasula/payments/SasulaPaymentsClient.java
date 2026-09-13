package com.servicecops.project.integrations.sasula.payments;

import com.servicecops.project.integrations.sasula.payments.dto.BusinessDebitRequest;
import com.servicecops.project.integrations.sasula.payments.dto.BusinessPaymentRequest;
import com.servicecops.project.integrations.sasula.payments.dto.BusinessPaymentRequestResponse;
import com.servicecops.project.integrations.sasula.payments.dto.DebitRequest;
import com.servicecops.project.integrations.sasula.payments.dto.DebitResponse;
import com.servicecops.project.integrations.sasula.payments.dto.PaymentRequest;
import com.servicecops.project.integrations.sasula.payments.dto.PaymentRequestResponse;
import com.servicecops.project.integrations.sasula.payments.dto.PaymentStatus;
import com.servicecops.project.integrations.sasula.payments.dto.PaymentStatusResponse;
import com.servicecops.project.integrations.sasula.payments.dto.RequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.client.core.SoapActionCallback;

/**
 * SOAP client for Sasula MMBridgeWS.
 *
 * <pre>{@code
 * DebitResponse response = sasulaPaymentsClient.paymentRequest(
 *     DebitRequest.builder()
 *         .amount(new BigDecimal("10000"))
 *         .customerPhone("256700000000")
 *         .channelCode("CHANNEL")
 *         .channepPassword("secret")
 *         .externalTransactionId("TX-1")
 *         .network(MmNetwork.MTN)
 *         .build()
 * );
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SasulaPaymentsClient {
    private static final SoapActionCallback SOAP_ACTION = new SoapActionCallback("");

    private final WebServiceTemplate sasulaPaymentsWebServiceTemplate;
    private final SasulaPaymentsProperties properties;

    public DebitResponse paymentRequest(DebitRequest debitPaymentRequest) {
        if (debitPaymentRequest == null) {
            throw new IllegalArgumentException("debitPaymentRequest cannot be null");
        }
        return paymentRequest(PaymentRequest.builder().debitPaymentRequest(debitPaymentRequest).build());
    }

    public DebitResponse paymentRequest(PaymentRequest request) {
        require(request, "PaymentRequest");
        if (properties.isSimulate()) {
            DebitRequest debit = request.getDebitPaymentRequest();
            return simulated(debit != null ? debit.getExternalTransactionId() : null, RequestStatus.REQUESTED);
        }
        PaymentRequestResponse response = invoke("PaymentRequest", request, PaymentRequestResponse.class);
        return requireReturn(response.getReturnValue(), "PaymentRequest");
    }

    public DebitResponse businessPaymentRequest(BusinessDebitRequest debitRequest) {
        if (debitRequest == null) {
            throw new IllegalArgumentException("debitRequest cannot be null");
        }
        return businessPaymentRequest(BusinessPaymentRequest.builder().debitRequest(debitRequest).build());
    }

    public DebitResponse businessPaymentRequest(BusinessPaymentRequest request) {
        require(request, "BusinessPaymentRequest");
        if (properties.isSimulate()) {
            BusinessDebitRequest debit = request.getDebitRequest();
            return simulated(debit != null ? debit.getExternalTransactionId() : null, RequestStatus.REQUESTED);
        }
        BusinessPaymentRequestResponse response = invoke(
                "BusinessPaymentRequest",
                request,
                BusinessPaymentRequestResponse.class
        );
        return requireReturn(response.getReturnValue(), "BusinessPaymentRequest");
    }

    public DebitResponse paymentStatus(DebitRequest debitPaymentStatus) {
        if (debitPaymentStatus == null) {
            throw new IllegalArgumentException("debitPaymentStatus cannot be null");
        }
        return paymentStatus(PaymentStatus.builder().debitPaymentStatus(debitPaymentStatus).build());
    }

    public DebitResponse paymentStatus(PaymentStatus request) {
        require(request, "PaymentStatus");
        if (properties.isSimulate()) {
            DebitRequest debit = request.getDebitPaymentStatus();
            return simulated(debit != null ? debit.getExternalTransactionId() : null, RequestStatus.SUCCESSFUL);
        }
        PaymentStatusResponse response = invoke("PaymentStatus", request, PaymentStatusResponse.class);
        return requireReturn(response.getReturnValue(), "PaymentStatus");
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String operation, Object payload, Class<T> responseType) {
        String endpoint = properties.endpointUrl();
        log.debug("Calling Sasula {} at {}", operation, endpoint);
        try {
            Object response = sasulaPaymentsWebServiceTemplate.marshalSendAndReceive(endpoint, payload, SOAP_ACTION);
            if (response == null) {
                throw new SasulaPaymentsException(operation + " returned an empty SOAP body");
            }
            if (!responseType.isInstance(response)) {
                throw new SasulaPaymentsException(
                        operation + " returned unexpected type " + response.getClass().getName()
                );
            }
            return (T) response;
        } catch (SoapFaultClientException fault) {
            String reason = fault.getFaultStringOrReason();
            log.warn("Sasula {} SOAP fault: {}", operation, reason);
            throw new SasulaPaymentsException(operation + " SOAP fault: " + reason, fault);
        } catch (SasulaPaymentsException error) {
            throw error;
        } catch (Exception error) {
            throw new SasulaPaymentsException("Unable to call Sasula " + operation, error);
        }
    }

    private DebitResponse simulated(String externalTransactionId, RequestStatus status) {
        String reference = (externalTransactionId == null || externalTransactionId.isBlank())
                ? "SIMULATED"
                : externalTransactionId;
        log.info("Simulating Sasula {} for {} with returnCode 0", status, reference);
        return DebitResponse.builder()
                .returnCode(0)
                .returnMessage("Simulated success")
                .status(status)
                .externalChannelPaymentReference(reference)
                .mmTransactionId("SIM-" + reference)
                .processingNumber("SIM")
                .finalProcessMessage("Simulated mobile money " + status)
                .build();
    }

    private void require(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    private DebitResponse requireReturn(DebitResponse value, String operation) {
        if (value == null) {
            throw new SasulaPaymentsException(operation + " response did not include a return payload");
        }
        return value;
    }
}
