package org.sagebionetworks.bridge;

import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.play.modules.BridgeProductionSpringContextModule;
import org.sagebionetworks.bridge.play.modules.BridgeTestSpringContextModule;
import org.sagebionetworks.bridge.runnable.FailableRunnable;

import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class TestUtils {
    
    private static final DateTime TEST_CREATED_ON = DateTime.parse("2015-01-27T00:38:32.486Z");

    // Helper metod to extract and assert on validator error messages.
    public static void assertValidatorMessage(InvalidEntityException e, String propName, String error) {
        Map<String,List<String>> errors = e.getErrors();
        List<String> messages = errors.get(propName);
        assertTrue(messages.get(0).contains(propName + error));
    }
    
    public static void assertResult(Result result, int statusCode, String message) throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        String resultMessage = node.get("message").asText();
        assertEquals(statusCode, result.status());
        assertEquals("application/json", result.contentType());
        assertEquals(message, resultMessage);
    }

    /**
     * Wrapper to wrap Play's Helpers.running() and Helpers.testServer() by swapping out the Production module with the
     * unit test one. Launches the test server on port 3333 and runs the arbitrary test code passed in as a
     * FailableRunnable. FailableRunnables can throw any exception and can be used with Java 8 lambdas.
     */
    public static void runningTestServerWithSpring(FailableRunnable runnable) {
        // in(new File(".") tells the app builder to use the project root directory as the server's root directory.
        // Specifically, it lets the app builder know where to find conf/application.conf
        // We also bind the unit test module and disable the Production module.
        Application testApp = new GuiceApplicationBuilder().in(new File("."))
                .bindings(new BridgeTestSpringContextModule()).disable(BridgeProductionSpringContextModule.class)
                .build();

        // Set up test server and execute.
        running(testServer(3333, testApp), () -> {
            try {
                runnable.run();
            } catch (Exception ex) {
                // Wrap in a RuntimeException, since regular Runnables can't throw checked exceptions.
                throw new RuntimeException(ex);
            }
        });
    }

    public static Map<SubpopulationGuid,ConsentStatus> toMap(ConsentStatus... statuses) {
        return TestUtils.toMap(Lists.newArrayList(statuses));
    }
    
    public static Map<SubpopulationGuid,ConsentStatus> toMap(Collection<ConsentStatus> statuses) {
        ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>();
        if (statuses != null) {
            for (ConsentStatus status : statuses) {
                builder.put(SubpopulationGuid.create(status.getSubpopulationGuid()), status);
            }
        }
        return builder.build();
    }
    
    /**
     * In the rare case where you need the context, you can use <code>Http.Context.current.get()</code>;
     */
    public static void mockPlayContextWithJson(String json) throws Exception {
        JsonNode node = new ObjectMapper().readTree(json);
        Http.RequestBody body = mock(Http.RequestBody.class);
        when(body.asJson()).thenReturn(node);

        Map<String,String[]> headers = Maps.newHashMap();
        headers.put(CONTENT_TYPE, new String[] {"text/json; charset=UTF-8"});
        headers.put(USER_AGENT, new String[] {"app/10"});
        Http.Request request = mock(Http.Request.class);
        Http.Response response = mock(Http.Response.class);

        when(request.getHeader(anyString())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String[] values = headers.get(args[0]);
            return (values == null || values.length == 0) ? null : values[0];
        });
        when(request.headers()).thenReturn(headers);
        when(request.body()).thenReturn(body);

        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);
        when(context.response()).thenReturn(response);

        Http.Context.current.set(context);
    }
    
    /**
     * In the rare case where you need the context, you can use <code>Http.Context.current.get()</code>;
     */
    public static void mockPlayContext(Http.Request mockRequest) {
        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(mockRequest);

        Http.Context.current.set(context);
    }
    
    /**
     * In the rare case where you need the context, you can use <code>Http.Context.current.get()</code>;
     */
    public static void mockPlayContext() throws Exception {
        Http.RequestBody body = mock(Http.RequestBody.class);
        when(body.asJson()).thenReturn(null);
        
        Http.Request request = mock(Http.Request.class);
        when(request.body()).thenReturn(body);
        mockPlayContext(request);
    }
    
    public static String randomName(Class<?> clazz) {
        return "test-" + clazz.getSimpleName().toLowerCase() + "-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }

    public static List<ScheduledActivity> runSchedulerForActivities(List<SchedulePlan> plans, ScheduleContext context) {
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        for (SchedulePlan plan : plans) {
            if (context.getCriteriaContext().getClientInfo().isTargetedAppVersion(plan.getMinAppVersion(), plan.getMaxAppVersion())) {
                Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
                // It's become possible for a user to match no schedule
                if (schedule != null) {
                    scheduledActivities.addAll(schedule.getScheduler().getScheduledActivities(plan, context));    
                }
            }
        }
        Collections.sort(scheduledActivities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        return scheduledActivities;
    }
    
    public static List<ScheduledActivity> runSchedulerForActivities(ScheduleContext context) {
        return runSchedulerForActivities(getSchedulePlans(context.getCriteriaContext().getStudyIdentifier()), context);
    }
    
    public static List<SchedulePlan> getSchedulePlans(StudyIdentifier studyId) {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("DDD");
        plan.setStrategy(getStrategy("P3D", TestConstants.TEST_1_ACTIVITY));
        plan.setStudyKey(studyId.getIdentifier());
        plan.setMinAppVersion(2);
        plan.setMaxAppVersion(5);
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("P1D", TestConstants.TEST_2_ACTIVITY));
        plan.setStudyKey(studyId.getIdentifier());
        plan.setMinAppVersion(9);
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("P2D", TestConstants.TEST_3_ACTIVITY));
        plan.setStudyKey(studyId.getIdentifier());
        plan.setMinAppVersion(5);
        plan.setMaxAppVersion(8);
        plans.add(plan);

        return plans;
    }
    
    public static SchedulePlan getSimpleSchedulePlan(StudyIdentifier studyId) {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        schedule.addActivity(new Activity.Builder().withLabel("Do task CCC").withTask("CCC").build());
        schedule.setExpires(Period.parse("PT60S"));
        schedule.setLabel("Test label for the user");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("GGG");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(studyId.getIdentifier());
        plan.setStrategy(strategy);
        return plan;
    }
    
    public static ScheduleStrategy getStrategy(String interval, Activity activity) {
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule " + activity.getLabel());
        schedule.setInterval(interval);
        schedule.setDelay("P1D");
        schedule.addTimes("13:00");
        schedule.setExpires("PT10H");
        schedule.addActivity(activity);
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        return strategy;
    }
    
    public static DynamoStudy getValidStudy(Class<?> clazz) {
        // This study will save without further modification.
        DynamoStudy study = new DynamoStudy();
        study.setName("Test Study ["+clazz.getSimpleName()+"]");
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.setVerifyEmailTemplate(new EmailTemplate("subject", "body with ${url}", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("subject", "body with ${url}", MimeType.TEXT));
        study.setIdentifier(TestUtils.randomName(clazz));
        study.setMinAgeOfConsent(18);
        study.setMaxNumOfParticipants(200);
        study.setSponsorName("The Council on Test Studies");
        study.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
        study.setSynapseDataAccessTeamId(1234L);
        study.setSynapseProjectId("test-synapse-project-id");
        study.setTechnicalEmail("bridge-testing+technical@sagebase.org");
        study.setSupportEmail("bridge-testing+support@sagebase.org");
        study.setUserProfileAttributes(Sets.newHashSet("a", "b"));
        study.setTaskIdentifiers(Sets.newHashSet("task1", "task2"));
        study.setDataGroups(Sets.newHashSet("beta_users", "production_users"));
        study.setStrictUploadValidationEnabled(true);
        study.setHealthCodeExportEnabled(true);
        study.setEmailVerificationEnabled(true);
        study.setExternalIdValidationEnabled(true);
        return study;
    }
    
    public static SchedulePlan getABTestSchedulePlan(StudyIdentifier studyId) {
        Schedule schedule1 = new Schedule();
        schedule1.setScheduleType(ScheduleType.RECURRING);
        schedule1.setCronTrigger("0 0 8 ? * TUE *");
        schedule1.addActivity(new Activity.Builder().withLabel("Do AAA task").withTask("AAA").build());
        schedule1.setExpires(Period.parse("PT1H"));
        schedule1.setLabel("Schedule 1");

        Schedule schedule2 = new Schedule();
        schedule2.setScheduleType(ScheduleType.RECURRING);
        schedule2.setCronTrigger("0 0 8 ? * TUE *");
        schedule2.addActivity(new Activity.Builder().withLabel("Do BBB task").withTask("BBB").build());
        schedule2.setExpires(Period.parse("PT1H"));
        schedule2.setLabel("Schedule 2");

        Schedule schedule3 = new Schedule();
        schedule3.setScheduleType(ScheduleType.RECURRING);
        schedule3.setCronTrigger("0 0 8 ? * TUE *");
        schedule3.addActivity(new Activity.Builder().withLabel("Do CCC task").withTask("CCC").build());
        schedule3.setExpires(Period.parse("PT1H"));
        schedule3.setLabel("Schedule 3");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("AAA");
        plan.setLabel("Test A/B Schedule");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(studyId.getIdentifier());
        
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(40, schedule1);
        strategy.addGroup(40, schedule2);
        strategy.addGroup(20, schedule3);
        plan.setStrategy(strategy);
        
        return plan;
    }
    
    public static Schedule getSchedule(String label) {
        Activity activity = new Activity.Builder().withLabel("Test survey")
                        .withSurvey("identifier", "ABC", TEST_CREATED_ON).build();

        Schedule schedule = new Schedule();
        schedule.setLabel(label);
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        return schedule;
    }
    
    public static Set<String> getFieldNamesSet(JsonNode node) {
        HashSet<String> set = new HashSet<>();
        for (Iterator<String> i = node.fieldNames(); i.hasNext(); ) {
            set.add(i.next());
        }
        return set;
    }
    
    /**
     * Converts single quote marks to double quote marks to convert JSON using single quotes to valid JSON. 
     * Useful to create more readable inline JSON in tests, because double quotes must be escaped in Java.
     */
    public static String createJson(String json) {
        return json.replaceAll("'", "\"");
    }
    
    public static Criteria createCriteria(Integer minAppVersion, Integer maxAppVersion, Set<String> allOfGroups, Set<String> noneOfGroups) {
        DynamoCriteria crit = new DynamoCriteria();
        crit.setMinAppVersion(minAppVersion);
        crit.setMaxAppVersion(maxAppVersion);
        crit.setAllOfGroups(allOfGroups);
        crit.setNoneOfGroups(noneOfGroups);
        return crit;
    }
    
    public static Criteria copyCriteria(Criteria criteria) {
        DynamoCriteria crit = new DynamoCriteria();
        if (criteria != null) {
            crit.setKey(criteria.getKey());
            crit.setLanguage(criteria.getLanguage());
            crit.setMinAppVersion(criteria.getMinAppVersion());
            crit.setMaxAppVersion(criteria.getMaxAppVersion());
            crit.setNoneOfGroups(criteria.getNoneOfGroups());
            crit.setAllOfGroups(criteria.getAllOfGroups());
        }
        return crit;
    }
    
    /**
     * Guava does not have a version of this method that also lets you add items.
     */
    @SuppressWarnings("unchecked")
    public static <T> LinkedHashSet<T> newLinkedHashSet(T... items) {
        LinkedHashSet<T> set = new LinkedHashSet<T>();
        for (T item : items) {
            set.add(item);    
        }
        return set;
    }
    
    public static String makeRandomTestEmail(Class<?> cls) {
        String devPart = BridgeConfigFactory.getConfig().getUser();
        String rndPart = TestUtils.randomName(cls);
        return String.format("bridge-testing+%s-%s@sagebase.org", devPart, rndPart);
    }
 }
