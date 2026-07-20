package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.caseprocessor.testutils.MessageConstructor.constructMessage;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.SurveyLaunchedDTO;
import uk.gov.ons.census.caseprocessor.service.SurveyLaunchedService;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
public class SurveyLaunchedReceiverTest {
  private final String TEST_QID_ID = "1234567890123456";

  @Mock private EventLogger eventLogger;
  @Mock private SurveyLaunchedService surveyLaunchedService;

  @InjectMocks SurveyLaunchedReceiver underTest;

  @Test
  public void testSurveyLaunchedEventFromRH() {
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    managementEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("RH");
    managementEvent.getHeader().setMessageType(EventType.SURVEY_LAUNCHED);
    managementEvent.setPayload(new PayloadDTO());

    SurveyLaunchedDTO surveyLaunch = new SurveyLaunchedDTO();
    surveyLaunch.setQuestionnaireId(TEST_QID_ID);
    managementEvent.getPayload().setSurveyLaunched(surveyLaunch);

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setQid(TEST_QID_ID);
    expectedUacQidLink.setEqLaunched(true);
    Message<byte[]> message = constructMessage(managementEvent);

    // Given
    when(surveyLaunchedService.handleSurveyLaunchedEvent(managementEvent))
        .thenReturn(expectedUacQidLink);

    // when
    underTest.receiveMessage(message);

    // then
    verify(surveyLaunchedService).handleSurveyLaunchedEvent(managementEvent);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);

    verify(eventLogger)
        .logUacQidEvent(
            uacQidLinkCaptor.capture(),
            eq("Survey launched"),
            eq(EventType.SURVEY_LAUNCHED),
            eq(managementEvent),
            eq(message));

    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID_ID);
    assertThat(actualUacQidLink.isEqLaunched()).isTrue();

    verifyNoMoreInteractions(eventLogger);
    verifyNoMoreInteractions(surveyLaunchedService);
  }

  @Test
  void testSurveyLaunchedEventFromRHWrongEventType() {
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    managementEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("RH");
    managementEvent.getHeader().setMessageType(EventType.RECEIPT);
    managementEvent.setPayload(new PayloadDTO());

    SurveyLaunchedDTO surveyLaunch = new SurveyLaunchedDTO();
    surveyLaunch.setQuestionnaireId(TEST_QID_ID);
    managementEvent.getPayload().setSurveyLaunched(surveyLaunch);

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setQid(TEST_QID_ID);
    expectedUacQidLink.setEqLaunched(true);
    Message<byte[]> message = constructMessage(managementEvent);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.receiveMessage(message));

    Assertions.assertThat(thrown.getMessage())
        .isEqualTo("Event Type 'RECEIPT' is invalid on this topic");
  }
}
