package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

/**
 * Dynamo DB implementation of UploadFieldDefinition. While there is nothing specific to Dynamo DB in this class, this
 * class exists to distinguish itself from potential other implementations.
 */
@JsonDeserialize(builder = DynamoUploadFieldDefinition.Builder.class)
public final class DynamoUploadFieldDefinition implements UploadFieldDefinition {
    private final @Nullable String fileExtension;
    private final @Nullable String mimeType;
    private final @Nullable Integer minAppVersion;
    private final @Nullable Integer maxAppVersion;
    private final @Nullable Integer maxLength;
    private final @Nonnull String name;
    private final boolean required;
    private final @Nonnull UploadFieldType type;

    /** Private constructor. Construction of a DynamoUploadFieldDefinition should go through the Builder. */
    private DynamoUploadFieldDefinition(@Nullable String fileExtension, @Nullable String mimeType,
            @Nullable Integer minAppVersion, @Nullable Integer maxAppVersion, @Nullable Integer maxLength,
            @Nonnull String name, boolean required, @Nonnull UploadFieldType type) {
        this.fileExtension = fileExtension;
        this.mimeType = mimeType;
        this.minAppVersion = minAppVersion;
        this.maxAppVersion = maxAppVersion;
        this.maxLength = maxLength;
        this.name = name;
        this.required = required;
        this.type = type;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getFileExtension() {
        return fileExtension;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getMimeType() {
        return mimeType;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Integer getMinAppVersion() {
        return minAppVersion;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Integer getMaxAppVersion() {
        return maxAppVersion;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Integer getMaxLength() {
        return maxLength;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRequired() {
        return required;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadFieldType getType() {
        return type;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DynamoUploadFieldDefinition that = (DynamoUploadFieldDefinition) o;
        return required == that.required &&
                Objects.equals(fileExtension, that.fileExtension) &&
                Objects.equals(mimeType, that.mimeType) &&
                Objects.equals(minAppVersion, that.minAppVersion) &&
                Objects.equals(maxAppVersion, that.maxAppVersion) &&
                Objects.equals(maxLength, that.maxLength) &&
                Objects.equals(name, that.name) &&
                type == that.type;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(fileExtension, mimeType, minAppVersion, maxAppVersion, maxLength, name, required, type);
    }

    /** Builder for DynamoUploadFieldDefinition */
    public static class Builder {
        private String fileExtension;
        private String mimeType;
        private Integer minAppVersion;
        private Integer maxAppVersion;
        private Integer maxLength;
        private String name;
        private Boolean required;
        private UploadFieldType type;

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getFileExtension */
        public Builder withFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getMimeType */
        public Builder withMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getMinAppVersion */
        public Builder withMinAppVersion(Integer minAppVersion) {
            this.minAppVersion = minAppVersion;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getMaxAppVersion */
        public Builder withMaxAppVersion(Integer maxAppVersion) {
            this.maxAppVersion = maxAppVersion;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getMaxLength */
        public Builder withMaxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getName */
        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#isRequired */
        public Builder withRequired(Boolean required) {
            this.required = required;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadFieldDefinition#getType */
        public Builder withType(UploadFieldType type) {
            this.type = type;
            return this;
        }

        /**
         * Builds and validates a DynamoUploadFieldDefinition. name must be non-null and non-empty. type must be
         * non-null. required may be null and defaults to true. If this is called with invalid fields, it will throw an
         * InvalidEntityException.
         *
         * @return validated DynamoUploadFieldDefinition
         * @throws InvalidEntityException
         *         if called with invalid fields
         */
        public DynamoUploadFieldDefinition build() throws InvalidEntityException {
            if (required == null) {
                required = true;
            }
            return new DynamoUploadFieldDefinition(fileExtension, mimeType, minAppVersion, maxAppVersion, maxLength,
                    name, required, type);
        }
    }
}
