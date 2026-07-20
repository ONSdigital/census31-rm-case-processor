package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.SurveyLaunchedDTO;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
public class SurveyLaunchedServiceTest {
  private final String TEST_QID_ID = "1234567890123456";

  @Mock private UacService uacService;
  @Mock private CaseService caseService;

  @InjectMocks SurveyLaunchedService underTest;

  @Test
  public void testHandleSurveyLaunchedEvent() {
    // Given
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    managementEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("RH");
    managementEvent.getHeader().setMessageType(EventType.SURVEY_LAUNCHED);
    SurveyLaunchedDTO surveyLaunchedDTO = new SurveyLaunchedDTO();
    surveyLaunchedDTO.setQuestionnaireId(TEST_QID_ID);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setSurveyLaunched(surveyLaunchedDTO);
    managementEvent.setPayload(payloadDTO);

    Case caze = new Case();
    caze.setSurveyLaunched(false);

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setQid(TEST_QID_ID);
    expectedUacQidLink.setSurveyLaunched(false);
    expectedUacQidLink.setCaze(caze);

    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);
    when(uacService.saveAndEmitUacUpdateEvent(
            expectedUacQidLink,
            managementEvent.getHeader().getCorrelationId(),
            managementEvent.getHeader().getOriginatingUser()))
        .thenReturn(expectedUacQidLink);

    // When
    underTest.handleSurveyLaunchedEvent(managementEvent);

    // Then
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkArgumentCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink capturedUacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertThat(capturedUacQidLink.isSurveyLaunched()).isTrue();
    assertThat(capturedUacQidLink.getCaze().isSurveyLaunched()).isTrue();
  }

  @Test
  public void testHandleSurveyLaunchedEventNoCase() {
    // Given
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    managementEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("RH");
    managementEvent.getHeader().setMessageType(EventType.SURVEY_LAUNCHED);
    SurveyLaunchedDTO surveyLaunchedDTO = new SurveyLaunchedDTO();
    surveyLaunchedDTO.setQuestionnaireId(TEST_QID_ID);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setSurveyLaunched(surveyLaunchedDTO);
    managementEvent.setPayload(payloadDTO);

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setQid(TEST_QID_ID);
    expectedUacQidLink.setSurveyLaunched(false);

    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);
    when(uacService.saveAndEmitUacUpdateEvent(
            expectedUacQidLink,
            managementEvent.getHeader().getCorrelationId(),
            managementEvent.getHeader().getOriginatingUser()))
        .thenReturn(expectedUacQidLink);

    // When
    underTest.handleSurveyLaunchedEvent(managementEvent);

    // Then
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkArgumentCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink capturedUacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertThat(capturedUacQidLink.isSurveyLaunched()).isTrue();

    verifyNoInteractions(caseService);
  }
}
