package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.NEW_CASE_TOPIC;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_CASE_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import uk.gov.ons.census.caseprocessor.model.dto.NewCase;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.EventRepository;
import uk.gov.ons.census.caseprocessor.testutils.DeleteDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.JunkDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.PubsubHelper;
import uk.gov.ons.census.caseprocessor.testutils.QueueSpy;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.Event;
import uk.gov.ons.census.common.model.entity.EventType;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
public class NewCaseReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Value("${queueconfig.case-update-topic}")
  private String caseUpdateTopic;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private EventRepository eventRepository;
  @Autowired private CaseRepository caseRepository;

  @BeforeEach
  public void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_CASE_SUBSCRIPTION, caseUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  public void testNewCaseLoaded() throws InterruptedException {
    try (QueueSpy<EventDTO> outboundCaseQueueSpy =
        pubsubHelper.pubsubProjectListen(OUTBOUND_CASE_SUBSCRIPTION, EventDTO.class)) {

      // GIVEN
      EventDTO event = new EventDTO();
      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(NEW_CASE_TOPIC);
      junkDataHelper.junkify(eventHeader);
      event.setHeader(eventHeader);

      CollectionExercise collectionExercise = junkDataHelper.setupJunkCollex();

      Map<String, String> sample = new HashMap<>();
      sample.put("Junk", "YesYouCan");

      Map<String, String> sampleSensitive = new HashMap<>();
      sampleSensitive.put("SensitiveJunk", "02071234567");

      PayloadDTO payloadDTO = new PayloadDTO();
      NewCase newCase = new NewCase();
      newCase.setCaseId(TEST_CASE_ID);
      newCase.setCollectionExerciseId(collectionExercise.getId());
      // TODO set sample fields
      payloadDTO.setNewCase(newCase);
      event.setPayload(payloadDTO);

      pubsubHelper.sendMessageToPubsubProject(NEW_CASE_TOPIC, event);

      //  THEN
      EventDTO actualEvent = outboundCaseQueueSpy.checkExpectedMessageReceived();

      CaseUpdateDTO emittedCase = actualEvent.getPayload().getCaseUpdate();
      Assertions.assertThat(emittedCase.getCaseId()).isEqualTo(TEST_CASE_ID);
      Assertions.assertThat(emittedCase.getCollectionExerciseId())
          .isEqualTo(collectionExercise.getId());
      Assertions.assertThat(emittedCase.getSurveyId())
          .isEqualTo(collectionExercise.getSurvey().getId());

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();

      assertThat(actualCase.getId()).isEqualTo(TEST_CASE_ID);
      assertThat(actualCase.getCollectionExercise().getId()).isEqualTo(collectionExercise.getId());

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      assertThat(events.get(0).getType()).isEqualTo(EventType.NEW_CASE);
      assertThat(events.get(0).getPayload()).contains("{\"SensitiveJunk\": \"REDACTED\"}");
    }
  }
}
