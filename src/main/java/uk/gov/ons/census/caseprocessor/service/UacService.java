package uk.gov.ons.census.caseprocessor.service;

import static com.google.cloud.spring.pubsub.support.PubSubTopicUtils.toProjectTopicName;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UacUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.caseprocessor.utils.EventHelper;
import uk.gov.ons.census.caseprocessor.utils.HashHelper;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@Service
public class UacService {
  private final UacQidLinkRepository uacQidLinkRepository;
  private final MessageSender messageSender;

  @Value("${queueconfig.uac-update-topic}")
  private String uacUpdateTopic;

  @Value("${spring.cloud.gcp.pubsub.project-id}")
  private String pubsubProject;

  public UacService(UacQidLinkRepository uacQidLinkRepository, MessageSender messageSender) {
    this.messageSender = messageSender;
    this.uacQidLinkRepository = uacQidLinkRepository;
  }

  public UacQidLink saveAndEmitUacUpdateEvent(
      UacQidLink uacQidLink, UUID correlationId, String originatingUser) {
    UacQidLink savedUacQidLink = uacQidLinkRepository.save(uacQidLink);

    EventHeaderDTO eventHeader =
        EventHelper.createEventDTO(
            uacUpdateTopic, correlationId, originatingUser, EventType.UAC_UPDATE);

    UacUpdateDTO uac = new UacUpdateDTO();
    uac.setQid(savedUacQidLink.getQid());
    uac.setUacHash(savedUacQidLink.getUacHash());
    uac.setActive(savedUacQidLink.isActive());
    uac.setReceiptReceived(savedUacQidLink.isReceiptReceived());
    uac.setEqLaunched(savedUacQidLink.isEqLaunched());

    if (savedUacQidLink.getCaze() != null) {
      uac.setCaseId(savedUacQidLink.getCaze().getId());
      uac.setCollectionExerciseId(savedUacQidLink.getCaze().getCollectionExercise().getId());
      uac.setSurveyId(savedUacQidLink.getCaze().getCollectionExercise().getSurvey().getId());
    } else {
      uac.setCaseId(null);
      uac.setCollectionExerciseId(null);
      uac.setSurveyId(null);
    }

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setUacUpdate(uac);
    EventDTO event = new EventDTO();
    event.setHeader(eventHeader);
    event.setPayload(payloadDTO);

    String topic = toProjectTopicName(uacUpdateTopic, pubsubProject).toString();
    messageSender.sendMessage(topic, event);

    return savedUacQidLink;
  }

  public UacQidLink findByQid(String qid) {
    Optional<UacQidLink> uacQidLinkOpt = uacQidLinkRepository.findByQid(qid);

    if (uacQidLinkOpt.isEmpty()) {
      throw new RuntimeException(String.format("qid '%s' not found!", qid));
    }

    return uacQidLinkOpt.get();
  }

  public boolean existsByQid(String qid) {
    return uacQidLinkRepository.existsByQid(qid);
  }

  public void createLinkAndEmitNewUacQid(
      Case caze,
      String uac,
      String qid,
      Object metadata,
      UUID correlationId,
      String originatingUser) {

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(uac);
    uacQidLink.setUacHash(HashHelper.hash(uac));
    uacQidLink.setQid(qid);
    uacQidLink.setMetadata(metadata);
    uacQidLink.setCaze(caze);
    saveAndEmitUacUpdateEvent(uacQidLink, correlationId, originatingUser);
  }
}
