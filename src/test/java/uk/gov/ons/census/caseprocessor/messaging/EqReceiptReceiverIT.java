package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_CASE_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_UAC_SUBSCRIPTION;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
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
class EqReceiptReceiverIT {
  private static final String QID = "010000";
  private static final String EQ_RECEIPT_TOPIC = "projects/dummy-eq-project/topics/eq_receipt";

  @Value("${queueconfig.uac-update-topic}")
  private String uacUpdateTopic;

  @Value("${queueconfig.case-update-topic}")
  private String caseUpdateTopic;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;
  @Autowired private EventRepository eventRepository;
  @Autowired private CaseRepository caseRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @BeforeEach
  void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_UAC_SUBSCRIPTION, uacUpdateTopic);
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_CASE_SUBSCRIPTION, caseUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @ParameterizedTest
  @ValueSource(strings = {"present", "absent", "null"})
  void receiptsQidAndAuditsEqMessage(String txIdState) throws Exception {
    try (QueueSpy<EventDTO> uacQueue =
            pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class);
        QueueSpy<EventDTO> caseQueue =
            pubsubHelper.pubsubProjectListen(OUTBOUND_CASE_SUBSCRIPTION, EventDTO.class)) {
      Case caze = junkDataHelper.setupJunkCase();
      UacQidLink link = new UacQidLink();
      link.setId(UUID.randomUUID());
      link.setQid(QID);
      link.setUac("abc");
      link.setUacHash("fakeHash");
      link.setCaze(caze);
      link.setActive(true);
      link.setReceiptReceived(false);
      link.setSurveyLaunched(true);
      uacQidLinkRepository.saveAndFlush(link);

      UUID txId = UUID.randomUUID();
      Map<String, String> eqReceipt = new HashMap<>();
      eqReceipt.put("questionnaire_id", QID);
      eqReceipt.put("case_id", UUID.randomUUID().toString());
      if ("present".equals(txIdState)) {
        eqReceipt.put("tx_id", txId.toString());
      } else if ("null".equals(txIdState)) {
        eqReceipt.put("tx_id", null);
      }
      pubsubHelper.sendMessage(EQ_RECEIPT_TOPIC, eqReceipt);

      EventDTO uacUpdate = uacQueue.checkExpectedMessageReceived();
      EventDTO caseUpdate = caseQueue.checkExpectedMessageReceived();
      assertThat(uacUpdate).isNotNull();
      assertThat(caseUpdate).isNotNull();
      UUID correlationId = uacUpdate.getHeader().getCorrelationId();
      assertThat(correlationId.version()).isEqualTo(4);
      if ("present".equals(txIdState)) {
        assertThat(correlationId).isEqualTo(txId);
      }
      assertThat(caseUpdate.getHeader().getCorrelationId()).isEqualTo(correlationId);
      assertThat(uacUpdate.getPayload().getUacUpdate().isActive()).isFalse();
      assertThat(caseUpdate.getPayload().getCaseUpdate().isReceiptReceived()).isTrue();
      assertThat(uacQidLinkRepository.findById(link.getId()).orElseThrow().isReceiptReceived())
          .isTrue();
      assertThat(caseRepository.findById(caze.getId()).orElseThrow().isReceiptReceived()).isTrue();

      List<Event> events = eventRepository.findAll();
      assertThat(events).hasSize(1);
      Event logged = events.get(0);
      assertThat(logged.getType()).isEqualTo(EventType.RESPONSE_RECEIVED);
      assertThat(logged.getUacQidLink().getId()).isEqualTo(link.getId());
      assertThat(logged.getChannel()).isEqualTo("EQ");
      assertThat(logged.getSource()).isEqualTo("RECEIPT_SERVICE");
      assertThat(logged.getCorrelationId()).isEqualTo(correlationId);
      assertThat(logged.getMessageId().version()).isEqualTo(4);
      assertThat(logged.getDateTime()).isEqualTo(logged.getMessageTimestamp());
    }
  }
}
