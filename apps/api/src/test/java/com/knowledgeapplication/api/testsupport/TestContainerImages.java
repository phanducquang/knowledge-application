package com.knowledgeapplication.api.testsupport;

import org.testcontainers.utility.DockerImageName;

public final class TestContainerImages {

    public static final DockerImageName POSTGRES = DockerImageName
            .parse("pgvector/pgvector:0.8.6-pg17-bookworm")
            .asCompatibleSubstituteFor("postgres");

    public static final DockerImageName MINIO = DockerImageName.parse(
            "ghcr.io/coollabsio/minio:RELEASE.2025-04-22T22-12-26Z"
    );

    private TestContainerImages() {
    }
}
