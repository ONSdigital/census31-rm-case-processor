package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.caseprocessor.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.census.caseprocessor.utils.Constants.TEMPLATE_UAC_KEY;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.ons.census.caseprocessor.client.UacQidServiceClient;
import uk.gov.ons.census.caseprocessor.model.dto.*;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentToProcessRepository;
import uk.gov.ons.census.caseprocessor.model.repository.SmsTemplateRepository;
import uk.gov.ons.census.caseprocessor.utils.Constants;
import uk.gov.ons.census.caseprocessor.utils.PubSubHelper;
import uk.gov.ons.census.common.model.entity.*;

@ExtendWith(MockitoExtension.class)
class FulfilmentRequestServiceTest {

  private final String TEST_PACK_CODE = "TEST_PACK_CODE";
  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";
  private final String TEST_SOURCE = "TEST_SOURCE";
  private final String TEST_CHANNEL = "TEST_CHANNEL";
  private final String TEST_USER = "test@example.test";

  @Mock private UacQidServiceClient uacQidServiceClient;
  @Mock private CaseRepository caseRepository;
  @Mock private SmsTemplateRepository smsTemplateRepository;
  @Mock private ExportFileTemplateRepository exportFileTemplateRepository;
  @Mock private UacService uacService;
  @Mock private CaseService caseService;
  @Mock private FulfilmentToProcessRepository fulfilmentToProcessRepository;
  @Mock private PubSubHelper pubSubHelper;

  @Value("${queueconfig.fulfilment-request-topic}")
  private String fulfilmentRequestTopic;

  @InjectMocks private FulfilmentRequestService fulfilmentRequestService;

  @Test
  void testFetchNewUacQidPairIfRequiredEmptyTemplate() {
    // When
    Optional<UacQidDTO> actualUacQidCreated =
        fulfilmentRequestService.fetchNewUacQidPairIfRequired(1, new String[] {});

    // Then
    assertThat(actualUacQidCreated).isEmpty();
    verifyNoInteractions(uacQidServiceClient);
  }

  @Test
  void testFetchNewUacQidPairIfRequiredUacAndQid() {
    // Given
    UacQidDTO newUacQidCreated = new UacQidDTO();
    newUacQidCreated.setUac("TEST_UAC");
    newUacQidCreated.setUac("TEST_QID");
    when(uacQidServiceClient.generateUacQid(1)).thenReturn(newUacQidCreated);

    // When
    Optional<UacQidDTO> actualUacQidCreated =
        fulfilmentRequestService.fetchNewUacQidPairIfRequired(
            1, new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});

