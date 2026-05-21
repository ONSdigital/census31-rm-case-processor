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
import uk.gov.ons.census.caseprocessor.model.dto.EqLaunchDTO;
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
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.Event;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
public class EqLaunchReceiverIT {
  private static final String TEST_QID = "1234334";
  private static final String INBOUND_TOPIC = "event_eq-launch";

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
  public void testEqLaunchLogsEventSetsFlagAndEmitsCorrectUACUpdatedEvent() throws Exception {
    // GIVEN

    try (QueueSpy<EventDTO> outboundUacQueueSpy =
        pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class)) {
      Case caze = junkDataHelper.setupJunkCase();

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(UUID.randomUUID());
      uacQidLink.setCaze(caze);
      uacQidLink.setUac("Junk");
      uacQidLink.setUacHash("junkHash");
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setCaze(caze);
      uacQidLink.setEqLaunched(false);
      uacQidLinkRepository.saveAndFlush(uacQidLink);

      EventDTO eqLaunchedEvent = new EventDTO();
      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(INBOUND_TOPIC);
      junkDataHelper.junkify(eventHeader);
      eqLaunchedEvent.setHeader(eventHeader);

      EqLaunchDTO EqLaunch = new EqLaunchDTO();
      EqLaunch.setQid(uacQidLink.getQid());
      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setEqLaunch(EqLaunch);
      eqLaunchedEvent.setPayload(payloadDTO);

      // WHEN
      pubsubHelper.sendMessageToPubsubProject(INBOUND_TOPIC, eqLaunchedEvent);

      // THEN
      EventDTO uacUpdatedEvent = outboundUacQueueSpy.checkExpectedMessageReceived();
      UacUpdateDTO emittedUac = uacUpdatedEvent.getPayload().getUacUpdate();
      assertThat(emittedUac.isEqLaunched()).isTrue();

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getDescription()).isEqualTo("EQ launched");
      UacQidLink actualUacQidLink = event.getUacQidLink();
      assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID);
      assertThat(actualUacQidLink.getCaze().getId()).isEqualTo(caze.getId());
    }
  }
}
