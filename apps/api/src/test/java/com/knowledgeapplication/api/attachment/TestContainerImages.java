package com.knowledgeapplication.api.attachment;

import org.testcontainers.utility.DockerImageName;

final class TestContainerImages {

    static final DockerImageName MINIO = DockerImageName.parse(
            "ghcr.io/coollabsio/minio:RELEASE.2025-04-22T22-12-26Z"
    );

    private TestContainerImages() {
    }
}