    // Then
    assertThat(actualUacQidCreated).contains(newUacQidCreated);
  }

  @Test
  void testFetchNewUacQidPairIfRequiredUacAndQidFailureNoQuestionnaireType() {
    // Given

    // When
    Exception thrownException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                fulfilmentRequestService.fetchNewUacQidPairIfRequired(
                    null, new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY}));

    // Then
    assertThat(thrownException.getMessage())
        .isEqualTo("SMS template is missing questionnaire type");
  }

  @Test
  void testProcessSMSFulfilmentReceiptService_success() {
    // --- Arrange ---
    UUID caseId = UUID.randomUUID();
    EventDTO smsRequestEnrichedEvent = setupSmsRequestEnrichedEvent(caseId);

    // Case returned
    Case caze = new Case();
    caze.setId(caseId);

    when(caseService.getCase(caseId)).thenReturn(caze);

    // QID does NOT exist
    when(uacService.existsByQid("QID123")).thenReturn(false);

    // --- Act ---
    Case result =
        fulfilmentRequestService.processSMSFulfilmentReceiptService(smsRequestEnrichedEvent);

    // --- Assert ---
    assertEquals(caze, result);

    verify(uacService)
        .createLinkAndEmitNewUacQid(
            caze,
            "UAC123",
            "QID123",
            null,
            smsRequestEnrichedEvent.getHeader().getCorrelationId(),
            smsRequestEnrichedEvent.getHeader().getOriginatingUser());
  }

  @Test
  void testProcessSMSFulfilmentReceiptService_duplicateEvent_returnsNull() {
    // --- Arrange ---
    UUID caseId = UUID.randomUUID();
    EventDTO smsRequestEnrichedEvent = setupSmsRequestEnrichedEvent(caseId);

    // Case returned
    Case caze = new Case();
    caze.setId(caseId);

    when(caseService.getCase(caseId)).thenReturn(caze);

    // QID exists
    when(uacService.existsByQid("QID123")).thenReturn(true);

    // QID linked to SAME case → duplicate
    UacQidLink link = new UacQidLink();
    link.setCaze(caze);

    when(uacService.findByQid("QID123")).thenReturn(link);

    // --- Act ---
    Case result =
        fulfilmentRequestService.processSMSFulfilmentReceiptService(smsRequestEnrichedEvent);

    // --- Assert ---
    assertNull(result);

    verify(uacService, never())
        .createLinkAndEmitNewUacQid(any(), any(), any(), any(), any(), any());

    when(caseService.getCase(caseId)).thenReturn(caze);

    // QID exists
    when(uacService.existsByQid("QID123")).thenReturn(true);

    // QID linked to SAME case → duplicate
    UacQidLink link2 = new UacQidLink();
    link2.setCaze(caze);

    when(uacService.findByQid("QID123")).thenReturn(link2);

    // --- Act ---
    Case result2 =
        fulfilmentRequestService.processSMSFulfilmentReceiptService(smsRequestEnrichedEvent);

    // --- Assert ---
    assertNull(result2);

    verify(uacService, never())
        .createLinkAndEmitNewUacQid(any(), any(), any(), any(), any(), any());
  }

  @Test
  void testProcessSMSFulfilmentReceiptService_qidLinkedToDifferentCase_throwsException() {

    // --- Arrange ---
    UUID caseId = UUID.randomUUID();
    EventDTO smsRequestEnrichedEvent = setupSmsRequestEnrichedEvent(caseId);

    // Case returned
    Case caze = new Case();
    caze.setId(caseId);

    // Case returned
    Case caze2 = new Case();
    caze2.setId(UUID.randomUUID());

    when(caseService.getCase(caseId)).thenReturn(caze);

    // QID exists
    when(uacService.existsByQid("QID123")).thenReturn(true);

    // QID linked to DIFFERENT case → duplicate
    UacQidLink link = new UacQidLink();
    link.setCaze(caze2);

    when(uacService.findByQid(
            smsRequestEnrichedEvent.getPayload().getSmsRequestEnriched().getQid()))
        .thenReturn(link);

    // --- Act + Assert ---
    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () ->
                fulfilmentRequestService.processSMSFulfilmentReceiptService(
                    smsRequestEnrichedEvent));

    assertTrue(ex.getMessage().contains("is already linked to a different case"));

    verify(uacService, never())
        .createLinkAndEmitNewUacQid(any(), any(), any(), any(), any(), any());
  }

  // QID is null -> not linking, simply return case
  @Test
  void testProcessSMSFulfilmentReceiptService_qidNull_returnsCaseWithoutLinking() {

    // --- Arrange ---
    UUID caseId = UUID.randomUUID();
    EventDTO smsRequestEnrichedEvent = setupSmsRequestEnrichedEvent(caseId);

    smsRequestEnrichedEvent.getPayload().getSmsRequestEnriched().setQid(null);
    smsRequestEnrichedEvent.getPayload().getSmsRequestEnriched().setUac(null);

    // Case returned
    Case caze = new Case();
    caze.setId(caseId);

    when(caseService.getCase(caseId)).thenReturn(caze);

    // --- Act ---
    Case result =
        fulfilmentRequestService.processSMSFulfilmentReceiptService(smsRequestEnrichedEvent);

    // --- Assert ---
    assertEquals(caze, result);

    verify(uacService, never()).existsByQid(any());

    verify(uacService, never())
        .createLinkAndEmitNewUacQid(any(), any(), any(), any(), any(), any());
  }

  // Case not found → throw exception
  @Test
  void testProcessSMSFulfilmentReceiptService_caseNotFound_throwsException() {

    // --- Arrange ---
    UUID caseId = UUID.randomUUID();

    EventDTO smsRequestEnrichedEvent = setupSmsRequestEnrichedEvent(caseId);

    when(caseService.getCase(caseId)).thenThrow(new RuntimeException("Case not found"));

    // --- Act + Assert ---
    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () ->
                fulfilmentRequestService.processSMSFulfilmentReceiptService(
                    smsRequestEnrichedEvent));

    assertTrue(ex.getMessage().contains("Case not found"));

    verify(uacService, never())
        .createLinkAndEmitNewUacQid(any(), any(), any(), any(), any(), any());
  }

  @Test
  void testProcessSMSRequestReceiver_success() {
    String topic = "sms-request-enriched-topic";
    UUID caseId = UUID.randomUUID();
    SmsTemplate smsTemplate = setupTemplate();
    EventDTO event = setupFulfilmentRequestEvent(caseId);
    when(smsTemplateRepository.findById("PACK1")).thenReturn(Optional.of(smsTemplate));

    // Case exists
    when(caseRepository.existsById(caseId)).thenReturn(true);

    // Mock UAC/QID pair
    UacQidDTO uacQid = new UacQidDTO();
    uacQid.setUac("UAC123");
    uacQid.setQid("QID123");

    when(uacQidServiceClient.generateUacQid(10)).thenReturn(uacQid);

    // --- Act ---
    EventDTO enrichedEvent = fulfilmentRequestService.processSMSRequestReceiver(event, topic);

    // --- Assert ---
    verify(pubSubHelper).publishAndConfirm(topic, enrichedEvent);

    SmsRequestEnriched enriched = enrichedEvent.getPayload().getSmsRequestEnriched();

    assertEquals(caseId, enriched.getCaseId());
    assertEquals("PACK1", enriched.getPackCode());
    assertEquals("UAC123", enriched.getUac());
    assertEquals("QID123", enriched.getQid());

    EventHeaderDTO enrichedHeader = enrichedEvent.getHeader();
    assertEquals(event.getHeader().getCorrelationId(), enrichedHeader.getCorrelationId());
    assertEquals(topic, enrichedHeader.getTopic());
    assertEquals(EventType.FULFILMENT_SMS_CONFIRMATION, enrichedHeader.getMessageType());
  }

  @Test
  void testProcessSMSRequestReceiver_templateNotFound_throws() {
    String topic = "sms-request-enriched-topic";
    UUID caseId = UUID.randomUUID();
    EventDTO event = setupFulfilmentRequestEvent(caseId);
    // SmsTemplate smsTemplate = setupTemplate();

    when(smsTemplateRepository.findById("PACK1")).thenReturn(Optional.empty());

    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> fulfilmentRequestService.processSMSRequestReceiver(event, topic));

    assertTrue(ex.getMessage().contains("SMS Template not found"));
  }

  @Test
  void testProcessSMSRequestReceiver_caseNotFound_throws() {
    String topic = "sms-request-enriched-topic";
    UUID caseId = UUID.randomUUID();
    EventDTO event = setupFulfilmentRequestEvent(caseId);
    SmsTemplate template = setupTemplate();

    when(smsTemplateRepository.findById("PACK1")).thenReturn(Optional.of(template));

    when(caseRepository.existsById(caseId)).thenReturn(false);

    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> fulfilmentRequestService.processSMSRequestReceiver(event, topic));

    assertTrue(ex.getMessage().contains("Case not found"));
  }

  @Test
  void testProcessSMSRequestReceiver_uacQidFetchFails_throwsWrappedException() {
    UUID caseId = UUID.randomUUID();
    String topic = "sms-request-enriched-topic";
    EventDTO event = setupFulfilmentRequestEvent(caseId);
    SmsTemplate template = setupTemplate();

    when(smsTemplateRepository.findById("PACK1")).thenReturn(Optional.of(template));

    when(caseRepository.existsById(caseId)).thenReturn(true);
    template.setQuestionnaireType(null);
    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> fulfilmentRequestService.processSMSRequestReceiver(event, topic));

    assertTrue(ex.getMessage().contains("Failed to fetch UAC/QID pair"));
  }

  @Test
  void testProcessSMSRequestReceiver_noUacQidRequired() {
    UUID caseId = UUID.randomUUID();
    String topic = "sms-request-enriched-topic";
    EventDTO event = setupFulfilmentRequestEvent(caseId);
    SmsTemplate template = setupTemplate();
    String[] modifiedValues =
        new String[] {
          "__pack_code__",
          "__caseref__",
          "ADDRESS_LINE1",
          "ADDRESS_LINE2",
          "ADDRESS_LINE3",
          "TOWN_NAME",
          "POSTCODE"
        };

    template.setTemplate(modifiedValues);

    when(smsTemplateRepository.findById("PACK1")).thenReturn(Optional.of(template));

    when(caseRepository.existsById(caseId)).thenReturn(true);

    EventDTO enriched = fulfilmentRequestService.processSMSRequestReceiver(event, topic);

    verify(pubSubHelper).publishAndConfirm(topic, enriched);

    SmsRequestEnriched enrichedPayload = enriched.getPayload().getSmsRequestEnriched();
    assertEquals(caseId, enrichedPayload.getCaseId());
    assertEquals("PACK1", enrichedPayload.getPackCode());
    assertNull(enrichedPayload.getUac());
    assertNull(enrichedPayload.getQid());
  }

  @Test
  void testProcessPrintFulfilmentReceiver_success() {
    // --- Arrange ---
    UUID caseId = UUID.randomUUID();

    EventDTO event = setupPrintFulfilmentRequestEvent(caseId);

    // Duplicate check → false
    when(fulfilmentToProcessRepository.existsByMessageId(event.getHeader().getMessageId()))
        .thenReturn(false);

    // Build Case → CollectionExercise → Survey → FulfilmentSurveyExportFileTemplate
    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setPackCode("P_CODE");

    FulfilmentSurveyExportFileTemplate fset = new FulfilmentSurveyExportFileTemplate();
    fset.setExportFileTemplate(exportFileTemplate);

    Survey survey = new Survey();
    survey.setName("Census");
    survey.setFulfilmentExportFileTemplates(List.of(fset));

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setSurvey(survey);

    Case caze = new Case();
    caze.setId(caseId);
    caze.setCollectionExercise(collectionExercise);

    when(caseService.getCase(caseId)).thenReturn(caze);

    // --- Act ---
    Case result = fulfilmentRequestService.processPrintFulfilmentReceiver(event);

    // --- Assert ---
    assertEquals(caze, result);

    // Capture saved entity
    ArgumentCaptor<FulfilmentToProcess> captor = ArgumentCaptor.forClass(FulfilmentToProcess.class);

    verify(fulfilmentToProcessRepository).saveAndFlush(captor.capture());

    FulfilmentToProcess saved = captor.getValue();

    assertEquals(exportFileTemplate, saved.getExportFileTemplate());
    assertEquals(caze, saved.getCaze());
    assertEquals(event.getHeader().getCorrelationId(), saved.getCorrelationId());
    assertEquals(event.getHeader().getMessageId(), saved.getMessageId());
    assertEquals("test-user", saved.getOriginatingUser());

    // Personalisation map from Contact.toMap()
    Map<String, String> personalisation = saved.getPersonalisation();
    assertEquals("Mr", personalisation.get("title"));
    assertEquals("John", personalisation.get("forename"));
    assertEquals("Doe", personalisation.get("surname"));
  }

  @Test
  void testProcessPrintFulfilmentReceiver_duplicateMessage_returnsNull() {
    // --- Arrange ---
    UUID caseId = UUID.randomUUID();
    EventDTO event = setupPrintFulfilmentRequestEvent(caseId);

    when(fulfilmentToProcessRepository.existsByMessageId(event.getHeader().getMessageId()))
        .thenReturn(true);

    Case result = fulfilmentRequestService.processPrintFulfilmentReceiver(event);

    assertNull(result);

    verify(fulfilmentToProcessRepository, never()).saveAndFlush(any());
  }

  @Test
  void testProcessPrintFulfilmentReceiver_templateNotAllowed_throws() {
    // --- Arrange ---
    UUID caseId = UUID.randomUUID();
    EventDTO event = setupPrintFulfilmentRequestEvent(caseId);

    when(fulfilmentToProcessRepository.existsByMessageId(event.getHeader().getMessageId()))
        .thenReturn(false);

    // Case has template PACK1, but request is P_CODE
    Case caze = setupCase(caseId, "PACK1");

    when(caseService.getCase(caseId)).thenReturn(caze);

    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> fulfilmentRequestService.processPrintFulfilmentReceiver(event));

    assertTrue(ex.getMessage().contains("Pack code P_CODE is not allowed"));
  }

  @Test
  void testProcessPrintFulfilmentReceiver_caseNotFound_throws() {
    // --- Arrange ---
    UUID caseId = UUID.randomUUID();
    EventDTO event = setupPrintFulfilmentRequestEvent(caseId);

    when(fulfilmentToProcessRepository.existsByMessageId(event.getHeader().getMessageId()))
        .thenReturn(false);

    when(caseService.getCase(caseId)).thenThrow(new RuntimeException("Case not found"));

    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> fulfilmentRequestService.processPrintFulfilmentReceiver(event));

    assertTrue(ex.getMessage().contains("Case not found"));
  }

  private EventDTO setupSmsRequestEnrichedEvent(UUID caseId) {
    // --- Arrange ---
    String topic = "sms-request-enriched-topic";
    UUID correlationId = UUID.randomUUID();
    String originatingUser = "test-user";

    // Header
    EventHeaderDTO header = new EventHeaderDTO();
    header.setMessageId(UUID.randomUUID());
    header.setCorrelationId(correlationId);
    header.setChannel("RM");
    header.setSource("RM");
    header.setOriginatingUser("test-user");

    // Contact → toMap() will be used
    Contact contact = new Contact();
    contact.setTitle("Mr");
    contact.setForename("John");
    contact.setSurname("Doe");
    contact.setTelNo("+447788991100");

    // Mock UAC/QID pair
    UacQidDTO uacQid = new UacQidDTO();
    uacQid.setUac("UAC123");
    uacQid.setQid("QID123");

    SmsRequestEnriched smsRequestEnriched = new SmsRequestEnriched();
    smsRequestEnriched.setCaseId(caseId);
    smsRequestEnriched.setPhoneNumber("+447788991100");
    smsRequestEnriched.setPackCode("P_CODE");
    smsRequestEnriched.setContact(contact);
    smsRequestEnriched.setScheduled(false);
    smsRequestEnriched.setPersonalisation(contact.toMap());
    smsRequestEnriched.setUac(uacQid.getUac());
    smsRequestEnriched.setQid(uacQid.getQid());

    EventHeaderDTO enrichedEventHeader = new EventHeaderDTO();
    enrichedEventHeader.setMessageId(UUID.randomUUID());
    enrichedEventHeader.setCorrelationId(header.getCorrelationId());
    enrichedEventHeader.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    enrichedEventHeader.setChannel(header.getChannel());
    enrichedEventHeader.setSource(header.getSource());
    enrichedEventHeader.setOriginatingUser(header.getOriginatingUser());
    enrichedEventHeader.setTopic(topic);
    enrichedEventHeader.setDateTime(OffsetDateTime.now());
    enrichedEventHeader.setMessageType(EventType.FULFILMENT_SMS_CONFIRMATION);

    PayloadDTO enrichedPayload = new PayloadDTO();
    enrichedPayload.setSmsRequestEnriched(smsRequestEnriched);

    EventDTO smsRequestEnrichedEvent = new EventDTO();
    smsRequestEnrichedEvent.setHeader(enrichedEventHeader);
    smsRequestEnrichedEvent.setPayload(enrichedPayload);

    return smsRequestEnrichedEvent;
  }

  private EventDTO setupFulfilmentRequestEvent(UUID caseId) {

    // Header
    EventHeaderDTO header = new EventHeaderDTO();
    header.setMessageId(UUID.randomUUID());
    header.setCorrelationId(UUID.randomUUID());
    header.setChannel("RM");
    header.setSource("RM");
    header.setOriginatingUser("test-user");

    // Fulfilment Request
    FulfilmentRequest fulfilmentRequest = new FulfilmentRequest();
    fulfilmentRequest.setCaseId(caseId);
    fulfilmentRequest.setFulfilmentCode("PACK1");
    fulfilmentRequest.setContact(new Contact()); // minimal

    PayloadDTO payload = new PayloadDTO();
    payload.setFulfilmentRequest(fulfilmentRequest);

    EventDTO event = new EventDTO();
    event.setHeader(header);
    event.setPayload(payload);

    return event;
  }

  private SmsTemplate setupTemplate() {
    String[] template =
        new String[] {
          "__pack_code__",
          "__uac__",
          "__qid__",
          "__caseref__",
          "ADDRESS_LINE1",
          "ADDRESS_LINE2",
          "ADDRESS_LINE3",
          "TOWN_NAME",
          "POSTCODE"
        };

    // Mock SMS Template
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("PACK1");
    smsTemplate.setQuestionnaireType(10);
    smsTemplate.setTemplate(template);

    return smsTemplate;
  }

  private EventDTO setupPrintFulfilmentRequestEvent(UUID caseId) {
    // --- Arrange ---
    UUID messageId = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();

    // Header
    EventHeaderDTO header = new EventHeaderDTO();
    header.setMessageId(messageId);
    header.setCorrelationId(correlationId);
    header.setOriginatingUser("test-user");

    // Contact → toMap() will be used
    Contact contact = new Contact();
    contact.setTitle("Mr");
    contact.setForename("John");
    contact.setSurname("Doe");

    // Fulfilment Request
    FulfilmentRequest fulfilmentRequest = new FulfilmentRequest();
    fulfilmentRequest.setCaseId(caseId);
    fulfilmentRequest.setFulfilmentCode("P_CODE");
    fulfilmentRequest.setContact(contact);

    PayloadDTO payload = new PayloadDTO();
    payload.setFulfilmentRequest(fulfilmentRequest);

    EventDTO event = new EventDTO();
    event.setHeader(header);
    event.setPayload(payload);

    return event;
  }

  private Case setupCase(UUID caseId, String packCode) {

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode(packCode);

    FulfilmentSurveyExportFileTemplate fset = new FulfilmentSurveyExportFileTemplate();
    fset.setExportFileTemplate(eft);

    Survey survey = new Survey();
    survey.setName("Census");
    survey.setFulfilmentExportFileTemplates(List.of(fset));

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setSurvey(survey);

    Case caze = new Case();
    caze.setId(caseId);
    caze.setCollectionExercise(collectionExercise);

    return caze;
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "07123456789",
        "07876543456",
        "+447123456789",
        "00447123456789",
        "447123456789",
        "7123456789",
      })
  void testValidatePhoneNumberValid(String phoneNumber) {
    assertTrue(fulfilmentRequestService.validatePhoneNumber(phoneNumber));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1",
        "foo",
        "007",
        "071234567890",
        "0447123456789",
        "000447123456789",
        "+44 7123456789",
        "44+7123456789",
        "0712345678a",
        "@7123456789",
        "07123 456789",
        "(+44) 07123456789"
      })
  void testValidatePhoneNumberInvalid(String phoneNumber) {
    assertFalse(fulfilmentRequestService.validatePhoneNumber(phoneNumber));
  }
}
