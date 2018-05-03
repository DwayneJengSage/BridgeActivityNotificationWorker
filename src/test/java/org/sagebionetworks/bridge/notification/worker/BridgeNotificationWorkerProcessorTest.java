package org.sagebionetworks.bridge.notification.worker;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import org.joda.time.LocalDate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.notification.helper.BridgeHelper;
import org.sagebionetworks.bridge.notification.helper.DynamoHelper;
import org.sagebionetworks.bridge.rest.model.AccountSummary;
import org.sagebionetworks.bridge.sqs.PollSqsWorkerBadRequestException;

public class BridgeNotificationWorkerProcessorTest {
    private static final String DATE_STRING = "2018-04-27";
    private static final LocalDate DATE = LocalDate.parse(DATE_STRING);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String STUDY_ID = "test-study";
    private static final String TAG = "dummy tag";

    private BridgeHelper mockBridgeHelper;
    private DynamoHelper mockDynamoHelper;
    private BridgeNotificationWorkerProcessor processor;

    @BeforeMethod
    public void before() throws Exception {
        // Set up mocks
        mockBridgeHelper = mock(BridgeHelper.class);
        mockDynamoHelper = mock(DynamoHelper.class);

        // Create processor. Spy the processor so we can test processAccountForDate() in a separate set of tests.
        processor = spy(new BridgeNotificationWorkerProcessor());
        processor.setBridgeHelper(mockBridgeHelper);
        processor.setDynamoHelper(mockDynamoHelper);
        processor.setPerUserRateLimit(1000.0);

        doNothing().when(processor).processAccountForDate(any(), any(), any());
    }

    @Test(expectedExceptions = PollSqsWorkerBadRequestException.class, expectedExceptionsMessageRegExp =
            "studyId must be specified")
    public void argsNoStudyId() throws Exception {
        ObjectNode requestNode = makeValidRequestNode();
        requestNode.remove(BridgeNotificationWorkerProcessor.REQUEST_PARAM_STUDY_ID);
        processor.accept(requestNode);
    }

    @Test(expectedExceptions = PollSqsWorkerBadRequestException.class, expectedExceptionsMessageRegExp =
            "studyId must be specified")
    public void argsNullStudyId() throws Exception {
        ObjectNode requestNode = makeValidRequestNode();
        requestNode.putNull(BridgeNotificationWorkerProcessor.REQUEST_PARAM_STUDY_ID);
        processor.accept(requestNode);
    }

    @Test(expectedExceptions = PollSqsWorkerBadRequestException.class, expectedExceptionsMessageRegExp =
            "studyId must be a string")
    public void argsStudyIdWrongType() throws Exception {
        ObjectNode requestNode = makeValidRequestNode();
        requestNode.put(BridgeNotificationWorkerProcessor.REQUEST_PARAM_STUDY_ID, 1234);
        processor.accept(requestNode);
    }

    @Test(expectedExceptions = PollSqsWorkerBadRequestException.class, expectedExceptionsMessageRegExp =
            "date must be specified")
    public void argsNoDate() throws Exception {
        ObjectNode requestNode = makeValidRequestNode();
        requestNode.remove(BridgeNotificationWorkerProcessor.REQUEST_PARAM_DATE);
        processor.accept(requestNode);
    }

    @Test(expectedExceptions = PollSqsWorkerBadRequestException.class, expectedExceptionsMessageRegExp =
            "date must be specified")
    public void argsNullDate() throws Exception {
        ObjectNode requestNode = makeValidRequestNode();
        requestNode.putNull(BridgeNotificationWorkerProcessor.REQUEST_PARAM_DATE);
        processor.accept(requestNode);
    }

    @Test(expectedExceptions = PollSqsWorkerBadRequestException.class, expectedExceptionsMessageRegExp =
            "date must be a string")
    public void argsDateWrongType() throws Exception {
        ObjectNode requestNode = makeValidRequestNode();
        requestNode.put(BridgeNotificationWorkerProcessor.REQUEST_PARAM_DATE, 20180427);
        processor.accept(requestNode);
    }

    @Test(expectedExceptions = PollSqsWorkerBadRequestException.class, expectedExceptionsMessageRegExp =
            "date must be in the format YYYY-MM-DD")
    public void argsDateInvalidFormat() throws Exception {
        ObjectNode requestNode = makeValidRequestNode();
        requestNode.put(BridgeNotificationWorkerProcessor.REQUEST_PARAM_DATE, "April 27, 2018");
        processor.accept(requestNode);
    }

    @Test
    public void multipleUsers() throws Exception {
        // Bridge returns 3 users. The second user throws an exception during processing.

        // Set up mocks
        AccountSummary accountSummary1 = new AccountSummary().id("user-1");
        AccountSummary accountSummary2 = new AccountSummary().id("user-2");
        AccountSummary accountSummary3 = new AccountSummary().id("user-3");
        when(mockBridgeHelper.getAllAccountSummaries(STUDY_ID)).thenReturn(ImmutableList.of(accountSummary1,
                accountSummary2, accountSummary3).iterator());

        doThrow(IOException.class).when(processor).processAccountForDate(STUDY_ID, DATE, accountSummary2);

        // Execute
        processor.accept(makeValidRequestNode());

        // Verify calls to processAccount()
        verify(processor).processAccountForDate(STUDY_ID, DATE, accountSummary1);
        verify(processor).processAccountForDate(STUDY_ID, DATE, accountSummary2);
        verify(processor).processAccountForDate(STUDY_ID, DATE, accountSummary3);

        // Verify call to dynamoHelper.writeWorkerLog()
        verify(mockDynamoHelper).writeWorkerLog(TAG);
    }

    private static ObjectNode makeValidRequestNode() {
        ObjectNode requestNode = JSON_MAPPER.createObjectNode();
        requestNode.put(BridgeNotificationWorkerProcessor.REQUEST_PARAM_STUDY_ID, STUDY_ID);
        requestNode.put(BridgeNotificationWorkerProcessor.REQUEST_PARAM_DATE, DATE_STRING);
        requestNode.put(BridgeNotificationWorkerProcessor.REQUEST_PARAM_TAG, TAG);
        return requestNode;
    }
}
