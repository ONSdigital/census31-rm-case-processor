package uk.gov.ons.census.caseprocessor.messaging;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.FULFILMENT_REQUEST_TOPIC;
import static uk.gov.ons.census.caseprocessor.utils.Constants.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.ons.census.caseprocessor.model.dto.*;
import uk.gov.ons.census.caseprocessor.model.repository.*;
import uk.gov.ons.census.caseprocessor.service.FulfilmentRequestService;
import uk.gov.ons.census.caseprocessor.testutils.*;
import uk.gov.ons.census.caseprocessor.utils.Constants;
import uk.gov.ons.census.common.model.entity.*;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
public class FulfilmentRequestReceiverIT {

  @Autowired private CaseRepository caseRepository;
  @Autowired private SurveyRepository surveyRepository;
  @Autowired private CollectionExerciseRepository collectionExerciseRepository;
  @Autowired private SmsTemplateRepository smsTemplateRepository;
  @Autowired private FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;
  @Autowired private FulfilmentToProcessRepository fulfilmentToProcessRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  private static final Map<String, String> TEST_COLLECTION_EXERCISE_UPDATE_METADATA =
      Map.of("TEST_COLLECTION_EXERCISE_UPDATE_METADATA", "TEST");

  private static final String TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION =
      "rm-internal-sms-request-enriched_notify-service";

  @AfterEach
  public void tearDown() {
    deleteDataHelper.deleteAllData();
  }

  @BeforeEach
  @Transactional
  public void setUp() {
    deleteDataHelper.deleteAllData();
    pubsubHelper.purgeMessages(TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION, smsRequestEnrichedTopic);
  }

  @Test
  void testFulfilmentRequestForExport() throws InterruptedException {

    // Given
    Case caze = junkDataHelper.setupJunkCase();
    ExportFileTemplate exportFileTemplate =
        junkDataHelper.setUpJunkExportFileTemplate(new String[] {"__request__.name"});
    junkDataHelper.linkExportFileTemplateToSurveyFulfilment(
        exportFileTemplate, caze.getCollectionExercise().getSurvey());

    EventDTO fulfilmentRequestEvent = new EventDTO();
    fulfilmentRequestEvent.setHeader(new EventHeaderDTO());
    junkDataHelper.junkify(fulfilmentRequestEvent.getHeader());
    fulfilmentRequestEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    fulfilmentRequestEvent.getHeader().setTopic(FULFILMENT_REQUEST_TOPIC);
    fulfilmentRequestEvent.getHeader().setMessageType(EventType.FULFILMENT_REQUEST);
    fulfilmentRequestEvent.getHeader().setMessageId(UUID.randomUUID());
    fulfilmentRequestEvent.setPayload(new PayloadDTO());
    FulfilmentRequest fulfilmentRequest = new FulfilmentRequest();
    fulfilmentRequest.setCaseId(caze.getId());
    fulfilmentRequest.setFulfilmentCode(exportFileTemplate.getPackCode());
    Contact contact = new Contact();
    contact.setTitle("Mr.");
    contact.setForename("Joe");
    contact.setSurname("Bloggs");
    fulfilmentRequest.setContact(contact);
    fulfilmentRequestEvent.getPayload().setFulfilmentRequest(fulfilmentRequest);

    // When
    pubsubHelper.sendMessageToPubsubProject(FULFILMENT_REQUEST_TOPIC, fulfilmentRequestEvent);

    // Then
    List<FulfilmentToProcess> fulfilmentsToProcess = getFulfilmentsToProcess();
    assertThat(fulfilmentsToProcess).hasSize(1);
    FulfilmentToProcess fulfilmentToProcess = fulfilmentsToProcess.get(0);

    assertThat(fulfilmentToProcess.getCorrelationId())
        .isEqualTo(fulfilmentRequestEvent.getHeader().getCorrelationId());
    assertThat(fulfilmentToProcess.getCaze().getId()).isEqualTo(caze.getId());
    assertThat(fulfilmentToProcess.getExportFileTemplate()).isEqualTo(exportFileTemplate);
    assertThat(fulfilmentToProcess.getPersonalisation()).isEqualTo(contact.toMap());
    assertThat(fulfilmentToProcess.getMessageId())
        .isEqualTo(fulfilmentRequestEvent.getHeader().getMessageId());
  }

