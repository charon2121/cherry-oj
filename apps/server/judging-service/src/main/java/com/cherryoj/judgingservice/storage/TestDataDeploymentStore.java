package com.cherryoj.judgingservice.storage;

import com.cherryoj.judgingservice.api.JudgingDtos.Manifest;
import java.io.InputStream;
import java.util.Set;

public interface TestDataDeploymentStore {
    Installed deploy(String testDataVersionId, String expectedSha256, Manifest manifest, InputStream archive)
            throws DeploymentException;
    void rollback(Installed installed);
    RecoveryResult recover(Set<String> readyTestDataVersionIds);

    record Installed(String testDataVersionId, String sha256) {}
    record RecoveryResult(int stagingDeleted, int orphanDirectoriesDeleted) {}

    final class DeploymentException extends Exception {
        private final Kind kind;
        public DeploymentException(Kind kind, String code) { super(code); this.kind = kind; }
        public DeploymentException(Kind kind, String code, Throwable cause) { super(code, cause); this.kind = kind; }
        public Kind kind() { return kind; }
    }

    enum Kind { INVALID, TOO_LARGE, CONFLICT, STORAGE }
}
