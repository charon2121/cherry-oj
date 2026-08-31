package com.cherryoj.problemservice.storage;

import com.cherryoj.problemservice.api.TestDataDtos.Manifest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public interface TestDataAssetStore {

    StagedAsset stage(String assetId, InputStream source) throws AssetException;

    void seal(StagedAsset staged) throws AssetException;

    void discard(StagedAsset staged);

    void delete(String storageRef);

    Asset open(String storageRef) throws AssetException;

    RecoveryResult recover(Set<String> readyStorageRefs);

    record StagedAsset(
            String id,
            String temporaryRef,
            String storageRef,
            String contentSha256,
            long archiveBytes,
            int caseCount,
            long totalBytes,
            Manifest manifest) {
    }

    record Asset(InputStream stream, long size) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    record RecoveryResult(int temporaryFilesDeleted, int orphanAssetsDeleted) {
    }

    final class AssetException extends Exception {
        private final Kind kind;

        public AssetException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public AssetException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }

    enum Kind { PAYLOAD_TOO_LARGE, INVALID_ARCHIVE, STORAGE_UNAVAILABLE, NOT_FOUND }
}
