package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadStatus;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingKind;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Single-flight durable intent log for a two-document profile binding update. */
final class ProfileBindingTransactionStore {

    private static final int SCHEMA_VERSION = 1;
    private static final String DOCUMENT_KIND = "profile-binding-transaction";

    private final Path transactionFile;
    private final Path temporaryFile;

    ProfileBindingTransactionStore(Path stateRoot) {
        if (stateRoot == null) {
            throw new IllegalArgumentException("stateRoot must not be null");
        }
        Path normalizedRoot = stateRoot.toAbsolutePath()
            .normalize();
        transactionFile = normalizedRoot.resolve("profile-binding-transaction.json")
            .normalize();
        temporaryFile = transactionFile.resolveSibling(transactionFile.getFileName() + ".tmp");
        if (!transactionFile.startsWith(normalizedRoot) || !temporaryFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("transaction path escapes stateRoot");
        }
    }

    LoadResult load() {
        if (Files.exists(temporaryFile)) {
            return LoadResult.failure(
                PersistenceLoadStatus.IO_ERROR,
                "A stale profile binding transaction temporary file requires explicit inspection: " + temporaryFile);
        }
        if (!Files.exists(transactionFile)) {
            return LoadResult.missing();
        }
        try {
            return decode(Files.readAllBytes(transactionFile));
        } catch (IOException failure) {
            return LoadResult.failure(
                PersistenceLoadStatus.IO_ERROR,
                "Could not read profile binding transaction: " + describe(failure));
        }
    }

