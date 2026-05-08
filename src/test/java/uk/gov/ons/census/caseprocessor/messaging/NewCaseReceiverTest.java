package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.caseprocessor.testutils.MessageConstructor.constructMessage;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.NewCase;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.CollectionExerciseRepository;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.caseprocessor.service.UacService;
import uk.gov.ons.census.caseprocessor.utils.JsonHelper;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.Survey;
import uk.gov.ons.census.common.validation.ColumnValidator;
import uk.gov.ons.census.common.validation.LengthRule;
import uk.gov.ons.census.common.validation.MandatoryRule;
import uk.gov.ons.census.common.validation.Rule;

@ExtendWith(MockitoExtension.class)
class NewCaseReceiverTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();
  private final UUID TEST_CASE_COLLECTION_EXERCISE_ID = UUID.randomUUID();
  private static final byte[] caserefgeneratorkey =
      new byte[] {0x10, 0x20, 0x10, 0x20, 0x10, 0x20, 0x10, 0x20};

  @Mock private UacService uacService;
  @Mock private EventLogger eventLogger;
  @Mock private CaseService caseService;
  @Mock private CaseRepository caseRepository;
  @Mock private CollectionExerciseRepository collectionExerciseRepository;

  @InjectMocks NewCaseReceiver underTest;

  @Test
  void testNewCaseReceiver() {
    // Given
    EventDTO event = buildValidNewCaseEventMessage();
    Message<byte[]> eventMessage = constructMessage(event);

    when(caseRepository.existsById(TEST_CASE_ID)).thenReturn(false);

    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setSampleValidationRules(  // TODO delete
        new ColumnValidator[] {
          new ColumnValidator("ADDRESS_LINE1", new Rule[] {new MandatoryRule()}),
          new ColumnValidator("POSTCODE", new Rule[] {new MandatoryRule()}),
          new ColumnValidator("Telephone", new Rule[] {new MandatoryRule()})
        });
    survey.setSampleDefinitionUrl("testDefinition");  // TODO delete

    CollectionExercise collex = new CollectionExercise();
    collex.setSurvey(survey);
    Optional<CollectionExercise> collexOpt = Optional.of(collex);
    when(collectionExerciseRepository.findById(TEST_CASE_COLLECTION_EXERCISE_ID))
        .thenReturn(collexOpt);

    when(caseRepository.saveAndFlush(any(Case.class)))
        .then(
            invocation -> {
              Case caze = invocation.getArgument(0);
              caze.setSecretSequenceNumber(123);
              return caze;
            });

    ReflectionTestUtils.setField(underTest, "caserefgeneratorkey", caserefgeneratorkey);

    // When
    underTest.receiveNewCase(eventMessage);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService)
        .emitCaseUpdate(
            caseArgumentCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getId()).isEqualTo(TEST_CASE_ID);

    verify(eventLogger)
        .logCaseEvent(actualCase, "New case created", EventType.NEW_CASE, event, eventMessage);
  }

  @Test
  void testNewCaseReceiverCaseAlreadyExists() {
    // Given
    NewCase newCase = new NewCase();
    newCase.setCaseId(TEST_CASE_ID);

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setNewCase(newCase);

    EventDTO event = new EventDTO();
    event.setHeader(eventHeader);
    event.setPayload(payloadDTO);

    byte[] payloadBytes = JsonHelper.convertObjectToJson(event).getBytes();
    Message<byte[]> message = mock(Message.class);
    when(message.getPayload()).thenReturn(payloadBytes);

    when(caseRepository.existsById(TEST_CASE_ID)).thenReturn(true);

    // When
    underTest.receiveNewCase(message);

    // Then
    verify(caseService, never()).emitCaseUpdate(any(), any(UUID.class), anyString());
    verifyNoInteractions(eventLogger);
  }

  @Test
  void testNewCaseReceiverCollectionExerciseNotFound() {
    ReflectionTestUtils.setField(underTest, "caserefgeneratorkey", caserefgeneratorkey);

    // Given
    NewCase newCase = new NewCase();
    newCase.setCaseId(TEST_CASE_ID);
    newCase.setCollectionExerciseId(TEST_CASE_COLLECTION_EXERCISE_ID);

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setNewCase(newCase);

    EventDTO event = new EventDTO();
    event.setHeader(eventHeader);
    event.setPayload(payloadDTO);

    Message<byte[]> eventMessage = constructMessage(event);

    when(caseRepository.existsById(TEST_CASE_ID)).thenReturn(false);
    when(collectionExerciseRepository.findById(TEST_CASE_COLLECTION_EXERCISE_ID))
        .thenReturn(Optional.empty());

    RuntimeException thrownException =
        assertThrows(RuntimeException.class, () -> underTest.receiveNewCase(eventMessage));
    assertThat(thrownException.getMessage())
        .isEqualTo("Collection exercise '" + TEST_CASE_COLLECTION_EXERCISE_ID + "' not found");
    verifyNoInteractions(eventLogger);
  }

