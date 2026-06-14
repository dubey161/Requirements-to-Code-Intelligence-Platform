package com.engineeringproductivity.platform.github.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Receives GitHub webhook events.
 *
 * Setup in GitHub repo:
 *   Settings → Webhooks → Add webhook
 *   Payload URL : https://your-domain/api/v1/webhooks/github
 *   Content type: application/json
 *   Secret      : same as GITHUB_WEBHOOK_SECRET env var
 *   Events      : pull_request, pull_request_review, push
 */
@RestController
@RequestMapping("/api/v1/webhooks/github")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final WebhookProperties props;
    private final GitHubWebhookHandler handler;

    public GitHubWebhookController(WebhookProperties props, GitHubWebhookHandler handler) {
        this.props = props;
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-Hub-Signature-256", defaultValue = "") String signature,
            @RequestBody String payload
    ) {
        if (!verifySignature(payload, signature)) {
            log.warn("Webhook signature verification failed for event: {}", event);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Received GitHub webhook event: {}", event);
        handler.handle(event, payload);
        return ResponseEntity.ok().build();
    }

    private boolean verifySignature(String payload, String signature) {
        String secret = props.secret();
        if (secret == null || secret.isBlank()) return true; // skip verification in dev

        if (signature == null || !signature.startsWith("sha256=")) return false;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(expected, signature);
        } catch (Exception e) {
            log.error("HMAC verification error", e);
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
