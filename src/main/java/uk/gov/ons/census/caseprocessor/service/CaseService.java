package uk.gov.ons.census.caseprocessor.service;

import static com.google.cloud.spring.pubsub.support.PubSubTopicUtils.toProjectTopicName;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.CaseUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.FieldActionInstruction;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.RefusalTypeDTO;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.utils.CaseFieldMapper;
import uk.gov.ons.census.caseprocessor.utils.EventHelper;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;

@Service
public class CaseService {
  private final CaseRepository caseRepository;
  private final MessageSender messageSender;

  @Value("${queueconfig.case-update-topic}")
  private String caseUpdateTopic;

  @Value("${spring.cloud.gcp.pubsub.project-id}")
  private String pubsubProject;

  public CaseService(CaseRepository caseRepository, MessageSender messageSender) {
    this.caseRepository = caseRepository;
    this.messageSender = messageSender;
  }

  public void saveCaseAndEmitCaseUpdate(Case caze, UUID correlationId, String originatingUser) {
    saveCase(caze);
    emitCaseUpdate(caze, correlationId, originatingUser);
  }

  public void saveCase(Case caze) {
    caseRepository.saveAndFlush(caze);
  }

  public void emitCaseUpdate(Case caze, UUID correlationId, String originatingUser) {
    emitCaseUpdate(caze, correlationId, originatingUser, null);
  }

  public void emitCaseUpdate(
      Case caze,
      UUID correlationId,
      String originatingUser,
      FieldActionInstruction fieldActionInstruction) {
    EventHeaderDTO eventHeader =
        EventHelper.createEventDTO(
            caseUpdateTopic, correlationId, originatingUser, EventType.CASE_UPDATE);

    if (fieldActionInstruction != null) {
      eventHeader.setFieldActionInstruction(fieldActionInstruction);
    }

    EventDTO event = prepareCaseEvent(caze, eventHeader);

    String topic = toProjectTopicName(caseUpdateTopic, pubsubProject).toString();
    messageSender.sendMessage(topic, event);
  }

  private EventDTO prepareCaseEvent(Case caze, EventHeaderDTO eventHeader) {
    PayloadDTO payloadDTO = new PayloadDTO();
    CaseUpdateDTO caseUpdate = new CaseUpdateDTO();
    caseUpdate.setCaseId(caze.getId());
    caseUpdate.setCaseRef(Long.toString(caze.getCaseRef()));
    caseUpdate.setCollectionExerciseId(caze.getCollectionExercise().getId());
    caseUpdate.setSurveyId(caze.getCollectionExercise().getSurvey().getId());
    caseUpdate.setCreatedAt(caze.getCreatedAt());
    caseUpdate.setLastUpdatedAt(caze.getLastUpdatedAt());
    caseUpdate.setReceiptReceived(caze.isReceiptReceived());
    caseUpdate.setSurveyLaunched(caze.isSurveyLaunched());
    caseUpdate.setInvalid(caze.isInvalid());

    if (caze.getRefusalReceived() != null) {
      caseUpdate.setRefusalReceived(RefusalTypeDTO.valueOf(caze.getRefusalReceived().name()));
    } else {
      caseUpdate.setRefusalReceived(null);
    }

    CaseFieldMapper.mapCaseSampleFieldsToCaseUpdateDTO(caze, caseUpdate);

    payloadDTO.setCaseUpdate(caseUpdate);
    EventDTO event = new EventDTO();
    event.setHeader(eventHeader);
    event.setPayload(payloadDTO);
    return event;
  }

  public Case getCase(UUID caseId) {
    Optional<Case> cazeResult = caseRepository.findById(caseId);

    if (cazeResult.isEmpty()) {
      throw new RuntimeException(String.format("Case with ID '%s' not found", caseId));
    }
    return cazeResult.get();
  }

  public Case getCaseAndLockForUpdate(UUID caseId) {
    Optional<Case> cazeResult = caseRepository.findByIdWithUpdateLock(caseId);

    if (cazeResult.isEmpty()) {
      throw new RuntimeException(
          String.format(
              "Case with ID '%s' not found, or could not obtain lock due to contention", caseId));
    }

    return cazeResult.get();
  }
}
