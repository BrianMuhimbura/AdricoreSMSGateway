package com.servicecops.project.integrations.sasula.payments;

import com.servicecops.project.integrations.sasula.payments.dto.BusinessDebitRequest;
import com.servicecops.project.integrations.sasula.payments.dto.BusinessPaymentRequest;
import com.servicecops.project.integrations.sasula.payments.dto.BusinessPaymentRequestResponse;
import com.servicecops.project.integrations.sasula.payments.dto.DebitRequest;
import com.servicecops.project.integrations.sasula.payments.dto.DebitResponse;
import com.servicecops.project.integrations.sasula.payments.dto.MmNetwork;
import com.servicecops.project.integrations.sasula.payments.dto.OperationReturn;
import com.servicecops.project.integrations.sasula.payments.dto.PaymentRequest;
import com.servicecops.project.integrations.sasula.payments.dto.PaymentRequestResponse;
import com.servicecops.project.integrations.sasula.payments.dto.PaymentStatus;
import com.servicecops.project.integrations.sasula.payments.dto.PaymentStatusResponse;
import com.servicecops.project.integrations.sasula.payments.dto.RequestStatus;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(SasulaPaymentsProperties.class)
public class SasulaPaymentsConfig {

    @Bean
    public Jaxb2Marshaller sasulaPaymentsMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
                OperationReturn.class,
                DebitRequest.class,
                BusinessDebitRequest.class,
                DebitResponse.class,
                PaymentRequest.class,
                PaymentRequestResponse.class,
                BusinessPaymentRequest.class,
                BusinessPaymentRequestResponse.class,
                PaymentStatus.class,
                PaymentStatusResponse.class,
                RequestStatus.class,
                MmNetwork.class
        );
        return marshaller;
    }

    @Bean
    public WebServiceTemplate sasulaPaymentsWebServiceTemplate(
            Jaxb2Marshaller sasulaPaymentsMarshaller,
            SasulaPaymentsProperties properties
    ) {
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(Duration.ofSeconds(20));
        sender.setReadTimeout(Duration.ofSeconds(90));

        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(sasulaPaymentsMarshaller);
        template.setUnmarshaller(sasulaPaymentsMarshaller);
        template.setDefaultUri(properties.endpointUrl());
        template.setMessageSender(sender);
        template.setInterceptors(new ClientInterceptor[]{new SasulaSoapLoggingInterceptor()});
        return template;
    }
}