//  @Test
//  public void testNewCaseReceiverCaseFailsValidation() {
//    ReflectionTestUtils.setField(underTest, "caserefgeneratorkey", caserefgeneratorkey);
//
//    // Given
//    NewCase newCase = new NewCase();
//    newCase.setCaseId(TEST_CASE_ID);
//    newCase.setCollectionExerciseId(TEST_CASE_COLLECTION_EXERCISE_ID);
//
//    Map<String, String> sample = new HashMap<>();
//    sample.put("ADDRESS_LINE1", "123 Fake Street");
//    sample.put("POSTCODE", "INVALID POSTCODE");
//    newCase.setSample(sample);
//
//    Map<String, String> sampleSensitive = new HashMap<>();
//    sampleSensitive.put("Telephone", "020712345");
//    newCase.setSampleSensitive(sampleSensitive);
//
//    EventHeaderDTO eventHeader = new EventHeaderDTO();
//    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
//    eventHeader.setCorrelationId(TEST_CORRELATION_ID);
//    eventHeader.setOriginatingUser(TEST_ORIGINATING_USER);
//    PayloadDTO payloadDTO = new PayloadDTO();
//    payloadDTO.setNewCase(newCase);
//
//    EventDTO event = new EventDTO();
//    event.setHeader(eventHeader);
//    event.setPayload(payloadDTO);
//
//    Message<byte[]> eventMessage = constructMessage(event);
//
//    when(caseRepository.existsById(TEST_CASE_ID)).thenReturn(false);
//
//    Survey survey = new Survey();
//    survey.setId(UUID.randomUUID());
//    survey.setSampleValidationRules(
//        new ColumnValidator[] {
//          new ColumnValidator(
//              "ADDRESS_LINE1", false, new Rule[] {new MandatoryRule(), new LengthRule(8)}),
//          new ColumnValidator(
//              "POSTCODE", false, new Rule[] {new MandatoryRule(), new LengthRule(8)}),
//          new ColumnValidator("Telephone", true, new Rule[] {new MandatoryRule()})
//        });
//
//    CollectionExercise collex = new CollectionExercise();
//    collex.setSurvey(survey);
//    Optional<CollectionExercise> collexOpt = Optional.of(collex);
//
//    when(collectionExerciseRepository.findById(TEST_CASE_COLLECTION_EXERCISE_ID))
//        .thenReturn(collexOpt);
//
//    RuntimeException thrownException =
//        assertThrows(RuntimeException.class, () -> underTest.receiveNewCase(eventMessage));
//
//    String expectedErrorMsg =
//        "NEW_CASE event: Column 'ADDRESS_LINE1' Failed validation for Rule 'LengthRule' "
//            + "validation error: Exceeded max length of 8"
//            + System.lineSeparator()
//            + "Column 'POSTCODE' Failed validation for Rule 'LengthRule' validation error: Exceeded max length of 8";
//
//    assertThat(thrownException.getMessage()).isEqualTo(expectedErrorMsg);
//    verifyNoInteractions(eventLogger);
//  }
//
//  @Test
//  public void testNewCaseReceiverCaseFailsValidationBecauseOfUndefinedSensitiveData() {
//    ReflectionTestUtils.setField(underTest, "caserefgeneratorkey", caserefgeneratorkey);
//
//    // Given
//    NewCase newCase = new NewCase();
//    newCase.setCaseId(TEST_CASE_ID);
//    newCase.setCollectionExerciseId(TEST_CASE_COLLECTION_EXERCISE_ID);
//
//    Map<String, String> sample = new HashMap<>();
//    sample.put("ADDRESS_LINE1", "123 Fake Street");
//    sample.put("POSTCODE", "abc123");
//    newCase.setSample(sample);
//
//    Map<String, String> sampleSensitive = new HashMap<>();
//    sampleSensitive.put("EmailAddress", "foo@bar.baz");
//    newCase.setSampleSensitive(sampleSensitive);
//
//    EventHeaderDTO eventHeader = new EventHeaderDTO();
//    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
//    eventHeader.setCorrelationId(TEST_CORRELATION_ID);
//    eventHeader.setOriginatingUser(TEST_ORIGINATING_USER);
//    PayloadDTO payloadDTO = new PayloadDTO();
//    payloadDTO.setNewCase(newCase);
//
//    EventDTO event = new EventDTO();
//    event.setHeader(eventHeader);
//    event.setPayload(payloadDTO);
//
//    Message<byte[]> eventMessage = constructMessage(event);
//
//    when(caseRepository.existsById(TEST_CASE_ID)).thenReturn(false);
//
//    Survey survey = new Survey();
//    survey.setId(UUID.randomUUID());
//    survey.setSampleValidationRules(
//        new ColumnValidator[] {
//          new ColumnValidator("ADDRESS_LINE1", false, new Rule[] {new MandatoryRule()}),
//          new ColumnValidator(
//              "POSTCODE", false, new Rule[] {new MandatoryRule(), new LengthRule(8)}),
//          new ColumnValidator("Telephone", true, new Rule[] {new MandatoryRule()})
//        });
//
//    CollectionExercise collex = new CollectionExercise();
//    collex.setSurvey(survey);
//    Optional<CollectionExercise> collexOpt = Optional.of(collex);
//
//    when(collectionExerciseRepository.findById(TEST_CASE_COLLECTION_EXERCISE_ID))
//        .thenReturn(collexOpt);
//
//    RuntimeException thrownException =
//        assertThrows(RuntimeException.class, () -> underTest.receiveNewCase(eventMessage));
//    assertThat(thrownException.getMessage())
//        .isEqualTo("Attempt to send sensitive data to RM which was not part of defined sample");
//    verifyNoInteractions(eventLogger);
//  }

  private EventDTO buildValidNewCaseEventMessage() {
    NewCase newCase = new NewCase();
    newCase.setCaseId(TEST_CASE_ID);
    newCase.setCollectionExerciseId(TEST_CASE_COLLECTION_EXERCISE_ID);

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

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setCorrelationId(TEST_CORRELATION_ID);
    eventHeader.setOriginatingUser(TEST_ORIGINATING_USER);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setNewCase(newCase);

    EventDTO event = new EventDTO();
    event.setHeader(eventHeader);
    event.setPayload(payloadDTO);

    return event;
  }
}
