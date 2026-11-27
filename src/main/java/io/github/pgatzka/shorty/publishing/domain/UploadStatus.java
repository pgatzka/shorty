package io.github.pgatzka.shorty.publishing.domain;

/** Lifecycle of an upload record. */
public enum UploadStatus {
    RENDERING,
    UPLOADING,
    UPLOADED,
    FAILED
}
