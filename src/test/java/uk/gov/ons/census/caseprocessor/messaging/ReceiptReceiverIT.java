package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
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
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.ReceiptDTO;
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
public class ReceiptReceiverIT {
  private static final String TEST_QID = "010000";
  private static final UUID TEST_UACLINK_ID = UUID.randomUUID();
  private static final String INBOUND_RECEIPT_TOPIC = "event_receipt";

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
  public void testReceipt() throws Exception {
    try (QueueSpy<EventDTO> outboundUacQueueSpy =
        pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class)) {
      // GIVEN

      Case caze = junkDataHelper.setupJunkCase();

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(TEST_UACLINK_ID);
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setUac("abc");
      uacQidLink.setUacHash("fakeHash");
      uacQidLink.setCaze(caze);
      uacQidLink.setActive(true);
      uacQidLink.setReceiptReceived(false);
      uacQidLink.setSurveyLaunched(true);
      uacQidLinkRepository.saveAndFlush(uacQidLink);

      ReceiptDTO receiptDTO = new ReceiptDTO();
      receiptDTO.setQid(TEST_QID);
      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setReceipt(receiptDTO);
      EventDTO event = new EventDTO();
      event.setPayload(payloadDTO);

      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(INBOUND_RECEIPT_TOPIC);
      eventHeader.setMessageType(EventType.RECEIPT);
      junkDataHelper.junkify(eventHeader);
      event.setHeader(eventHeader);

      pubsubHelper.sendMessageToPubsubProject(INBOUND_RECEIPT_TOPIC, event);

      //  THEN
      EventDTO uacUpdatedEvent = outboundUacQueueSpy.checkExpectedMessageReceived();
      UacUpdateDTO emittedUac = uacUpdatedEvent.getPayload().getUacUpdate();
      assertThat(emittedUac.isActive()).isFalse();
      // assertThat(emittedUac.isReceiptReceived()).isTrue();

      List<Event> storedEvents = eventRepository.findAll();
      assertThat(storedEvents.size()).isEqualTo(1);
      assertThat(storedEvents.get(0).getUacQidLink().getId()).isEqualTo(TEST_UACLINK_ID);
      assertThat(storedEvents.get(0).getType()).isEqualTo(EventType.RECEIPT);
    }
  }
}
