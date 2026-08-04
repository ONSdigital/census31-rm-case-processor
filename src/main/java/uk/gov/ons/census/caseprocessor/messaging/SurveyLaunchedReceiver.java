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
import uk.gov.ons.census.caseprocessor.service.UacService;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@MessageEndpoint
public class SurveyLaunchedReceiver {

  private final EventLogger eventLogger;
  private final SurveyLaunchedService surveyLaunchedService;
  private final UacService uacService;

  public SurveyLaunchedReceiver(
      EventLogger eventLogger, SurveyLaunchedService surveyLaunchedService, UacService uacService) {
    this.eventLogger = eventLogger;
    this.surveyLaunchedService = surveyLaunchedService;
    this.uacService = uacService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "surveyLaunchedInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());

    if (!isSurveyLaunchedEvent(event)) {
      logRespondentAuthenticatedEvent(event, message);
      return;
    }

    UacQidLink uacQidLink = surveyLaunchedService.handleSurveyLaunchedEvent(event);

    eventLogger.logUacQidEvent(
        uacQidLink, "Survey launched", EventType.SURVEY_LAUNCHED, event, message);
  }

  /**
   * Returns {@code true} if the event is a genuine SURVEY_LAUNCHED event, {@code false} if it's a
   * RESPONDENT_AUTHENTICATED event that should be discarded (after logging — see {@link
   * #logRespondentAuthenticatedEvent}).
   *
   * @throws IllegalStateException if the event is neither of the expected message types.
   */
  private boolean isSurveyLaunchedEvent(EventDTO surveyEvent) {
    EventHeaderDTO header = surveyEvent.getHeader();

    return switch (header.getMessageType()) {
      case SURVEY_LAUNCHED -> true;
      case RESPONDENT_AUTHENTICATED -> false;
      default ->
          throw new IllegalStateException(
              "Event Type '%s' is invalid on this topic".formatted(header.getMessageType()));
    };
  }

  private void logRespondentAuthenticatedEvent(EventDTO surveyEvent, Message<byte[]> message) {
    UacQidLink uacQidLink =
        uacService.findByQid(
            surveyEvent.getPayload().getRespondentAuthenticated().getQuestionnaireId());

    eventLogger.logUacQidEvent(
        uacQidLink,
        "Respondent authenticated",
        EventType.RESPONDENT_AUTHENTICATED,
        surveyEvent,
        message);
  }
}
