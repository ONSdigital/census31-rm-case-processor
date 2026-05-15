package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_CASE_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

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
import uk.gov.ons.census.caseprocessor.model.dto.RefusalDTO;
import uk.gov.ons.census.caseprocessor.model.dto.RefusalTypeDTO;
import uk.gov.ons.census.caseprocessor.model.repository.EventRepository;
import uk.gov.ons.census.caseprocessor.testutils.DeleteDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.JunkDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.PubsubHelper;
import uk.gov.ons.census.caseprocessor.testutils.QueueSpy;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.Event;
import uk.gov.ons.census.common.model.entity.EventType;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
class RefusalReceiverIT {
  private static final String INBOUND_REFUSAL_TOPIC = "event_refusal";

  @Value("${queueconfig.case-update-topic}")
  private String caseUpdateTopic;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private EventRepository eventRepository;

  @BeforeEach
  void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_CASE_SUBSCRIPTION, caseUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  void testRefusal() throws Exception {
    try (QueueSpy<EventDTO> outboundCaseQueueSpy =
        pubsubHelper.pubsubProjectListen(OUTBOUND_CASE_SUBSCRIPTION, EventDTO.class)) {
      // GIVEN

      Case caze = junkDataHelper.setupJunkCase();

      RefusalDTO refusalDTO = new RefusalDTO();
      refusalDTO.setCaseId(caze.getId());
      refusalDTO.setType(RefusalTypeDTO.EXTRAORDINARY_REFUSAL);
      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setRefusal(refusalDTO);
      EventDTO event = new EventDTO();
      event.setPayload(payloadDTO);

      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(INBOUND_REFUSAL_TOPIC);
      junkDataHelper.junkify(eventHeader);
      event.setHeader(eventHeader);

      pubsubHelper.sendMessageToPubsubProject(INBOUND_REFUSAL_TOPIC, event);

      //  THEN
      EventDTO actualEvent = outboundCaseQueueSpy.checkExpectedMessageReceived();

      CaseUpdateDTO emittedCase = actualEvent.getPayload().getCaseUpdate();
      assertThat(emittedCase.getCaseId()).isEqualTo(caze.getId());
      assertThat(emittedCase.getRefusalReceived()).isEqualTo(RefusalTypeDTO.EXTRAORDINARY_REFUSAL);
      assertThat(emittedCase.getAddress().getAddressLine1()).isEqualTo(caze.getAddressLine1());
      assertThat(emittedCase.getAddress().getAddressType()).isEqualTo(caze.getAddressType());
      assertThat(emittedCase.getAddress().getPostcode()).isEqualTo(caze.getPostcode());

      assertThat(eventRepository.findAll().size()).isEqualTo(1);
      Event databaseEvent = eventRepository.findAll().get(0);
      assertThat(databaseEvent.getCaze().getId()).isEqualTo(caze.getId());
      assertThat(databaseEvent.getType()).isEqualTo(EventType.REFUSAL);
    }
  }

  @Test
  void testRefusalWithErasure() throws Exception {
    try (QueueSpy<EventDTO> outboundCaseQueueSpy =
        pubsubHelper.pubsubProjectListen(OUTBOUND_CASE_SUBSCRIPTION, EventDTO.class)) {
      // GIVEN

      Case caze = junkDataHelper.setupJunkCase();

      RefusalDTO refusalDTO = new RefusalDTO();
      refusalDTO.setCaseId(caze.getId());
      refusalDTO.setType(RefusalTypeDTO.EXTRAORDINARY_REFUSAL);
      refusalDTO.setEraseData(true);
      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setRefusal(refusalDTO);
      EventDTO event = new EventDTO();
      event.setPayload(payloadDTO);

      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(INBOUND_REFUSAL_TOPIC);
      junkDataHelper.junkify(eventHeader);
      event.setHeader(eventHeader);

      // WHEN
      pubsubHelper.sendMessageToPubsubProject(INBOUND_REFUSAL_TOPIC, event);

      // THEN
      EventDTO actualEvent = outboundCaseQueueSpy.checkExpectedMessageReceived();

      CaseUpdateDTO emittedCase = actualEvent.getPayload().getCaseUpdate();
      assertThat(emittedCase.getCaseId()).isEqualTo(caze.getId());
      assertThat(emittedCase.getRefusalReceived()).isEqualTo(RefusalTypeDTO.EXTRAORDINARY_REFUSAL);
      assertThat(emittedCase.isInvalid()).isTrue();

      assertThat(eventRepository.findAll().size()).isEqualTo(2);
      Event databaseRefusalEvent = eventRepository.findAll().get(1);
      assertThat(databaseRefusalEvent.getCaze().getId()).isEqualTo(caze.getId());
      assertThat(databaseRefusalEvent.getType()).isEqualTo(EventType.REFUSAL);
      Event databaseDataErasureEvent = eventRepository.findAll().get(0);
      assertThat(databaseDataErasureEvent.getCaze().getId()).isEqualTo(caze.getId());
      assertThat(databaseDataErasureEvent.getType()).isEqualTo(EventType.ERASE_DATA);
    }
  }
}
