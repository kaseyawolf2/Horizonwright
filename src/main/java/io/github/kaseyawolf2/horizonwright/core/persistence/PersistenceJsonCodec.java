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
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

final class PersistenceJsonCodec {

    private final Gson gson = new GsonBuilder().serializeNulls()
        .setPrettyPrinting()
        .create();

    byte[] encodeProfile(ProfileEnvelope profile) {
        profile.validate();
        return encode(profile);
    }

    byte[] encodeRuntime(RuntimeEnvelope runtime) {
        runtime.validate();
        return encode(runtime);
    }

    DecodeResult<ProfileEnvelope> decodeProfile(byte[] content) {
        return decode(
            content,
            PersistenceSchema.PROFILE_DOCUMENT_KIND,
            ProfileEnvelope.class,
            new Validator<ProfileEnvelope>() {

                @Override
                public void validate(ProfileEnvelope value) {
                    value.validate();
                }
            });
    }

    DecodeResult<RuntimeEnvelope> decodeRuntime(byte[] content) {
        return decode(
            content,
            PersistenceSchema.RUNTIME_DOCUMENT_KIND,
            RuntimeEnvelope.class,
            new Validator<RuntimeEnvelope>() {

                @Override
                public void validate(RuntimeEnvelope value) {
                    value.validate();
                }
            });
    }

    private byte[] encode(Object value) {
        return (gson.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private <T> DecodeResult<T> decode(byte[] content, String expectedKind, Class<T> type, Validator<T> validator) {
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
            return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "document is not valid UTF-8");
        }

        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) {
                return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "document root must be a JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            DecodeResult<T> headerFailure = validateHeader(root, expectedKind);
            if (headerFailure != null) {
                return headerFailure;
            }
            T value = gson.fromJson(root, type);
            if (value == null) {
                return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "document decoded to null");
            }
            validator.validate(value);
            return DecodeResult.loaded(
                value,
                "loaded schema v" + PersistenceSchema.CURRENT_VERSION + " " + expectedKind + " document");
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException invalid) {
            return DecodeResult
                .failure(PersistenceLoadStatus.CORRUPT, "invalid " + expectedKind + " document: " + describe(invalid));
        }
    }

    private static <T> DecodeResult<T> validateHeader(JsonObject root, String expectedKind) {
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
        if (schemaVersion > PersistenceSchema.CURRENT_VERSION) {
            return DecodeResult.failure(
                PersistenceLoadStatus.NEWER_SCHEMA,
                "schema v" + schemaVersion + " is newer than supported v" + PersistenceSchema.CURRENT_VERSION);
        }
        if (schemaVersion != PersistenceSchema.CURRENT_VERSION) {
            return DecodeResult.failure(
                PersistenceLoadStatus.UNSUPPORTED_SCHEMA,
                "schema v" + schemaVersion + " is unsupported; migration must be explicit");
        }

        if (!root.has("documentKind") || !root.get("documentKind")
            .isJsonPrimitive()
            || !root.get("documentKind")
                .getAsJsonPrimitive()
                .isString()) {
            return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "documentKind must be a string");
        }
        String actualKind;
        try {
            actualKind = root.get("documentKind")
                .getAsString();
        } catch (UnsupportedOperationException invalidKind) {
            return DecodeResult.failure(PersistenceLoadStatus.CORRUPT, "documentKind must be a string");
        }
        if (!expectedKind.equals(actualKind)) {
            return DecodeResult.failure(
                PersistenceLoadStatus.WRONG_DOCUMENT_KIND,
                "expected documentKind '" + expectedKind + "' but found '" + actualKind + "'");
        }
        return null;
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.trim()
            .isEmpty() ? exception.getClass()
                .getSimpleName() : message;
    }

    private interface Validator<T> {

        void validate(T value);
    }

    static final class DecodeResult<T> {

        private final PersistenceLoadStatus status;
        private final T value;
        private final String diagnostic;

        private DecodeResult(PersistenceLoadStatus status, T value, String diagnostic) {
            this.status = status;
            this.value = value;
            this.diagnostic = diagnostic;
        }

        static <T> DecodeResult<T> loaded(T value, String diagnostic) {
            return new DecodeResult<>(PersistenceLoadStatus.LOADED, value, diagnostic);
        }

        static <T> DecodeResult<T> failure(PersistenceLoadStatus status, String diagnostic) {
            return new DecodeResult<>(status, null, diagnostic);
        }

        PersistenceLoadStatus getStatus() {
            return status;
        }

        T getValue() {
            return value;
        }

        String getDiagnostic() {
            return diagnostic;
        }
    }
}
