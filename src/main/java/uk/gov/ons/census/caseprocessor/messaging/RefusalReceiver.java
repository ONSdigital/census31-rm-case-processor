package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.RefusalDTO;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.RefusalType;

@MessageEndpoint
public class RefusalReceiver {
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public RefusalReceiver(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "refusalInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());

    RefusalDTO refusal = event.getPayload().getRefusal();
    Case refusedCase = caseService.getCase(refusal.getCaseId());
    refusedCase.setRefusalReceived(RefusalType.valueOf(refusal.getType().name()));

    caseService.saveCaseAndEmitCaseUpdate(
        refusedCase, event.getHeader().getCorrelationId(), event.getHeader().getOriginatingUser());

    eventLogger.logCaseEvent(refusedCase, "Refusal Received", EventType.REFUSAL, event, message);
  }
}
