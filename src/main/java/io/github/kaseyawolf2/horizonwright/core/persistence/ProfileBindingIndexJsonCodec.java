package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Strict schema-v1 codec dedicated to the profile binding index. */
final class ProfileBindingIndexJsonCodec {

    private final Gson gson = new GsonBuilder().setPrettyPrinting()
        .create();

    byte[] encode(ProfileBindingIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index must not be null");
        }
        index.validate();
        return (gson.toJson(index) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    DecodeResult decode(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString();
        } catch (CharacterCodingException invalidUtf8) {
            return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "profile binding index is not valid UTF-8");
        }

        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) {
                return DecodeResult
                    .failure(PersistenceLoadStatus.CORRUPT, "profile binding index root must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            DecodeResult headerFailure = validateHeader(root);
            if (headerFailure != null) {
                return headerFailure;
            }
            ProfileBindingIndex index = gson.fromJson(root, ProfileBindingIndex.class);
            if (index == null) {
                return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "profile binding index decoded to null");
            }
            index.validate();
            return DecodeResult.loaded(index, "loaded schema v1 profile binding index");
        } catch (RuntimeException invalid) {
            return DecodeResult
                .failure(PersistenceLoadStatus.CORRUPT, "invalid profile binding index: " + describe(invalid));
        }
    }

    private static DecodeResult validateHeader(JsonObject root) {
        if (!root.has("schemaVersion") || !root.get("schemaVersion")
            .isJsonPrimitive()
            || !root.get("schemaVersion")
                .getAsJsonPrimitive()
                .isNumber()) {
            return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "schemaVersion must be a JSON number");
        }
        final int schemaVersion;
        try {
            schemaVersion = new BigDecimal(
                root.get("schemaVersion")
                    .getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException | UnsupportedOperationException invalidVersion) {
            return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "schemaVersion must be an integer");
        }
        if (schemaVersion > ProfileBindingIndex.SCHEMA_VERSION) {
            return DecodeResult.failure(
                PersistenceLoadStatus.NEWER_SCHEMA,
                "profile binding schema v" + schemaVersion
                    + " is newer than supported v"
                    + ProfileBindingIndex.SCHEMA_VERSION);
        }
        if (schemaVersion != ProfileBindingIndex.SCHEMA_VERSION) {
            return DecodeResult.failure(
                PersistenceLoadStatus.UNSUPPORTED_SCHEMA,
                "profile binding schema v" + schemaVersion + " requires explicit migration");
        }
        if (!root.has("documentKind") || !root.get("documentKind")
            .isJsonPrimitive()
            || !root.get("documentKind")
                .getAsJsonPrimitive()
                .isString()) {
            return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "documentKind must be a string");
        }
        String actualKind = root.get("documentKind")
            .getAsString();
        if (!ProfileBindingIndex.DOCUMENT_KIND.equals(actualKind)) {
            return DecodeResult.failure(
                PersistenceLoadStatus.WRONG_DOCUMENT_KIND,
                "expected profile binding index documentKind but found '" + actualKind + "'");
        }
        return null;
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.trim()
            .isEmpty() ? exception.getClass()
                .getSimpleName() : message;
    }

    static final class DecodeResult {

        private final PersistenceLoadStatus status;
        private final ProfileBindingIndex value;
        private final String diagnostic;

        private DecodeResult(PersistenceLoadStatus status, ProfileBindingIndex value, String diagnostic) {
            this.status = status;
            this.value = value;
            this.diagnostic = diagnostic;
        }

        static DecodeResult loaded(ProfileBindingIndex value, String diagnostic) {
            return new DecodeResult(PersistenceLoadStatus.LOADED, value, diagnostic);
        }

        static DecodeResult failure(PersistenceLoadStatus status, String diagnostic) {
            return new DecodeResult(status, null, diagnostic);
        }

        PersistenceLoadStatus getStatus() {
            return status;
        }

        ProfileBindingIndex getValue() {
            return value;
        }

        String getDiagnostic() {
            return diagnostic;
        }
    }
}
