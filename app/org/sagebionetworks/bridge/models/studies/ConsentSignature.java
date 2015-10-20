package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.validators.ConsentSignatureValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonFilter("filter")
public final class ConsentSignature implements BridgeEntity {

    public static final ObjectWriter SIGNATURE_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter", 
            SimpleBeanPropertyFilter.serializeAllExcept("signedOn")));
    
    private static final String NAME_FIELD = "name";
    private static final String BIRTHDATE_FIELD = "birthdate";
    private static final String IMAGE_DATA_FIELD = "imageData";
    private static final String IMAGE_MIME_TYPE = "imageMimeType";
    private static final ConsentSignatureValidator VALIDATOR = new ConsentSignatureValidator();

    private final @Nonnull String name;
    private final @Nonnull String birthdate;
    private final @Nullable String imageData;
    private final @Nullable String imageMimeType;
    private final @Nonnull long signedOn;

    /** Private constructor. Instances should be constructed using factory methods create() or createFromJson(). */
    @JsonCreator
    private ConsentSignature(@JsonProperty("name") String name, @JsonProperty("birthdate") String birthdate,
            @JsonProperty("imageData") String imageData, @JsonProperty("imageMimeType") String imageMimeType, 
            @JsonProperty("signedOn") long signedOn) {

        this.name = name;
        this.birthdate = birthdate;
        this.imageData = imageData;
        this.imageMimeType = imageMimeType;
        this.signedOn = signedOn;
    }

    /**
     * <p>
     * Factory method to create and validate ConsentSignature.
     * </p>
     * <p>
     * imageData and imageMimeType are optional. However, if one of them is specified, both of them must be specified.
     * If they are specified, they must be non-empty.
     * </p>
     *
     * @param name
     *         name of the user giving consent, must be non-null and non-empty
     * @param birthdate
     *         user's birthday in the format "YYYY-MM-DD", must be non-null and non-empty
     * @param imageData
     *         image data as a Base64 encoded string, optional
     * @param imageMimeType
     *         image MIME type (ex: image/png), optional
     * @return validated ConsentSignature
     * @throws InvalidEntityException
     *         if called with invalid fields
     */
    public static ConsentSignature create(@Nonnull String name, @Nonnull String birthdate, @Nullable String imageData,
            @Nullable String imageMimeType, @Nonnull long signedOn) throws InvalidEntityException {
        ConsentSignature sig = new ConsentSignature(name, birthdate, imageData, imageMimeType, signedOn);
        Validate.entityThrowingException(VALIDATOR, sig);
        return sig;
    }
    
    /**
     * A copy constructor that will return a new consent signature with an updated signedOn value. 
     * Used to migrate older versions of the signature object to an updated value that includes the 
     * signing date. 
     * @param signature
     *      the base signature from which to derive this signature
     * @param signedOn
     *      the date and time stamp of the signing
     * @return validated ConsentSignature
     * @throws InvalidEntityException
     *         if called with invalid fields
     */
    public static ConsentSignature create(ConsentSignature signature, long signedOn) {
        return create(signature.name, signature.birthdate, signature.imageData, signature.imageMimeType, signedOn);
    }

    /**
     * Factory method to create and validate ConsentSignature from JSON. See {@link #create} for validation details.
     *
     * @param node
     *         JSON node to parse
     * @param signedOn
     *         the date and time recorded for this signature (Unix timestamp)
     * @return validated ConsentSignature
     * @throws InvalidEntityException
     *         if the JSON contains invalid fields
     */
    public static ConsentSignature createFromJson(JsonNode node, long signedOn) throws InvalidEntityException {
        String name = JsonUtils.asText(node, NAME_FIELD);
        String birthdate = JsonUtils.asText(node, BIRTHDATE_FIELD);
        String imageData = JsonUtils.asText(node, IMAGE_DATA_FIELD);
        String imageMimeType = JsonUtils.asText(node, IMAGE_MIME_TYPE);
        return create(name, birthdate, imageData, imageMimeType, signedOn);
    }

    /** Name of the user giving consent. */
    public @Nonnull String getName() {
        return name;
    }

    /** User's birthday in the format "YYYY-MM-DD". */
    public @Nonnull String getBirthdate() {
        return birthdate;
    }

    /** Image data as a Base64 encoded string. */
    public @Nullable String getImageData() {
        return imageData;
    }

    /** Image MIME type (ex: image/png). */
    public @Nullable String getImageMimeType() {
        return imageMimeType;
    }
    
    /** The date and time recorded for this signature. */
    public long getSignedOn() {
        return signedOn;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(birthdate);
        result = prime * result + Objects.hashCode(imageData);
        result = prime * result + Objects.hashCode(imageMimeType);
        result = prime * result + Objects.hashCode(name);
        result = prime * result + Objects.hashCode(signedOn);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ConsentSignature other = (ConsentSignature) obj;
        return Objects.equals(birthdate, other.birthdate) && Objects.equals(imageData, other.imageData)
                && Objects.equals(imageMimeType, other.imageMimeType) && Objects.equals(name, other.name) 
                && Objects.equals(signedOn, other.signedOn);
    }

    @Override
    public String toString() {
        return String.format("ConsentSignature [name=%s, birthdate=%s, imageData=%s, imageMimeType=%s, signedOn=%s]", 
                name, birthdate, imageData, imageMimeType, signedOn);
    }
}
