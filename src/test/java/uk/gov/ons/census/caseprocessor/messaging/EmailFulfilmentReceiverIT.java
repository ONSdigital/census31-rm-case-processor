package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.EMAIL_CONFIRMATION_TOPIC;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_UAC_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.ons.census.caseprocessor.client.UacQidServiceClient;
import uk.gov.ons.census.caseprocessor.model.dto.EmailConfirmation;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UacQidDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UacUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.caseprocessor.testutils.DeleteDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.JunkDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.PubsubHelper;
import uk.gov.ons.census.caseprocessor.testutils.QueueSpy;
import uk.gov.ons.census.caseprocessor.utils.HashHelper;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
class EmailFulfilmentReceiverIT {

  private static final String PACK_CODE = "TEST_EMAIL";
  private static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");
  private static final Map<String, String> TEST_PERSONALISATION = Map.of("foo", "bar");

  @Value("${queueconfig.uac-update-topic}")
  private String uacUpdateTopic;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Autowired private UacQidServiceClient uacQidServiceClient;

  @BeforeEach
  public void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_UAC_SUBSCRIPTION, uacUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  void testEmailFulfilment() throws Exception {
    // Given
    // Get a new UAC QID pair
    List<UacQidDTO> uacQidDTOList = uacQidServiceClient.getUacQids(1, 1);
    UacQidDTO emailUacQid = uacQidDTOList.get(0);

    // Create the case
    Case testCase = junkDataHelper.setupJunkCase();

    // Build the event message
    EmailConfirmation emailConfirmation = new EmailConfirmation();
    emailConfirmation.setUac(emailUacQid.getUac());
    emailConfirmation.setQid(emailUacQid.getQid());
    emailConfirmation.setCaseId(testCase.getId());
    emailConfirmation.setPackCode(PACK_CODE);
    emailConfirmation.setUacMetadata(TEST_UAC_METADATA);
    emailConfirmation.setPersonalisation(TEST_PERSONALISATION);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setEmailConfirmation(emailConfirmation);

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setTopic(EMAIL_CONFIRMATION_TOPIC);
    eventHeader.setMessageType(EventType.EMAIL_FULFILMENT);
    junkDataHelper.junkify(eventHeader);

    EventDTO event = new EventDTO();
    event.setHeader(eventHeader);
    event.setPayload(payloadDTO);

    try (QueueSpy<EventDTO> outboundUacQueueSpy =
        pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class)) {
      pubsubHelper.sendMessage(EMAIL_CONFIRMATION_TOPIC, event);
      EventDTO emittedEvent = outboundUacQueueSpy.checkExpectedMessageReceived();

      assertThat(emittedEvent.getHeader().getTopic()).isEqualTo(uacUpdateTopic);

      UacUpdateDTO uacUpdatedEvent = emittedEvent.getPayload().getUacUpdate();
      assertThat(uacUpdatedEvent.getCaseId()).isEqualTo(testCase.getId());
      assertThat(uacUpdatedEvent.getUacHash()).isEqualTo(HashHelper.hash(emailUacQid.getUac()));
      assertThat(uacUpdatedEvent.getQid()).isEqualTo(emailUacQid.getQid());
    }

    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    assertThat(uacQidLinks.get(0).getCaze().getId()).isEqualTo(testCase.getId());
    assertThat(uacQidLinks.get(0).getMetadata()).isEqualTo(TEST_UAC_METADATA);
  }
}