    void begin(ProfileBindingTransaction transaction) throws IOException {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction must not be null");
        }
        if (Files.exists(transactionFile) || Files.exists(temporaryFile)) {
            throw new IOException("an unfinished profile binding transaction already requires recovery");
        }
        Files.createDirectories(transactionFile.getParent());
        byte[] content = encode(transaction);
        try (FileChannel channel = FileChannel
            .open(temporaryFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        try {
            Files.move(temporaryFile, transactionFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IOException("atomic transaction intent creation is not supported", failure);
        }
    }

    void complete() throws IOException {
        Files.deleteIfExists(transactionFile);
    }

    private static byte[] encode(ProfileBindingTransaction transaction) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("documentKind", DOCUMENT_KIND);
        root.addProperty(
            "operation",
            transaction.getOperation()
                .name());
        root.addProperty("baseIndexRevision", transaction.getBaseIndexRevision());
        root.addProperty(
            "targetKind",
            transaction.getTargetKind()
                .name());
        root.addProperty("targetLocatorHash", transaction.getTargetLocatorHash());
        root.addProperty("targetWorldMarkerHash", transaction.getTargetWorldMarkerHash());
        JsonObject identity = new JsonObject();
        identity.addProperty(
            "profileId",
            transaction.getTargetIdentity()
                .getProfileId());
        identity.addProperty(
            "displayName",
            transaction.getTargetIdentity()
                .getDisplayName());
        identity.addProperty(
            "serverAddress",
            transaction.getTargetIdentity()
                .getServerAddress());
        identity.addProperty(
            "worldFingerprint",
            transaction.getTargetIdentity()
                .getWorldFingerprint());
        identity.addProperty(
            "createdAtEpochMillis",
            transaction.getTargetIdentity()
                .getCreatedAtEpochMillis());
        root.add("targetIdentity", identity);
        if (transaction.getPreviousIdentity() != null) {
            JsonObject previousIdentity = new JsonObject();
            previousIdentity.addProperty(
                "profileId",
                transaction.getPreviousIdentity()
                    .getProfileId());
            previousIdentity.addProperty(
                "displayName",
                transaction.getPreviousIdentity()
                    .getDisplayName());
            previousIdentity.addProperty(
                "serverAddress",
                transaction.getPreviousIdentity()
                    .getServerAddress());
            previousIdentity.addProperty(
                "worldFingerprint",
                transaction.getPreviousIdentity()
                    .getWorldFingerprint());
            previousIdentity.addProperty(
                "createdAtEpochMillis",
                transaction.getPreviousIdentity()
                    .getCreatedAtEpochMillis());
            root.add("previousIdentity", previousIdentity);
        }
        root.addProperty("confirmationId", transaction.getConfirmationId());
        root.addProperty("confirmedAtEpochMillis", transaction.getConfirmedAtEpochMillis());
        return (root.toString() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static LoadResult decode(byte[] content) {
        try {
            JsonElement parsed = new JsonParser().parse(new String(content, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return LoadResult.failure(PersistenceLoadStatus.CORRUPT, "transaction root must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            int schema = required(root, "schemaVersion").getAsInt();
            if (schema > SCHEMA_VERSION) {
                return LoadResult.failure(
                    PersistenceLoadStatus.NEWER_SCHEMA,
                    "transaction schema " + schema + " is newer than supported schema " + SCHEMA_VERSION);
            }
            if (schema != SCHEMA_VERSION) {
                return LoadResult.failure(
                    PersistenceLoadStatus.UNSUPPORTED_SCHEMA,
                    "transaction schema " + schema + " is unsupported");
            }
            if (!DOCUMENT_KIND.equals(required(root, "documentKind").getAsString())) {
                return LoadResult.failure(
                    PersistenceLoadStatus.WRONG_DOCUMENT_KIND,
                    "transaction documentKind is not '" + DOCUMENT_KIND + "'");
            }
            JsonObject identityJson = required(root, "targetIdentity").getAsJsonObject();
            WorldProfileIdentity identity = new WorldProfileIdentity(
                required(identityJson, "profileId").getAsString(),
                required(identityJson, "displayName").getAsString(),
                required(identityJson, "serverAddress").getAsString(),
                required(identityJson, "worldFingerprint").getAsString(),
                required(identityJson, "createdAtEpochMillis").getAsLong());
            WorldProfileIdentity previousIdentity = null;
            if (root.has("previousIdentity") && !root.get("previousIdentity")
                .isJsonNull()) {
                JsonObject previousJson = root.getAsJsonObject("previousIdentity");
                previousIdentity = new WorldProfileIdentity(
                    required(previousJson, "profileId").getAsString(),
                    required(previousJson, "displayName").getAsString(),
                    required(previousJson, "serverAddress").getAsString(),
                    required(previousJson, "worldFingerprint").getAsString(),
                    required(previousJson, "createdAtEpochMillis").getAsLong());
            }
            ProfileBindingTransaction transaction = ProfileBindingTransaction.restore(
                ProfileBindingTransaction.Operation.valueOf(required(root, "operation").getAsString()),
                required(root, "baseIndexRevision").getAsLong(),
                ProfileBindingKind.valueOf(required(root, "targetKind").getAsString()),
                required(root, "targetLocatorHash").getAsString(),
                required(root, "targetWorldMarkerHash").getAsString(),
                previousIdentity,
                identity,
                required(root, "confirmationId").getAsString(),
                required(root, "confirmedAtEpochMillis").getAsLong());
            return LoadResult.loaded(transaction);
        } catch (RuntimeException failure) {
            return LoadResult.failure(PersistenceLoadStatus.CORRUPT, "transaction is invalid: " + describe(failure));
        }
    }

    private static JsonElement required(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("missing transaction field '" + field + "'");
        }
        return value;
    }

    private static String describe(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.trim()
            .isEmpty() ? failure.getClass()
                .getSimpleName() : message;
    }

    static final class LoadResult {

        private final PersistenceLoadStatus status;
        private final ProfileBindingTransaction transaction;
        private final String diagnostic;

        private LoadResult(PersistenceLoadStatus status, ProfileBindingTransaction transaction, String diagnostic) {
            this.status = status;
            this.transaction = transaction;
            this.diagnostic = diagnostic;
        }

        static LoadResult loaded(ProfileBindingTransaction transaction) {
            return new LoadResult(PersistenceLoadStatus.LOADED, transaction, "transaction loaded");
        }

        static LoadResult missing() {
            return new LoadResult(PersistenceLoadStatus.MISSING, null, "no transaction is pending");
        }

        static LoadResult failure(PersistenceLoadStatus status, String diagnostic) {
            return new LoadResult(status, null, diagnostic);
        }

        PersistenceLoadStatus getStatus() {
            return status;
        }

        ProfileBindingTransaction getTransaction() {
            if (transaction == null) {
                throw new IllegalStateException("transaction is unavailable: " + diagnostic);
            }
            return transaction;
        }

        String getDiagnostic() {
            return diagnostic;
        }
    }
}
