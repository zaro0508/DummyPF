package org.sagebionetworks.bridge.models.upload;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An API view of uploads that combines information from our internal upload health data record tables.
 * We serialize this in the API but do not read it through the API.  
 */
public class UploadViewTest {
    
    private static final DateTime REQUESTED_ON = DateTime.parse("2016-07-25T16:25:32.211Z");
    private static final DateTime COMPLETED_ON = DateTime.parse("2016-07-25T16:25:32.277Z");
    
    @Test
    public void canSerialize() throws Exception {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setContentLength(1000L);
        upload.setStatus(UploadStatus.SUCCEEDED);
        upload.setRequestedOn(REQUESTED_ON.getMillis());
        upload.setCompletedOn(COMPLETED_ON.getMillis());
        upload.setCompletedBy(UploadCompletionClient.APP);
        
        UploadView view = new UploadView.Builder().withUpload(upload)
                .withSchemaId("schema-name").withSchemaRevision(10).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(view);
        assertEquals(1000, node.get("contentLength").asInt());
        assertEquals("succeeded", node.get("status").asText());
        assertEquals("2016-07-25T16:25:32.211Z", node.get("requestedOn").asText());
        assertEquals("2016-07-25T16:25:32.277Z", node.get("completedOn").asText());
        assertEquals("app", node.get("completedBy").asText());
        assertEquals("schema-name", node.get("schemaId").asText());
        assertEquals(10, node.get("schemaRevision").asInt());
        assertEquals("Upload", node.get("type").asText());
    }

}
