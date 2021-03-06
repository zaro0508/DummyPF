package org.sagebionetworks.bridge.services;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Implementation of {@link UserDataDownloadService} that connects to the Bridge User Data Download Service via SQS.
 */
@Component
public class UserDataDownloadViaSqsService implements UserDataDownloadService {
    private static final Logger logger = LoggerFactory.getLogger(UserDataDownloadViaSqsService.class);

    // constants - these are package scoped so unit tests can access them
    static final String CONFIG_KEY_UDD_SQS_QUEUE_URL = "udd.sqs.queue.url";
    static final String REQUEST_KEY_END_DATE = "endDate";
    static final String REQUEST_KEY_START_DATE = "startDate";
    static final String REQUEST_KEY_STUDY_ID = "studyId";
    static final String REQUEST_KEY_USERNAME = "username";

    private BridgeConfig bridgeConfig;
    private AmazonSQSClient sqsClient;

    /** Bridge config, used to get the SQS queue URL. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    /** SQS client. */
    @Autowired
    public final void setSqsClient(AmazonSQSClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    /** {@inheritDoc} */
    @Override
    public void requestUserData(@Nonnull StudyIdentifier studyIdentifier, @Nonnull User user,
            @Nonnull DateRange dateRange) throws JsonProcessingException {
        String studyId = studyIdentifier.getIdentifier();
        String email = user.getEmail();
        String startDateStr = dateRange.getStartDate().toString();
        String endDateStr = dateRange.getEndDate().toString();

        // construct message as string-string map
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put(REQUEST_KEY_STUDY_ID, studyId);
        requestMap.put(REQUEST_KEY_USERNAME, email);
        requestMap.put(REQUEST_KEY_START_DATE, startDateStr);
        requestMap.put(REQUEST_KEY_END_DATE, endDateStr);

        // serialize to JSON so we can write it to the SQS message
        String requestJsonText = BridgeObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(
                requestMap);

        // send to SQS
        String queueUrl = bridgeConfig.getProperty(CONFIG_KEY_UDD_SQS_QUEUE_URL);
        SendMessageResult sqsResult = sqsClient.sendMessage(queueUrl, requestJsonText);
        logger.info("Sent request to SQS for hash[username]=" + email.hashCode() + ", study=" + studyId +
                ", startDate=" + startDateStr + ", endDate=" + endDateStr + "; received message ID=" +
                sqsResult.getMessageId());
    }
}
