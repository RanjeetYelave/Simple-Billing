package com.billing.simple.billsoft.licensing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class MachineIdentityTest {

    @TempDir
    File tempDir;

    @Test
    void testFormatAndAlphabet() {
        String mid = MachineIdentity.generateRandomMachineId();
        assertNotNull(mid);
        assertEquals(19, mid.length());
        assertTrue(MachineIdentity.isValidFormat(mid), "Should be valid Crockford Base32 format");

        // Format: XXXX-XXXX-XXXX-XXXX
        String[] parts = mid.split("-");
        assertEquals(4, parts.length);
        for (String part : parts) {
            assertEquals(4, part.length());
            for (char c : part.toCharArray()) {
                assertTrue("0123456789ABCDEFGHJKMNPQRSTVWXYZ".indexOf(c) >= 0);
            }
        }
    }

    @Test
    void testPersistence() {
        File idFile = new File(tempDir, "test_mid.dat");
        MachineIdentity identity1 = new MachineIdentity(idFile);
        String id1 = identity1.getMachineId();
        assertNotNull(id1);
        assertTrue(idFile.exists());

        // Create second instance pointing to the same file
        MachineIdentity identity2 = new MachineIdentity(idFile);
        String id2 = identity2.getMachineId();
        assertEquals(id1, id2, "Machine ID must be persistent across instances");
    }

    @Test
    void testInvalidFormatDetection() {
        assertFalse(MachineIdentity.isValidFormat(null));
        assertFalse(MachineIdentity.isValidFormat(""));
        assertFalse(MachineIdentity.isValidFormat("ABCD-EFGH-IJKL-MNOP")); // Contains I and L
        assertFalse(MachineIdentity.isValidFormat("1234-5678-9012")); // Too short
    }
}
