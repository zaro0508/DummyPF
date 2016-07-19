package org.sagebionetworks.bridge.models.upload;

import java.util.List;

import org.joda.time.LocalDate;

/** Metadata for Bridge uploads. */
public interface Upload {
    /**
     * If the upload is in a state where calling uploadComplete() and kicking off validation is a valid thing to do,
     * then this method will return true. Otherwise, this method will return false.
     */
    boolean canBeValidated();

    /** Name of the file to upload. */
    String getFilename();

    /** Health code of the user from which this upload originates from. */
    String getHealthCode();

    /**
     * S3 object ID (key name). This is generated by Bridge to ensure no filename collisions and to ensure that we
     * don't get S3 hotspots from poorly distributed names.
     */
    String getObjectId();

    /**
     * Record ID of the corresponding health data record. This is generally null until upload validation is complete
     * and creates the corresponding record.
     */
    String getRecordId();

    /** Represents upload status, such as requested, validation in progress, validation failed, or succeeded. */
    UploadStatus getStatus();

    /**
     * <p>
     * Calendar date the file was uploaded (specifically, the uploadComplete() call.
     * </p>
     * Date is determined using Pacific local time. Pacific local time was chosen because currently, all studies are
     * done in the US, so if we partitioned based on date using UTC, we'd get a cut-off in the middle of the afternoon,
     * likely in the middle of peak uploads. In the future, if we have studies outside of the US, the upload date
     * timezone will be configurable per study.
     * <p>
     */
    LocalDate getUploadDate();
    
    /**
     * <p>The UTC timestamp of the time when the server creates the initial REQUESTED upload record.</p>
     */
    long getRequestedOn();
    
    /**
     * <p>The UTC timestamp of the time when the upload record is updated based on a completed call by any external 
     * client (either the S3 event listener or the mobile client). </p>
     */
    long getCompletedOn();
    
    /**
     * <p>A string indicating the client that completed the upload. The two current clients are "s3 listener" and 
     * "mobile client". </p>
     */
    UploadClient getCompletedBy();
    
    /**
     * <p>The study identifier for this upload.</p>
     */
    String getStudyId();

    /** Upload ID. This is the key in the Dynamo DB table that uniquely identifies this upload. */
    String getUploadId();

    /**
     * List of validation messages, generally contains error messages. Since a single upload file may fail validation
     * in multiple ways, Bridge server will attempt to return all messages to the user. For example, the upload file
     * might be unencrypted, uncompressed, and it might not fit any of the expected schemas for the study.
     */
    List<String> getValidationMessageList();
}
