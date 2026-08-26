package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_UAC_METADATA;

import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UacUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.caseprocessor.utils.HashHelper;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.Survey;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
class UacServiceTest {
  @Mock UacQidLinkRepository uacQidLinkRepository;
  @Mock MessageSender messageSender;

  @InjectMocks UacService underTest;

  @Test
  void saveAndEmitUacUpdatedEvent() {
    ReflectionTestUtils.setField(underTest, "uacUpdateTopic", "Test topic");
    ReflectionTestUtils.setField(underTest, "pubsubProject", "Test project");

    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setId(UUID.randomUUID());
    collectionExercise.setSurvey(survey);

    Case caze = new Case();
    caze.setId(UUID.randomUUID());
    caze.setCollectionExercise(collectionExercise);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac("abc");
    uacQidLink.setUacHash("test hash");
    uacQidLink.setQid("01234");
    uacQidLink.setActive(true);
    uacQidLink.setCaze(caze);

    when(uacQidLinkRepository.save(uacQidLink)).then(returnsFirstArg());
    underTest.saveAndEmitUacUpdateEvent(uacQidLink, TEST_CORRELATION_ID, TEST_ORIGINATING_USER);

    verify(uacQidLinkRepository).save(uacQidLink);

    ArgumentCaptor<EventDTO> eventArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);

    verify(messageSender).sendMessage(any(), eventArgumentCaptor.capture());
    EventDTO actualEvent = eventArgumentCaptor.getValue();

    assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(actualEvent.getHeader().getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);

    UacUpdateDTO uacUpdateDto = actualEvent.getPayload().getUacUpdate();
    assertThat(uacUpdateDto.getUacHash()).isEqualTo("test hash");
    assertThat(uacUpdateDto.getQid()).isEqualTo("01234");
    assertThat(uacUpdateDto.getCaseId()).isEqualTo(caze.getId());
    assertThat(uacUpdateDto.getSurveyId()).isEqualTo(survey.getId());
    assertThat(uacUpdateDto.getCollectionExerciseId()).isEqualTo(collectionExercise.getId());
    assertThat(uacUpdateDto.getFormType()).isEqualTo("H");
  }

  @Test
  void findByQid() {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac("abc");
    uacQidLink.setQid("01234");
    uacQidLink.setActive(true);

    when(uacQidLinkRepository.findByQid(uacQidLink.getQid())).thenReturn(Optional.of(uacQidLink));

    UacQidLink actualUacQidLink = underTest.findByQid(uacQidLink.getQid());
    assertThat(actualUacQidLink).isEqualTo(uacQidLink);
  }

  @Test
  void findByQidFails() {
    String qid = "12345";
    String expectedErrorMessage = String.format("qid '%s' not found!", qid);

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> underTest.findByQid(qid));

    Assertions.assertThat(thrown.getMessage()).isEqualTo(expectedErrorMessage);
  }

  @Test
  void existsByQid() {
    String testQid = "12345";
    underTest.existsByQid(testQid);
    verify(uacQidLinkRepository).existsByQid(testQid);
  }

  @Test
  void createLinkAndEmitNewUacQid() {
    // Given
    ReflectionTestUtils.setField(underTest, "uacUpdateTopic", "Test topic");
    ReflectionTestUtils.setField(underTest, "pubsubProject", "Test project");

    String qid = "0100000001";
    String uac = "TEST_UAC";

    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setId(UUID.randomUUID());
    collectionExercise.setSurvey(survey);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    UacQidLink expectedSavedUacQidLink = new UacQidLink();
    expectedSavedUacQidLink.setUac(uac);
    expectedSavedUacQidLink.setQid(qid);
    expectedSavedUacQidLink.setCaze(testCase);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    when(uacQidLinkRepository.save(uacQidLinkCaptor.capture())).then(returnsFirstArg());

    // When
    underTest.createLinkAndEmitNewUacQid(
        testCase, uac, qid, TEST_UAC_METADATA, TEST_CORRELATION_ID, TEST_ORIGINATING_USER);

    // Then
    UacQidLink actualSavedUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualSavedUacQidLink.isActive()).isTrue();
    assertThat(actualSavedUacQidLink.getQid()).isEqualTo(qid);
    assertThat(actualSavedUacQidLink.getUac()).isEqualTo(uac);
    assertThat(actualSavedUacQidLink.getUacHash()).isEqualTo(HashHelper.hash(uac));
    assertThat(actualSavedUacQidLink.getMetadata()).isEqualTo(TEST_UAC_METADATA);
    assertThat(actualSavedUacQidLink.getCaze()).isEqualTo(testCase);

    ArgumentCaptor<EventDTO> eventArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(messageSender).sendMessage(any(), eventArgumentCaptor.capture());
    EventDTO actualEvent = eventArgumentCaptor.getValue();

    assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(actualEvent.getHeader().getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);

    UacUpdateDTO uacUpdateDto = actualEvent.getPayload().getUacUpdate();
    assertThat(uacUpdateDto.getUacHash()).isEqualTo(HashHelper.hash(uac));
    assertThat(uacUpdateDto.getQid()).isEqualTo(qid);
    assertThat(uacUpdateDto.getCaseId()).isEqualTo(testCase.getId());
    assertThat(uacUpdateDto.getFormType()).isEqualTo("H");
  }

  @Test
  void createNewUacQidWithoutCase() {
    // Given
    ReflectionTestUtils.setField(underTest, "uacUpdateTopic", "Test topic");
    ReflectionTestUtils.setField(underTest, "pubsubProject", "Test project");

    String qid = "0100000001";
    String uac = "TEST_UAC";

    UacQidLink expectedSavedUacQidLink = new UacQidLink();
    expectedSavedUacQidLink.setUac(uac);
    expectedSavedUacQidLink.setQid(qid);
    expectedSavedUacQidLink.setCaze(null);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    when(uacQidLinkRepository.save(uacQidLinkCaptor.capture())).then(returnsFirstArg());

    // When
    underTest.createLinkAndEmitNewUacQid(
        null, uac, qid, TEST_UAC_METADATA, TEST_CORRELATION_ID, TEST_ORIGINATING_USER);

    // Then
    UacQidLink actualSavedUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualSavedUacQidLink.isActive()).isTrue();
    assertThat(actualSavedUacQidLink.getQid()).isEqualTo(qid);
    assertThat(actualSavedUacQidLink.getUac()).isEqualTo(uac);
    assertThat(actualSavedUacQidLink.getUacHash()).isEqualTo(HashHelper.hash(uac));
    assertThat(actualSavedUacQidLink.getMetadata()).isEqualTo(TEST_UAC_METADATA);
    assertThat(actualSavedUacQidLink.getCaze()).isEqualTo(null);

    ArgumentCaptor<EventDTO> eventArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(messageSender).sendMessage(any(), eventArgumentCaptor.capture());
    EventDTO actualEvent = eventArgumentCaptor.getValue();

    assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(actualEvent.getHeader().getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);

    UacUpdateDTO uacUpdateDto = actualEvent.getPayload().getUacUpdate();
    assertThat(uacUpdateDto.getUacHash()).isEqualTo(HashHelper.hash(uac));
    assertThat(uacUpdateDto.getQid()).isEqualTo(qid);
    assertThat(uacUpdateDto.getCaseId()).isEqualTo(null);
  }
}
