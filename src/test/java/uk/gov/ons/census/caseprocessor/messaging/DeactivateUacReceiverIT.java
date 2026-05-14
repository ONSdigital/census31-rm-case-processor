package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_UAC_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

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
import uk.gov.ons.census.caseprocessor.model.dto.DeactivateUacDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UacUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.repository.EventRepository;
import uk.gov.ons.census.caseprocessor.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.caseprocessor.testutils.DeleteDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.JunkDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.PubsubHelper;
import uk.gov.ons.census.caseprocessor.testutils.QueueSpy;
import uk.gov.ons.census.common.model.entity.Event;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
public class DeactivateUacReceiverIT {
  private static final String TEST_QID = "0123456789";

  @Value("${queueconfig.uac-update-topic}")
  private String uacUpdateTopic;

  @Value("${queueconfig.deactivate-uac-topic}")
  private String deactivateUacTopic;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private UacQidLinkRepository uacQidLinkRepository;
  @Autowired private EventRepository eventRepository;

  @BeforeEach
  public void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_UAC_SUBSCRIPTION, uacUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  public void testDeactivateUacReceiver() throws Exception {
    try (QueueSpy<EventDTO> uacRhQueue =
        pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class)) {
      // GIVEN
      EventDTO event = new EventDTO();
      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(deactivateUacTopic);
      junkDataHelper.junkify(eventHeader);
      event.setHeader(eventHeader);

      PayloadDTO payloadDTO = new PayloadDTO();
      DeactivateUacDTO deactivateUacDTO = new DeactivateUacDTO();
      deactivateUacDTO.setQid(TEST_QID);
      payloadDTO.setDeactivateUac(deactivateUacDTO);
      event.setPayload(payloadDTO);

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(UUID.randomUUID());
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setUac("test_uac");
      uacQidLink.setUacHash("fakeHash");
      uacQidLink.setActive(true);
      uacQidLink.setCaze(junkDataHelper.setupJunkCase());
      uacQidLinkRepository.save(uacQidLink);

      // WHEN
      pubsubHelper.sendMessageToPubsubProject(deactivateUacTopic, event);

      // THEN
      EventDTO actualEvent = uacRhQueue.checkExpectedMessageReceived();

      UacUpdateDTO uac = actualEvent.getPayload().getUacUpdate();
      assertThat(uac.getQid()).isEqualTo(TEST_QID);
      assertThat(uac.isActive()).isFalse();

      UacQidLink sentUacQidLinkUpdated = uacQidLinkRepository.findByQid(TEST_QID).get();

      assertThat(sentUacQidLinkUpdated.isActive()).isFalse();

      Event databaseEvent = eventRepository.findAll().get(0);
      assertThat(databaseEvent.getType()).isEqualTo(EventType.DEACTIVATE_UAC);
    }
  }
}
