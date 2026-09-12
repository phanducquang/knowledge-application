package com.knowledgeapplication.api.knowledge.model;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ShareTokenGenerator {

    static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public ShareTokenGenerator() {
        this(new SecureRandom());
    }

    ShareTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
