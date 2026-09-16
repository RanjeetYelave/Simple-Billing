package com.billing.simple.billsoft.dataprotection;

/**
 * Storage abstraction for off-device backup uploads and downloads.
 */
public interface BackupStorageProvider {

    class UploadResult {
        private final boolean success;
        private final String sha;
        private final String errorMessage;
        private final int statusCode;
        private final DataProtectionErrorCode errorCode;

        public UploadResult(boolean success, String sha, String errorMessage, int statusCode, DataProtectionErrorCode errorCode) {
            this.success = success;
            this.sha = sha;
            this.errorMessage = errorMessage;
            this.statusCode = statusCode;
            this.errorCode = errorCode;
        }

        public static UploadResult ok(String sha) {
            return new UploadResult(true, sha, null, 200, null);
        }

        public static UploadResult error(String errorMessage, int statusCode, DataProtectionErrorCode errorCode) {
            return new UploadResult(false, null, errorMessage, statusCode, errorCode);
        }

        public static UploadResult error(String errorMessage, int statusCode) {
            return new UploadResult(false, null, errorMessage, statusCode, DataProtectionErrorCode.fromHttpStatus(statusCode));
        }

        public boolean isSuccess() {
            return success;
        }

        public String getSha() {
            return sha;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public DataProtectionErrorCode getErrorCode() {
            return errorCode;
        }
    }

    /**
     * Uploads the encrypted backup container for the specified machine.
     * @param machineId Machine identifier
     * @param encryptedData RCBP container payload bytes
     * @param lastKnownSha SHA of previous blob (null on initial creation)
     * @return Result containing new blob SHA or error description
     */
    UploadResult uploadBackup(String machineId, byte[] encryptedData, String lastKnownSha);

    /**
     * Downloads the latest encrypted backup container for the specified machine.
     * @param machineId Machine identifier
     * @return Decryption container bytes or null if not found
     */
    byte[] downloadBackup(String machineId);
}
