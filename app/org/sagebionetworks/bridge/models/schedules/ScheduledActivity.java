package org.sagebionetworks.bridge.models.schedules;

import java.util.Comparator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonDeserialize(as = DynamoScheduledActivity.class)
public interface ScheduledActivity extends BridgeEntity {

    /**
     * Due to the use of the DynamoIndexHelper, which uses JSON deserialization to recover object
     * structure, we do not use @JsonIgnore annotation on DynamoScheduledActivity. Instead, we 
     * exclude those values using a filter and this writer.
     */
    public static final ObjectWriter SCHEDULED_ACTIVITY_WRITER = new BridgeObjectMapper().writer(
        new SimpleFilterProvider().addFilter("filter", 
        SimpleBeanPropertyFilter.serializeAllExcept("healthCode", "schedulePlanGuid")));

    public static ScheduledActivity create() {
        return new DynamoScheduledActivity();
    }

    // Sorts in reverse order.
    public static final Comparator<ScheduledActivity> SCHEDULED_ACTIVITY_COMPARATOR = new Comparator<ScheduledActivity>() {
        @Override
        public int compare(ScheduledActivity scheduledActivity1, ScheduledActivity scheduledActivity2) {
            // Sort activities with no set scheduled time behind activities with scheduled times.
            if (scheduledActivity1.getScheduledOn() == null) {
                return (scheduledActivity2.getScheduledOn() == null) ? 0 : 1;
            }
            if (scheduledActivity2.getScheduledOn() == null) {
                return -1;
            }
            int result = scheduledActivity1.getScheduledOn().compareTo(scheduledActivity2.getScheduledOn());
            if (result == 0) {
                Activity act1 = scheduledActivity1.getActivity();
                Activity act2 = scheduledActivity2.getActivity();
                if (act1 != null && act1.getLabel() != null && act2 != null && act2.getLabel() != null) {
                    result = scheduledActivity1.getActivity().getLabel().compareTo(scheduledActivity2.getActivity().getLabel());
                }
            }
            return result;
        }
    };

    public ScheduledActivityStatus getStatus();

    /**
     * Get the time zone for this request. Currently this is a field on the activity and must be set to get DateTime values
     * from other fields in the class. This forces one method of converting schedule times to local times in order to
     * satisfy the API's delivery of times in the user's time zone, and may change when we convert closer to the service
     * layer and remove this as a consideration from activity construction.
     * 
     * @return
     */
    public DateTimeZone getTimeZone();

    public void setTimeZone(DateTimeZone timeZone);
    
    public String getSchedulePlanGuid();

    public void setSchedulePlanGuid(String schedulePlanGuid);

    public String getGuid();

    public void setGuid(String guid);

    public String getHealthCode();

    public void setHealthCode(String healthCode);

    public Activity getActivity();

    public void setActivity(Activity activity);

    public DateTime getScheduledOn();

    public void setScheduledOn(DateTime scheduledOn);

    public DateTime getExpiresOn();

    public void setExpiresOn(DateTime expiresOn);

    public Long getStartedOn();

    public void setStartedOn(Long startedOn);

    public Long getFinishedOn();

    public void setFinishedOn(Long finishedOn);

    public boolean getPersistent();

    public void setPersistent(boolean persistent);
    
}
