package com.nuno.kafkalab.paymentservice.gateway.fake;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

// Assinatura dos webhooks: HMAC-SHA256 do body com um segredo partilhado entre o fornecedor e o serviço.
// É o mesmo princípio do header Stripe-Signature: sem o segredo ninguém consegue forjar um "pagamento feito"
final class FakeWebhookSignature {

    static final String HEADER = "Fake-Signature";

    private FakeWebhookSignature() {
    }

    static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    // MessageDigest.isEqual compara em tempo constante: não deixa adivinhar a assinatura pelo tempo de resposta
    static boolean isValid(String payload, String signature, String secret) {
        if (signature == null) {
            return false;
        }
        byte[] expected = sign(payload, secret).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }
}
