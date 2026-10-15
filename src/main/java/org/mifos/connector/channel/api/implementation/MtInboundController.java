package org.mifos.connector.channel.api.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.mifos.connector.channel.zeebe.ZeebeProcessStarter;
import org.mifos.connector.channel.zeebe.ZeebeVariables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts one Monétique Tunisie BPMN per inbound operation. mt-api is the only public entry:
 * the card switch never calls this connector directly.
 */
@RestController
@RequestMapping("/channel/mt")
public class MtInboundController {

    private final ZeebeProcessStarter starter;
    private final ZeebeClient zeebeClient;
    private final ObjectMapper objectMapper;
    private final List<String> dfspIds;

    public MtInboundController(ZeebeProcessStarter starter, ZeebeClient zeebeClient, ObjectMapper objectMapper,
            @Value("#{'${dfspids}'.split(',')}") List<String> dfspIds) {
        this.starter = starter;
        this.zeebeClient = zeebeClient;
        this.objectMapper = objectMapper;
        this.dfspIds = dfspIds;
    }

    @PostMapping("/inbound")
    public Map<String, String> inbound(@RequestHeader("Platform-TenantId") String tenant, @RequestBody Map<String, Object> body)
            throws Exception {
        if (tenant == null || !dfspIds.contains(tenant)) {
            throw new IllegalArgumentException("Requested tenant " + tenant + " not configured in the connector");
        }
        String operation = text(body.get("operation")).toUpperCase(Locale.ROOT);
        String processId = switch (operation) {
            case "CAPTURE" -> "mt_inbound_capture-" + tenant;
            case "VOID" -> "mt_inbound_void-" + tenant;
            case "REFUND" -> "mt_inbound_refund-" + tenant;
            default -> "mt_inbound_authorization-" + tenant;
        };
        String amount = text(body.getOrDefault("amount", "0"));
        String currency = text(body.getOrDefault("currency", "TND"));
        String pan = text(first(body.get("pan"), body.get("panToken")));
        String merchant = text(body.get("merchantId"));
        Map<String, Object> channelRequest = channelRequest(text(body.get("cardholderName")), pan, merchant, amount, currency);
        Map<String, Object> variables = new HashMap<>();
        variables.put(ZeebeVariables.TENANT_ID, tenant);
        variables.put("payeeTenantId", tenant);
        variables.put("currency", currency);
        variables.put("amount", amount);
        variables.put("pan", pan);
        variables.put("panToken", pan);
        variables.put("merchantId", merchant);
        variables.put("operation", operation);
        variables.put("clientReference", text(body.get("clientReference")));
        variables.put("ecommerce", ecommerce(body));
        variables.put("partyIdType", "ACCOUNT_ID");
        variables.put("partyId", pan);
        variables.put(ZeebeVariables.CHANNEL_REQUEST, objectMapper.writeValueAsString(channelRequest));
        String transactionId = starter.startZeebeWorkflow(processId, objectMapper.writeValueAsString(channelRequest), variables);
        return Map.of("transactionId", transactionId);
    }

    @PostMapping("/decision")
    public Map<String, Object> decision(@RequestBody Map<String, Object> body) {
        String transactionId = text(body.get("transactionId"));
        if (transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
        boolean accepted = Boolean.TRUE.equals(body.get("accepted")) || "true".equalsIgnoreCase(text(body.get("accepted")));
        Map<String, Object> variables = new HashMap<>();
        variables.put("cardholderRejected", !accepted);
        variables.put("cardholderDecision", accepted ? "ACCEPTED" : "REJECTED");
        zeebeClient.newPublishMessageCommand().messageName("cardholderDecision").correlationKey(transactionId).variables(variables)
                .send().join();
        return Map.of("published", true, "transactionId", transactionId, "accepted", accepted);
    }

    private static Map<String, Object> channelRequest(String holder, String pan, String merchant, String amount, String currency) {
        Map<String, Object> payer = new LinkedHashMap<>();
        payer.put("name", holder.isBlank() ? "Cardholder" : holder);
        payer.put("partyIdInfo", Map.of("partyIdType", "ACCOUNT_ID", "partyIdentifier", pan.isBlank() ? "UNKNOWN" : pan, "fspId", "nexusbank"));
        Map<String, Object> payee = new LinkedHashMap<>();
        payee.put("name", merchant.isBlank() ? "Merchant" : merchant);
        payee.put("partyIdInfo", Map.of("partyIdType", "ALIAS", "partyIdentifier", merchant.isBlank() ? "merchant" : merchant, "fspId", "mt"));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("payer", payer);
        request.put("payee", payee);
        request.put("amount", Map.of("amount", amount, "currency", currency));
        request.put("note", "Monétique Tunisie card payment");
        return request;
    }

    private static boolean ecommerce(Map<String, Object> body) {
        if (Boolean.TRUE.equals(body.get("ecommerce"))) {
            return true;
        }
        String mode = text(first(body.get("entryMode"), body.get("channel"), body.get("posEntryMode"))).toUpperCase(Locale.ROOT);
        return mode.equals("ECOM") || mode.equals("ECOMMERCE") || mode.equals("ONLINE") || mode.equals("CNP");
    }

    private static Object first(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
