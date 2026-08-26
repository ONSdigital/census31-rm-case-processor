package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_CASE_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_UAC_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.ons.census.caseprocessor.model.dto.CaseUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.RespondentAuthenticatedDTO;
import uk.gov.ons.census.caseprocessor.model.dto.SurveyLaunchedDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UacUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.repository.EventRepository;
import uk.gov.ons.census.caseprocessor.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.caseprocessor.testutils.DeleteDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.JunkDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.PubsubHelper;
import uk.gov.ons.census.caseprocessor.testutils.QueueSpy;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.Event;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
public class SurveyLaunchedReceiverIT {
  private static final String TEST_QID = "1234334";
  private static final String INBOUND_TOPIC = "event_survey-launched";

  @Value("${queueconfig.uac-update-topic}")
  private String uacUpdateTopic;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @BeforeEach
  public void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_UAC_SUBSCRIPTION, uacUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  public void testSurveyLaunchedLogsEventSetsFlagAndEmitsCorrectUACUpdatedEvent() throws Exception {
    // GIVEN

    try (QueueSpy<EventDTO> outboundUacQueueSpy =
            pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class);
        QueueSpy<EventDTO> outboundCaseQueueSpy =
            pubsubHelper.pubsubProjectListen(OUTBOUND_CASE_SUBSCRIPTION, EventDTO.class)) {
      Case caze = junkDataHelper.setupJunkCase();

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(UUID.randomUUID());
      uacQidLink.setCaze(caze);
      uacQidLink.setUac("Junk");
      uacQidLink.setUacHash("junkHash");
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setCaze(caze);
      uacQidLink.setSurveyLaunched(false);
      uacQidLinkRepository.saveAndFlush(uacQidLink);

      EventDTO surveyLaunchedEvent = new EventDTO();
      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(INBOUND_TOPIC);
      eventHeader.setChannel("RH");
      eventHeader.setMessageType(EventType.SURVEY_LAUNCHED);
      junkDataHelper.junkify(eventHeader);
      surveyLaunchedEvent.setHeader(eventHeader);

      SurveyLaunchedDTO surveyLaunch = new SurveyLaunchedDTO();
      surveyLaunch.setQuestionnaireId(uacQidLink.getQid());
      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setSurveyLaunched(surveyLaunch);
      surveyLaunchedEvent.setPayload(payloadDTO);

      // WHEN
      pubsubHelper.sendMessageToPubsubProject(INBOUND_TOPIC, surveyLaunchedEvent);

      // THEN
      EventDTO uacUpdatedEvent = outboundUacQueueSpy.checkExpectedMessageReceived();
      UacUpdateDTO emittedUac = uacUpdatedEvent.getPayload().getUacUpdate();
      assertThat(emittedUac.isSurveyLaunched()).isTrue();
      assertThat(emittedUac.getFormType()).isNull();

      EventDTO caseUpdatedEvent = outboundCaseQueueSpy.checkExpectedMessageReceived();
      CaseUpdateDTO emittedCase = caseUpdatedEvent.getPayload().getCaseUpdate();
      assertThat(emittedCase.isSurveyLaunched()).isTrue();

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getDescription()).isEqualTo("Survey launched");
      UacQidLink actualUacQidLink = event.getUacQidLink();
      assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID);
      assertThat(actualUacQidLink.getCaze().getId()).isEqualTo(caze.getId());
    }
  }

  @Test
  public void testRespondentAuthenticatedLogsEventButDoesNotEmitMessages() throws Exception {
    // GIVEN
    try (QueueSpy<EventDTO> outboundUacQueueSpy =
            pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class);
        QueueSpy<EventDTO> outboundCaseQueueSpy =
            pubsubHelper.pubsubProjectListen(OUTBOUND_CASE_SUBSCRIPTION, EventDTO.class)) {
      Case caze = junkDataHelper.setupJunkCase();

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(UUID.randomUUID());
      uacQidLink.setCaze(caze);
      uacQidLink.setUac("Junk");
      uacQidLink.setUacHash("junkHash");
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setSurveyLaunched(false);
      uacQidLinkRepository.saveAndFlush(uacQidLink);

      EventDTO respondentAuthenticatedEvent = new EventDTO();
      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(INBOUND_TOPIC);
      eventHeader.setChannel("RH");
      eventHeader.setMessageType(EventType.RESPONDENT_AUTHENTICATED);
      junkDataHelper.junkify(eventHeader);
      respondentAuthenticatedEvent.setHeader(eventHeader);

      RespondentAuthenticatedDTO respondentAuthenticated = new RespondentAuthenticatedDTO();
      respondentAuthenticated.setQuestionnaireId(uacQidLink.getQid());
      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setRespondentAuthenticated(respondentAuthenticated);
      respondentAuthenticatedEvent.setPayload(payloadDTO);

      // WHEN
      pubsubHelper.sendMessageToPubsubProject(INBOUND_TOPIC, respondentAuthenticatedEvent);

      // THEN
      outboundUacQueueSpy.checkMessageIsNotReceived(5);
      outboundCaseQueueSpy.checkMessageIsNotReceived(1);

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getDescription()).isEqualTo("Respondent authenticated");
      assertThat(event.getType()).isEqualTo(EventType.RESPONDENT_AUTHENTICATED);
      UacQidLink actualUacQidLink = event.getUacQidLink();
      assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID);
      assertThat(actualUacQidLink.getCaze().getId()).isEqualTo(caze.getId());
    }
  }
}
