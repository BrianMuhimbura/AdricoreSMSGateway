package com.servicecops.project.integrations.sasula.payments;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;
import org.springframework.ws.context.MessageContext;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
class SasulaSoapLoggingInterceptor extends ClientInterceptorAdapter {
    @Override
    public boolean handleRequest(MessageContext messageContext) {
        logPayload("request", messageContext.getRequest());
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) {
        logPayload("response", messageContext.getResponse());
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext) {
        logPayload("fault", messageContext.getResponse());
        return true;
    }

    private void logPayload(String kind, WebServiceMessage message) {
        if (!log.isInfoEnabled() || message == null) {
            return;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            String xml = maskSecrets(out.toString(StandardCharsets.UTF_8));
            log.info("Sasula SOAP {}: {}", kind, xml);
        } catch (Exception error) {
            log.info("Unable to log Sasula SOAP {}: {}", kind, error.getMessage());
        }
    }

    private String maskSecrets(String xml) {
        return xml.replaceAll("(?is)(<channepPassword>)(.*?)(</channepPassword>)", "$1***$3");
    }
}
