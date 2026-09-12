package com.knowledgeapplication.api.configuration;

import com.knowledgeapplication.api.auth.OwnerIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentOwner {

    private final UUID ownerId;
    private final OwnerIdentity ownerIdentity;

    public CurrentOwner(
            @Value("${app.owner-id}") UUID ownerId,
            OwnerIdentity ownerIdentity
    ) {
        this.ownerId = ownerId;
        this.ownerIdentity = ownerIdentity;
    }

    public UUID id() {
        ownerIdentity.requireAuthorized(SecurityContextHolder.getContext().getAuthentication());
        return ownerId;
    }
}
