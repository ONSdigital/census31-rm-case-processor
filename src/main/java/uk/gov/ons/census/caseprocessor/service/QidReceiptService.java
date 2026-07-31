package uk.gov.ons.census.caseprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@Service
public class QidReceiptService {

  private static final Logger log = LoggerFactory.getLogger(QidReceiptService.class);
  private final UacService uacService;
  private final CaseReceiptService caseReceiptService;

  public QidReceiptService(UacService uacService, CaseReceiptService caseReceiptService) {
    this.uacService = uacService;
    this.caseReceiptService = caseReceiptService;
  }

  public UacQidLink processReceiptEvent(EventDTO eventDTO) {

    UacQidLink uacQidLink = uacService.findByQid(eventDTO.getPayload().getReceipt().getQid());

    if (!uacQidLink.isReceiptReceived()) {
      uacQidLink.setActive(false);
      uacQidLink.setReceiptReceived(true);

      uacQidLink =
          uacService.saveAndEmitUacUpdateEvent(
              uacQidLink,
              eventDTO.getHeader().getCorrelationId(),
              eventDTO.getHeader().getOriginatingUser());

      Case caze = uacQidLink.getCaze();

      if (caze != null && "RH".equals(eventDTO.getHeader().getChannel())) {
        uacQidLink = caseReceiptService.receiptCase(uacQidLink, eventDTO);
      } else {
        if (log.isWarnEnabled()) {
          log.warn(
              "Receipt received for unaddressed UAC/QID pair not yet linked to a case. QID: {}, Correlation ID: {}, Channel: {}",
              eventDTO.getPayload().getReceipt().getQid(),
              eventDTO.getHeader().getCorrelationId(),
              eventDTO.getHeader().getChannel());
        }
      }
    }
    return uacQidLink;
  }
}
