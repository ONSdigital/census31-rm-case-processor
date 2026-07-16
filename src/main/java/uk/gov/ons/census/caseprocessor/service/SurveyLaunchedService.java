package uk.gov.ons.census.caseprocessor.service;

import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@Component
public class SurveyLaunchedService {

  private final CaseService caseService;
  private final UacService uacService;

  public SurveyLaunchedService(CaseService caseService, UacService uacService) {
    this.caseService = caseService;
    this.uacService = uacService;
  }

  public UacQidLink handleSurveyLaunchedEvent(EventDTO eventDTO) {

    UacQidLink uacQidLink =
        uacService.findByQid(eventDTO.getPayload().getSurveyLaunched().getQuestionnaireId());

    Case caze = uacQidLink.getCaze();
    uacQidLink.setEqLaunched(true);
    uacService.saveAndEmitUacUpdateEvent(
        uacQidLink,
        eventDTO.getHeader().getCorrelationId(),
        eventDTO.getHeader().getOriginatingUser());

    if (caze != null && "RH".equals(eventDTO.getHeader().getChannel())) {
      caze.setSurveyLaunched(true);
      caseService.saveCaseAndEmitCaseUpdate(
          caze, eventDTO.getHeader().getCorrelationId(), eventDTO.getHeader().getOriginatingUser());
    }
    return uacQidLink;
  }
}
