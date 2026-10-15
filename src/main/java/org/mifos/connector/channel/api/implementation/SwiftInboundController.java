package org.mifos.connector.channel.api.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Starts one SWIFT BPMN per inbound message. swift-api is the only public entry: the SWIFT network never calls this
 * connector directly.
 */
@RestController
@RequestMapping("/channel/swift")
public class SwiftInboundController {

    private final ZeebeProcessStarter starter;
    private final ObjectMapper objectMapper;
    private final List<String> dfspIds;

    public SwiftInboundController(ZeebeProcessStarter starter, ObjectMapper objectMapper,
            @Value("#{'${dfspids}'.split(',')}") List<String> dfspIds) {
        this.starter = starter;
        this.objectMapper = objectMapper;
        this.dfspIds = dfspIds;
    }

    @PostMapping("/inbound")
    public Map<String, String> inbound(@RequestHeader("Platform-TenantId") String tenant, @RequestBody Map<String, Object> body)
            throws Exception {
        if (tenant == null || !dfspIds.contains(tenant)) {
            throw new IllegalArgumentException("Requested tenant " + tenant + " not configured in the connector");
        }
        String operation = text(body.get("operation"));
        String processId = "swift_inbound_credit-" + tenant;
        String currency = text(body.getOrDefault("currency", "EUR")).toUpperCase(Locale.ROOT);
        String amount = text(body.getOrDefault("amount", "0"));
        String creditorIban = text(body.get("creditorIban"));
        String debtorIban = text(body.get("debtorIban"));
        Map<String, Object> channelRequest = new LinkedHashMap<>();
        channelRequest.put("payer", party(text(body.get("debtorName")), debtorIban, text(body.get("debtorBic"))));
        channelRequest.put("payee", party(text(body.get("creditorName")), creditorIban, tenant));
        channelRequest.put("amount", Map.of("amount", amount, "currency", currency));
        channelRequest.put("note", text(body.get("remittanceInformation")));

        Map<String, Object> variables = new HashMap<>();
        variables.put(ZeebeVariables.TENANT_ID, tenant);
        variables.put("payeeTenantId", tenant);
        variables.put(ZeebeVariables.PARTY_ID_TYPE, "IBAN");
        variables.put(ZeebeVariables.PARTY_ID, creditorIban);
        variables.put("currency", currency);
        variables.put("initiator", "PAYEE");
        variables.put("initiatorType", "CONSUMER");
        variables.put("scenario", "DEPOSIT");
        variables.put("amount", Map.of("amount", amount, "currency", currency));
        variables.put("operation", operation.isBlank() ? "CREDIT_TRANSFER" : operation);
        variables.put("clientReference", text(body.get("clientReference")));
        variables.put("endToEndId", text(body.get("clientReference")));
        variables.put("uetr", text(body.get("uetr")));
        variables.put("creditorAccount", creditorIban);
        variables.put("creditorBic", text(body.get("creditorBic")));
        variables.put("debtorAccount", debtorIban);
        variables.put("debtorBic", text(body.get("debtorBic")));
        String transactionId = starter.startZeebeWorkflow(processId, objectMapper.writeValueAsString(channelRequest), variables);
        return Map.of("transactionId", transactionId);
    }

    private static Map<String, Object> party(String name, String identifier, String fspId) {
        Map<String, Object> party = new LinkedHashMap<>();
        party.put("name", name.isBlank() ? "SWIFT party" : name);
        party.put("partyIdInfo", Map.of("partyIdType", "IBAN", "partyIdentifier", identifier.isBlank() ? "UNKNOWN" : identifier, "fspId",
                fspId.isBlank() ? "swift" : fspId));
        return party;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
