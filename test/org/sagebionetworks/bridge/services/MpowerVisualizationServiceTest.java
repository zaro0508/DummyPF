package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.dao.MpowerVisualizationDao;
import org.sagebionetworks.bridge.dynamodb.DynamoMpowerVisualization;
import org.sagebionetworks.bridge.dynamodb.DynamoMpowerVisualizationTest;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.visualization.MpowerVisualization;

public class MpowerVisualizationServiceTest {
    private static final String DUMMY_HEALTH_CODE = "dummyHealthCode";

    @Test
    public void specifiedStartAndEndDates() {
        // mock dao
        MpowerVisualizationDao mockDao = mock(MpowerVisualizationDao.class);
        JsonNode mockViz = mock(JsonNode.class);
        when(mockDao.getVisualization(DUMMY_HEALTH_CODE, LocalDate.parse("2016-02-06"), LocalDate.parse("2016-02-08")))
                .thenReturn(mockViz);

        // set up service
        MpowerVisualizationService svc = new MpowerVisualizationService();
        svc.setMpowerVisualizationDao(mockDao);

        // execute and validate
        JsonNode result = svc.getVisualization(DUMMY_HEALTH_CODE, LocalDate.parse("2016-02-06"),
                LocalDate.parse("2016-02-08"));
        assertSame(mockViz, result);
    }

    @Test
    public void defaultStartAndEndDates() {
        // mock now
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2016-02-08T09:00-0800").getMillis());

        try {
            // mock dao
            MpowerVisualizationDao mockDao = mock(MpowerVisualizationDao.class);
            JsonNode mockViz = mock(JsonNode.class);
            when(mockDao.getVisualization(DUMMY_HEALTH_CODE, LocalDate.parse("2016-02-07"),
                    LocalDate.parse("2016-02-07"))).thenReturn(mockViz);

            // set up service
            MpowerVisualizationService svc = new MpowerVisualizationService();
            svc.setMpowerVisualizationDao(mockDao);

            // execute and validate
            JsonNode result = svc.getVisualization(DUMMY_HEALTH_CODE, null, null);
            assertSame(mockViz, result);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    @Test(expected = BadRequestException.class)
    public void startDateAfterEndDate() {
        new MpowerVisualizationService().getVisualization(DUMMY_HEALTH_CODE, LocalDate.parse("2016-02-09"),
                LocalDate.parse("2016-02-08"));
    }

    @Test(expected = BadRequestException.class)
    public void dateRangeTooWide() {
        // Two months is definitely too wide. Don't need exactly 45 days.
        new MpowerVisualizationService().getVisualization(DUMMY_HEALTH_CODE, LocalDate.parse("2016-01-01"),
                LocalDate.parse("2016-03-01"));
    }

    @Test(expected = InvalidEntityException.class)
    public void writeNull() {
        new MpowerVisualizationService().writeVisualization(null);
    }

    @Test(expected = InvalidEntityException.class)
    public void writeInvalid() {
        new MpowerVisualizationService().writeVisualization(new DynamoMpowerVisualization());
    }

    @Test
    public void writeSuccess() {
        // set up input, mock dao, and service
        MpowerVisualization viz = DynamoMpowerVisualizationTest.makeValidMpowerVisualization();
        MpowerVisualizationDao mockDao = mock(MpowerVisualizationDao.class);
        MpowerVisualizationService svc = new MpowerVisualizationService();
        svc.setMpowerVisualizationDao(mockDao);

        // execute
        svc.writeVisualization(viz);

        // verify we called through to the DAO
        verify(mockDao).writeVisualization(viz);
    }
}
