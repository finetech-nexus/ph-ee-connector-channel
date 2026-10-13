package org.mifos.connector.channel.api.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Starts {@code sibtel_domestic_inbound_transfer} for a virement received in a SIBTEL lot.
 */
@RestController
@RequestMapping("/channel/sibtel")
public class SibtelInboundController {

    private final ZeebeProcessStarter starter;
    private final ObjectMapper objectMapper;
    private final List<String> dfspIds;

    public SibtelInboundController(ZeebeProcessStarter starter, ObjectMapper objectMapper,
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
        String beneficiaryRib = text(body.get("beneficiaryRib"));
        String beneficiaryIban = iban(beneficiaryRib);
        String debtorRib = text(body.get("debtorRib"));
        Map<String, Object> channelRequest = new LinkedHashMap<>();
        channelRequest.put("payer", party(text(body.get("debtorName")), debtorRib));
        channelRequest.put("payee", party(text(body.get("beneficiaryName")), beneficiaryIban));
        channelRequest.put("amount", Map.of("amount", text(body.get("amount")), "currency", text(body.getOrDefault("currency", "TND"))));
        channelRequest.put("note", text(body.get("motif")));

        Map<String, Object> variables = new HashMap<>();
        variables.put(ZeebeVariables.TENANT_ID, tenant);
        variables.put("payeeTenantId", tenant);
        variables.put(ZeebeVariables.PARTY_ID_TYPE, "IBAN");
        variables.put(ZeebeVariables.PARTY_ID, beneficiaryIban);
        variables.put("currency", "TND");
        variables.put("initiator", "PAYEE");
        variables.put("initiatorType", "CONSUMER");
        variables.put("scenario", "DEPOSIT");
        variables.put("amount", Map.of("amount", text(body.get("amount")), "currency", "TND"));
        variables.put("sibtelRecordId", body.get("recordId"));
        variables.put("creditorAccount", beneficiaryRib);
        variables.put("debtorAccount", debtorRib);
        String processId = "sibtel_domestic_inbound_transfer-" + tenant;
        String transactionId = starter.startZeebeWorkflow(processId, objectMapper.writeValueAsString(channelRequest), variables);
        return Map.of("transactionId", transactionId);
    }

    private static Map<String, Object> party(String name, String identifier) {
        Map<String, Object> party = new LinkedHashMap<>();
        party.put("name", name);
        party.put("partyIdInfo", Map.of("partyIdType", "IBAN", "partyIdentifier", identifier, "fspId", "nexusbank"));
        return party;
    }

    /** Tunisian IBAN is {@code TN} + two check digits + the 20-digit RIB. */
    static String iban(String rib) {
        String compact = rib.replaceAll("\\s", "");
        String rearranged = compact + "TN00";
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isLetter(c) ? (c - 'A' + 10) : String.valueOf(c));
        }
        int check = 98 - new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue();
        return "TN" + String.format("%02d", check) + compact;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
