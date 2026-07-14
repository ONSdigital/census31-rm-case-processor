package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_CASE_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import org.assertj.core.api.Assertions;
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
import uk.gov.ons.census.caseprocessor.model.dto.InvalidCase;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
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
public class InvalidCaseReceiverIT {
  private static final String INBOUND_INVALID_CASE_TOPIC = "event_invalid-case";

  @Value("${queueconfig.case-update-topic}")
  private String caseUpdateTopic;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private EventRepository eventRepository;

  @BeforeEach
  public void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_CASE_SUBSCRIPTION, caseUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  public void testInvalidCase() throws Exception {
    try (QueueSpy<EventDTO> outboundCaseQueueSpy =
        pubsubHelper.pubsubProjectListen(OUTBOUND_CASE_SUBSCRIPTION, EventDTO.class)) {
      // GIVEN

      Case caze = junkDataHelper.setupJunkCase();

      InvalidCase invalidCase = new InvalidCase();
      invalidCase.setCaseId(caze.getId());
      invalidCase.setReason("Not found");
      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setInvalidCase(invalidCase);
      EventDTO event = new EventDTO();
      event.setPayload(payloadDTO);

      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(INBOUND_INVALID_CASE_TOPIC);
      eventHeader.setMessageType(EventType.INVALID_CASE);
      junkDataHelper.junkify(eventHeader);
      event.setHeader(eventHeader);

      //  When
      pubsubHelper.sendMessageToPubsubProject(INBOUND_INVALID_CASE_TOPIC, event);

      //  Then
      EventDTO actualEvent = outboundCaseQueueSpy.checkExpectedMessageReceived();

      CaseUpdateDTO emittedCase = actualEvent.getPayload().getCaseUpdate();
      assertThat(emittedCase.getCaseId()).isEqualTo(caze.getId());
      assertThat(emittedCase.isInvalid()).isTrue();
      Assertions.assertThat(emittedCase.getAddress().getAddressLine1())
          .isEqualTo(caze.getAddressLine1());
      Assertions.assertThat(emittedCase.getAddress().getAddressType())
          .isEqualTo(caze.getAddressType());
      Assertions.assertThat(emittedCase.getAddress().getPostcode()).isEqualTo(caze.getPostcode());
      Assertions.assertThat(emittedCase.getAddress().getRegion()).isEqualTo(caze.getRegion());

      assertThat(eventRepository.findAll().size()).isEqualTo(1);
      Event databaseEvent = eventRepository.findAll().get(0);
      assertThat(databaseEvent.getCaze().getId()).isEqualTo(caze.getId());
      assertThat(databaseEvent.getType()).isEqualTo(EventType.INVALID_CASE);
    }
  }
}
