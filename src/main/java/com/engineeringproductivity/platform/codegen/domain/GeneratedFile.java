package com.engineeringproductivity.platform.codegen.domain;

/**
 * A single generated source file — filename + Java content.
 */
public record GeneratedFile(
        String fileName,
        String packagePath,
        String content,
        FileType fileType
) {
    public enum FileType {
        ENTITY, REPOSITORY, CREATE_REQUEST, UPDATE_REQUEST, RESPONSE, SERVICE, CONTROLLER,
        ALGORITHM, BATCH_JOB, CLI, LIBRARY, EVENT_CONSUMER, EVENT_PRODUCER
    }

    public String fullPath() {
        return packagePath.replace('.', '/') + "/" + fileName;
    }
}
