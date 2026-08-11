package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.FieldActionInstruction;
import uk.gov.ons.census.caseprocessor.model.dto.NewCase;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.CollectionExerciseRepository;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.caseprocessor.utils.CaseFieldMapper;
import uk.gov.ons.census.caseprocessor.utils.CaseRefGenerator;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.SampleField;
import uk.gov.ons.census.common.validation.ColumnValidator;
import uk.gov.ons.census.common.validation.SampleFieldValidators;

@MessageEndpoint
public class NewCaseReceiver {
  private final CaseRepository caseRepository;
  private final CaseService caseService;
  private final CollectionExerciseRepository collectionExerciseRepository;
  private final EventLogger eventLogger;

  @Value("${caserefgeneratorkey}")
  private byte[] caserefgeneratorkey;

  public NewCaseReceiver(
      CaseRepository caseRepository,
      CaseService caseService,
      CollectionExerciseRepository collectionExerciseRepository,
      EventLogger eventLogger) {
    this.caseRepository = caseRepository;
    this.caseService = caseService;
    this.collectionExerciseRepository = collectionExerciseRepository;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "newCaseInputChannel", adviceChain = "retryAdvice")
  public void receiveNewCase(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());

    NewCase newCasePayload = event.getPayload().getNewCase();

    if (caseRepository.existsById(newCasePayload.getCaseId())) {
      // Case already exists, so let's not overwrite it... swallow the message quietly
      return;
    }

    CollectionExercise collex =
        collectionExerciseRepository
            .findById(newCasePayload.getCollectionExerciseId())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Collection exercise '"
                            + newCasePayload.getCollectionExerciseId()
                            + "' not found"));

    validateNewCase(newCasePayload);

    Case newCase = new Case();
    newCase.setId(newCasePayload.getCaseId());
    newCase.setCollectionExercise(collex);
    CaseFieldMapper.mapPayloadSampleFieldsToCase(newCasePayload, newCase);

    newCase = saveNewCaseAndStampCaseRef(newCase);
    caseService.emitCaseUpdate(
        newCase, event.getHeader().getCorrelationId(), event.getHeader().getOriginatingUser());

    event.getHeader().setFieldActionInstruction(FieldActionInstruction.CREATE);

    eventLogger.logCaseEvent(newCase, "New case created", EventType.NEW_CASE, event, message);
  }

  private void validateNewCase(NewCase newCasePayload) {
    ColumnValidator[] columnValidators = SampleFieldValidators.getValidators();
    List<String> validationErrors = new ArrayList<>();

    for (ColumnValidator columnValidator : columnValidators) {
      SampleField sampleField = SampleField.valueOf(columnValidator.getColumnName());
      columnValidator
          .validateData(newCasePayload.getSampleFieldValue(sampleField), false)
          .ifPresent(validationErrors::add);
    }

    if (!validationErrors.isEmpty()) {
      throw new RuntimeException(
          "NEW_CASE event: "
              + validationErrors.stream().collect(Collectors.joining(System.lineSeparator())));
    }
  }

  private Case saveNewCaseAndStampCaseRef(Case caze) {
    Case newCase = caseRepository.saveAndFlush(caze);
    newCase.setCaseRef(
        CaseRefGenerator.getCaseRef(newCase.getSecretSequenceNumber(), caserefgeneratorkey));
    return caseRepository.saveAndFlush(newCase);
  }
}
