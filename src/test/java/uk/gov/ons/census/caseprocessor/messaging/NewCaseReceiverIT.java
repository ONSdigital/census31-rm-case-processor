package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.NEW_CASE_TOPIC;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_CASE_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.util.List;
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
import uk.gov.ons.census.caseprocessor.testutils.*;
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

      PayloadDTO payloadDTO = new PayloadDTO();
      NewCase newCase = new NewCase();
      newCase.setCaseId(TEST_CASE_ID);
      newCase.setCollectionExerciseId(collectionExercise.getId());
      newCase.setTreatmentCode("HH_QP3E");
      newCase.setAddressType("H");
      newCase.setUprn("1234567890");
      newCase.setEstabUprn("1234567890");
      newCase.setEstabType("HOUSEHOLD");
      newCase.setAddressLine1("123 Fake Street");
      newCase.setTownName("Testington");
      newCase.setRegion("E");
      newCase.setPostcode("NP10 111");
      newCase.setAddressType("HH");
      newCase.setAddressLevel("U");
      newCase.setAbpCode("ABC123");
      newCase.setFieldCoordinatorId("ABCD1234");
      newCase.setFieldOfficerId("ABCD1234");
      newCase.setOa("A12345678");
      newCase.setLsoa("A12345678");
      newCase.setMsoa("A12345678");
      newCase.setLad("ABC123");
      newCase.setHtcDigital("1");
      newCase.setHtcWillingness("1");
      newCase.setLatitude("51.5074");
      newCase.setLongitude("0.1278");
      newCase.setPrintBatch("1");
      newCase.setSecureEstablishment(false);

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
      Assertions.assertThat(emittedCase.getAddress().getAddressLine1())
          .isEqualTo(newCase.getAddressLine1());
      Assertions.assertThat(emittedCase.getAddress().getAddressType())
          .isEqualTo(newCase.getAddressType());
      Assertions.assertThat(emittedCase.getAddress().getPostcode())
          .isEqualTo(newCase.getPostcode());
      Assertions.assertThat(emittedCase.getAddress().getRegion()).isEqualTo(newCase.getRegion());

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();

      assertThat(actualCase.getId()).isEqualTo(TEST_CASE_ID);
      assertThat(actualCase.getCollectionExercise().getId()).isEqualTo(collectionExercise.getId());
      assertThat(actualCase.getUprn()).isEqualTo(newCase.getUprn());
      assertThat(actualCase.getEstabUprn()).isEqualTo(newCase.getEstabUprn());
      assertThat(actualCase.getAddressType()).isEqualTo(newCase.getAddressType());
      assertThat(actualCase.getEstabType()).isEqualTo(newCase.getEstabType());
      assertThat(actualCase.getAddressLine1()).isEqualTo(newCase.getAddressLine1());
      assertThat(actualCase.getTownName()).isEqualTo(newCase.getTownName());
      assertThat(actualCase.getRegion()).isEqualTo(newCase.getRegion());
      assertThat(actualCase.isSecureEstablishment()).isEqualTo(newCase.isSecureEstablishment());

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      assertThat(events.get(0).getType()).isEqualTo(EventType.NEW_CASE);
    }
  }
}