  @Test
  void testFulfilmentRequestForExportDuplicate() throws InterruptedException {

    // Given
    Logger fooLogger = (Logger) LoggerFactory.getLogger(FulfilmentRequestService.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);

    Case caze = junkDataHelper.setupJunkCase();
    ExportFileTemplate exportFileTemplate =
        junkDataHelper.setUpJunkExportFileTemplate(new String[] {"__request__.name"});
    junkDataHelper.linkExportFileTemplateToSurveyFulfilment(
        exportFileTemplate, caze.getCollectionExercise().getSurvey());

    EventDTO fulfilmentRequestEvent = new EventDTO();
    fulfilmentRequestEvent.setHeader(new EventHeaderDTO());
    junkDataHelper.junkify(fulfilmentRequestEvent.getHeader());
    fulfilmentRequestEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    fulfilmentRequestEvent.getHeader().setTopic(FULFILMENT_REQUEST_TOPIC);
    fulfilmentRequestEvent.getHeader().setMessageType(EventType.FULFILMENT_REQUEST);
    fulfilmentRequestEvent.getHeader().setMessageId(UUID.randomUUID());
    fulfilmentRequestEvent.setPayload(new PayloadDTO());
    FulfilmentRequest fulfilmentRequest = new FulfilmentRequest();
    fulfilmentRequest.setCaseId(caze.getId());
    fulfilmentRequest.setFulfilmentCode(exportFileTemplate.getPackCode());
    Contact contact = new Contact();
    fulfilmentRequest.setContact(contact);
    fulfilmentRequestEvent.getPayload().setFulfilmentRequest(fulfilmentRequest);

    // When
    pubsubHelper.sendMessageToPubsubProject(FULFILMENT_REQUEST_TOPIC, fulfilmentRequestEvent);
    pubsubHelper.sendMessageToPubsubProject(FULFILMENT_REQUEST_TOPIC, fulfilmentRequestEvent);

    // Then
    List<FulfilmentToProcess> fulfilmentsToProcess = getFulfilmentsToProcess();
    assertThat(fulfilmentsToProcess).hasSize(1);
    FulfilmentToProcess fulfilmentToProcess = fulfilmentsToProcess.get(0);

    assertThat(fulfilmentToProcess.getCorrelationId())
        .isEqualTo(fulfilmentRequestEvent.getHeader().getCorrelationId());
    assertThat(fulfilmentToProcess.getCaze().getId()).isEqualTo(caze.getId());
    assertThat(fulfilmentToProcess.getExportFileTemplate()).isEqualTo(exportFileTemplate);
    assertThat(fulfilmentToProcess.getPersonalisation()).isEqualTo(contact.toMap());
    assertThat(fulfilmentToProcess.getMessageId())
        .isEqualTo(fulfilmentRequestEvent.getHeader().getMessageId());

    // check the logging
    List<ILoggingEvent> logsList = listAppender.list;
    assertThat(logsList.size()).isEqualTo(1);
    String expectedLogMessage =
        String.format(
            "Received duplicate fulfilment message ID, ignoring and acking the duplicate message");
    assertThat(logsList.get(0).getMessage()).isEqualTo(expectedLogMessage);
  }

  @Test
  void testFulfilmentRequestForSms() throws InterruptedException {
    // Given
    // Set up all the data required
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setName("TEST SURVEY");
    survey.setSampleSeparator(',');
    survey = surveyRepository.saveAndFlush(survey);

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setId(UUID.randomUUID());
    collectionExercise.setSurvey(survey);
    collectionExercise.setName("TEST COLLEX");
    collectionExercise.setReference("MVP012021");
    collectionExercise.setStartDate(OffsetDateTime.now());
    collectionExercise.setEndDate(OffsetDateTime.now().plusDays(2));
    collectionExercise.setMetadata(TEST_COLLECTION_EXERCISE_UPDATE_METADATA);
    collectionExercise = collectionExerciseRepository.saveAndFlush(collectionExercise);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    testCase = caseRepository.saveAndFlush(testCase);

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_SMS_PACK_CODE");
    smsTemplate.setTemplate(
        new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY, REQUEST_PERSONALISATION_PREFIX + "name"});
    smsTemplate.setNotifyTemplateId(UUID.randomUUID());
    smsTemplate.setDescription("Test description");
    smsTemplate.setNotifyServiceRef("test-service");
    smsTemplate.setQuestionnaireType(99);
    smsTemplateRepository.saveAndFlush(smsTemplate);

    EventDTO fulfilmentRequestEvent = new EventDTO();
    fulfilmentRequestEvent.setHeader(new EventHeaderDTO());
    junkDataHelper.junkify(fulfilmentRequestEvent.getHeader());
    fulfilmentRequestEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    fulfilmentRequestEvent.getHeader().setTopic(FULFILMENT_REQUEST_TOPIC);
    fulfilmentRequestEvent.getHeader().setMessageId(UUID.randomUUID());
    fulfilmentRequestEvent.getHeader().setMessageType(EventType.FULFILMENT_REQUEST);
    fulfilmentRequestEvent.setPayload(new PayloadDTO());
    FulfilmentRequest fulfilmentRequest = new FulfilmentRequest();
    fulfilmentRequest.setCaseId(testCase.getId());
    fulfilmentRequest.setFulfilmentCode("TEST_SMS_PACK_CODE");
    Contact contact = new Contact();
    contact.setTelNo("07788660011");
    fulfilmentRequest.setContact(contact);
    fulfilmentRequestEvent.getPayload().setFulfilmentRequest(fulfilmentRequest);

