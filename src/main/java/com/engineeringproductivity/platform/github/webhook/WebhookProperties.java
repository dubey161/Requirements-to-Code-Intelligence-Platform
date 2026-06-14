package com.engineeringproductivity.platform.github.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * GitHub Webhook config.
 * Set the secret in your GitHub repo → Settings → Webhooks → Secret.
 * Events to subscribe: pull_request, pull_request_review, push
 */
@ConfigurationProperties(prefix = "github.webhook")
public record WebhookProperties(
        @DefaultValue("") String secret,
        @DefaultValue("true") boolean enabled
) {}
