package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.CaseUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.RefusalTypeDTO;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.RefusalType;
import uk.gov.ons.census.common.model.entity.Survey;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {
  @Mock CaseRepository caseRepository;
  @Mock MessageSender messageSender;

  @InjectMocks CaseService underTest;

  @Test
  void saveCaseAndEmitCaseUpdatedEvent() {
    ReflectionTestUtils.setField(underTest, "caseUpdateTopic", "Test topic");
    ReflectionTestUtils.setField(underTest, "pubsubProject", "Test project");

    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());
    collex.setSurvey(survey);

    Case caze = new Case();
    caze.setId(UUID.randomUUID());
    caze.setCaseRef(1234567890L);
    caze.setCollectionExercise(collex);
    // TODO set sample fields
    caze.setInvalid(true);
    caze.setRefusalReceived(RefusalType.HARD_REFUSAL);

    underTest.saveCaseAndEmitCaseUpdate(caze, TEST_CORRELATION_ID, TEST_ORIGINATING_USER);
    verify(caseRepository).saveAndFlush(caze);

    ArgumentCaptor<EventDTO> eventArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);

    verify(messageSender).sendMessage(any(), eventArgumentCaptor.capture());
    EventDTO actualEvent = eventArgumentCaptor.getValue();

    assertThat(actualEvent.getHeader().getTopic()).isEqualTo("Test topic");
    assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(actualEvent.getHeader().getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);

    CaseUpdateDTO actualCaseUpdate = actualEvent.getPayload().getCaseUpdate();
    assertThat(actualCaseUpdate.getCaseId()).isEqualTo(caze.getId());
    assertThat(actualCaseUpdate.getCaseRef()).isEqualTo(caze.getCaseRef().toString());
    assertThat(actualCaseUpdate.getCollectionExerciseId()).isEqualTo(collex.getId());
    assertThat(actualCaseUpdate.getSurveyId()).isEqualTo(survey.getId());
    assertThat(actualCaseUpdate.isInvalid()).isTrue();
    assertThat(actualCaseUpdate.getRefusalReceived()).isEqualTo(RefusalTypeDTO.HARD_REFUSAL);
  }

  @Test
  void saveCase() {
    Case caze = new Case();
    underTest.saveCase(caze);
    verify(caseRepository).saveAndFlush(caze);
  }

  @Test
  void emitCaseUpdatedEvent() {
    ReflectionTestUtils.setField(underTest, "caseUpdateTopic", "Test topic");
    ReflectionTestUtils.setField(underTest, "pubsubProject", "Test project");

    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());
    collex.setSurvey(survey);

    Case caze = new Case();
    caze.setId(UUID.randomUUID());
    caze.setCaseRef(1234567890L);
    caze.setCollectionExercise(collex);
    // TODO set sample fields
    caze.setInvalid(true);
    caze.setRefusalReceived(RefusalType.EXTRAORDINARY_REFUSAL);
    caze.setCreatedAt(OffsetDateTime.now().minusSeconds(10));
    caze.setLastUpdatedAt(OffsetDateTime.now());

    underTest.emitCaseUpdate(caze, TEST_CORRELATION_ID, TEST_ORIGINATING_USER);

    ArgumentCaptor<EventDTO> eventArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);

    verify(messageSender).sendMessage(any(), eventArgumentCaptor.capture());
    EventDTO actualEvent = eventArgumentCaptor.getValue();

    assertThat(actualEvent.getHeader().getTopic()).isEqualTo("Test topic");
    assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    assertThat(actualEvent.getHeader().getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);

    CaseUpdateDTO actualCaseUpdate = actualEvent.getPayload().getCaseUpdate();
    assertThat(actualCaseUpdate.getCaseId()).isEqualTo(caze.getId());
    assertThat(actualCaseUpdate.getCaseRef()).isEqualTo(caze.getCaseRef().toString());
    assertThat(actualCaseUpdate.getCollectionExerciseId()).isEqualTo(collex.getId());
    assertThat(actualCaseUpdate.getSurveyId()).isEqualTo(survey.getId());
    assertThat(actualCaseUpdate.isInvalid()).isTrue();
    assertThat(actualCaseUpdate.getRefusalReceived())
        .isEqualTo(RefusalTypeDTO.EXTRAORDINARY_REFUSAL);
    assertThat(actualCaseUpdate.getCreatedAt()).isEqualTo(caze.getCreatedAt());
    assertThat(actualCaseUpdate.getLastUpdatedAt()).isEqualTo(caze.getLastUpdatedAt());
  }

  @Test
  void getCaseByCaseId() {
    Case caze = new Case();
    caze.setId(UUID.randomUUID());
    Optional<Case> caseOpt = Optional.of(caze);
    when(caseRepository.findById(any())).thenReturn(caseOpt);

    Case returnedCase = underTest.getCase(caze.getId());
    assertThat(returnedCase).isEqualTo(caze);
    verify(caseRepository).findById(caze.getId());
  }

  @Test
  void getByCaseIdMissingCase() {
    UUID caseId = UUID.randomUUID();
    String expectedErrorMessage = String.format("Case with ID '%s' not found", caseId);

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> underTest.getCase(caseId));

    assertThat(thrown.getMessage()).isEqualTo(expectedErrorMessage);
  }
}
