package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.service.SurveyLaunchedService;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@MessageEndpoint
public class SurveyLaunchedReceiver {
  private final EventLogger eventLogger;
  private final SurveyLaunchedService surveyLaunchedService;

  public SurveyLaunchedReceiver(
      EventLogger eventLogger, SurveyLaunchedService surveyLaunchedService) {
    this.eventLogger = eventLogger;
    this.surveyLaunchedService = surveyLaunchedService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "surveyLaunchedInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());

    if (!processEvent(event)) {
      return;
    }

    UacQidLink uacQidLink = surveyLaunchedService.handleSurveyLaunchedEvent(event);

    eventLogger.logUacQidEvent(
        uacQidLink, "Survey launched", EventType.SURVEY_LAUNCHED, event, message);
  }

  private boolean processEvent(EventDTO surveyEvent) {

    EventHeaderDTO event = surveyEvent.getHeader();

    switch (event.getMessageType()) {
      case SURVEY_LAUNCHED:
        return true;

      default:
        // Should never get here
        throw new RuntimeException(
            String.format("Event Type '%s' is invalid on this topic", event.getMessageType()));
    }
  }
}