    // When
    pubsubHelper.sendMessageToPubsubProject(FULFILMENT_REQUEST_TOPIC, fulfilmentRequestEvent);
    sleep(3000);

    // Then
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    assertThat(uacQidLinks.get(0).getCaze().getId()).isEqualTo(testCase.getId());
  }

  @Test
  void testSmsRequestEnrichedReceiver() throws InterruptedException, JacksonException {
    // Given
    // Set up all the data required
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setName("TEST SURVEY");
    survey.setSampleSeparator(',');
    survey = surveyRepository.saveAndFlush(survey);

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setId(UUID.randomUUID());
    collectionExercise.setSurvey(survey);
    collectionExercise.setName("TEST COLLEX");
    collectionExercise.setReference("MVP012021");
    collectionExercise.setStartDate(OffsetDateTime.now());
    collectionExercise.setEndDate(OffsetDateTime.now().plusDays(2));
    collectionExercise.setMetadata(TEST_COLLECTION_EXERCISE_UPDATE_METADATA);
    collectionExercise = collectionExerciseRepository.saveAndFlush(collectionExercise);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    testCase = caseRepository.saveAndFlush(testCase);

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(
        new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY, REQUEST_PERSONALISATION_PREFIX + "name"});
    smsTemplate.setNotifyTemplateId(UUID.randomUUID());
    smsTemplate.setDescription("Test description");
    smsTemplate.setNotifyServiceRef("test-service");
    smsTemplate.setQuestionnaireType(99);
    smsTemplateRepository.saveAndFlush(smsTemplate);

    Contact contact = new Contact();
    contact.setTelNo("+447788990011");

    EventDTO smsRequestEnrichedEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequestEnriched smsRequestEnriched = new SmsRequestEnriched();
    smsRequestEnriched.setCaseId(testCase.getId());
    smsRequestEnriched.setPackCode("TEST_PACK_CODE");
    smsRequestEnriched.setUac("TEST_UAC");
    smsRequestEnriched.setQid("TEST_QID");
    smsRequestEnriched.setPersonalisation(contact.toMap());
    smsRequestEnriched.setPhoneNumber(contact.getTelNo());
    smsRequestEnrichedEvent.getPayload().setSmsRequestEnriched(smsRequestEnriched);

    try (QueueSpy<EventDTO> outboundUacQueueSpy =
        pubsubHelper.pubsubProjectListen(TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION, EventDTO.class)) {
      pubsubHelper.sendMessage(smsRequestEnrichedTopic, smsRequestEnrichedEvent);
      EventDTO emittedEvent = outboundUacQueueSpy.checkExpectedMessageReceived();
      // Then

      assertThat(emittedEvent.getHeader().getTopic()).isEqualTo(smsRequestEnrichedTopic);
      SmsRequestEnriched smsRequestEnrichedReceived =
          emittedEvent.getPayload().getSmsRequestEnriched();
      assertThat(smsRequestEnrichedReceived.getCaseId()).isEqualTo(testCase.getId());
      assertThat(smsRequestEnrichedReceived.getPackCode()).isEqualTo("TEST_PACK_CODE");
      assertThat(smsRequestEnrichedReceived.getQid()).isEqualTo("TEST_QID");
      assertThat(smsRequestEnrichedReceived.getUac()).isEqualTo("TEST_UAC");
    }
  }

  private List<FulfilmentToProcess> getFulfilmentsToProcess() throws InterruptedException {
    List<FulfilmentToProcess> fulfilmentsToProcess;
    for (int i = 0; i < 10; i++) {
      fulfilmentsToProcess = fulfilmentToProcessRepository.findAll();
      if (fulfilmentsToProcess.size() > 0) {
        return fulfilmentsToProcess;
      } else {
        sleep(1000);
      }
    }
    return List.of();
  }

  public static EventDTO buildEventDTO(String topic) {
    EventDTO eventDTO = new EventDTO();
    EventHeaderDTO eventHeaderDTO = new EventHeaderDTO();
    PayloadDTO payloadDTO = new PayloadDTO();
    eventHeaderDTO.setTopic(topic);
    eventHeaderDTO.setDateTime(OffsetDateTime.now());
    eventHeaderDTO.setMessageId(UUID.randomUUID());
    eventHeaderDTO.setCorrelationId(UUID.randomUUID());
    eventHeaderDTO.setOriginatingUser("test@example.test");
    eventHeaderDTO.setSource("TEST_SOURCE");
    eventHeaderDTO.setChannel("TEST_CHANNEL");
    eventHeaderDTO.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);

    eventDTO.setPayload(payloadDTO);
    eventDTO.setHeader(eventHeaderDTO);
    return eventDTO;
  }
}
