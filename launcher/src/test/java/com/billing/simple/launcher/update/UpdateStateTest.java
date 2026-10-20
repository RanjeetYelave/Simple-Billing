package com.billing.simple.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UpdateStateTest {

    @Test
    void testSerializationRoundTrip() {
        UpdateState state = new UpdateState(
                "tx-12345",
                UpdateState.STATUS_VERIFIED_STAGED,
                "1.0.0",
                "1.0.12",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                50_000_000L,
                "/path/to/staged.war",
                "/path/to/backup.war",
                "/path/to/quarantine.zip",
                "No error",
                1700000000000L
        );

        String json = state.toJson();
        assertNotNull(json);

        UpdateState loaded = UpdateState.fromJson(json);
        assertEquals("tx-12345", loaded.getTransactionId());
        assertEquals(UpdateState.STATUS_VERIFIED_STAGED, loaded.getStatus());
        assertEquals("1.0.0", loaded.getCurrentVersion());
        assertEquals("1.0.12", loaded.getTargetVersion());
        assertEquals(50_000_000L, loaded.getExpectedSizeBytes());
        assertEquals("/path/to/staged.war", loaded.getStagedArtifactPath());
        assertEquals("/path/to/backup.war", loaded.getBackupArtifactPath());
        assertEquals("/path/to/quarantine.zip", loaded.getQuarantineBackupPath());
        assertEquals("No error", loaded.getErrorMessage());
    }

    @Test
    void testAtomicSaveAndLoad(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("update-state.json");

        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_DOWNLOADING);
        state.setTargetVersion("2.0.0");
        state.saveAtomic(stateFile);

        assertTrue(Files.exists(stateFile));

        UpdateState loaded = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_DOWNLOADING, loaded.getStatus());
        assertEquals("2.0.0", loaded.getTargetVersion());
    }

    @Test
    void testCorruptedFileLoadsAsIdle(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("corrupted-state.json");
        Files.writeString(stateFile, "{ invalid json content truncated");

        UpdateState loaded = UpdateState.load(stateFile);
        assertNotNull(loaded);
        assertEquals(UpdateState.STATUS_IDLE, loaded.getStatus());
    }

    @Test
    void testNonExistentFileReturnsIdle(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("non-existent.json");
        UpdateState loaded = UpdateState.load(missing);
        assertNotNull(loaded);
        assertEquals(UpdateState.STATUS_IDLE, loaded.getStatus());
    }
}
