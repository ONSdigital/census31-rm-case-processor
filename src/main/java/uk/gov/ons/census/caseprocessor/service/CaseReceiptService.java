package uk.gov.ons.census.caseprocessor.service;

import static uk.gov.ons.census.caseprocessor.utils.FormTypeHelper.mapQuestionnaireTypeToFormType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@Component
public class CaseReceiptService {

  private static final Logger log = LoggerFactory.getLogger(CaseReceiptService.class);

  private CaseService caseService;
  private static final String HH = "H";
  private static final String IND = "I";

  private Map<Key, BiFunction<Case, EventDTO, Case>> rules = new HashMap<>();

  public CaseReceiptService(CaseService caseService) {
    this.caseService = caseService;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: https://officefornationalstatistics.atlassian.net/wiki/x/igE4Ew
    */
    rules.put(new Key("HH", "U", HH), receiptAndCancel);
    rules.put(new Key("HI", "U", HH), receiptCase);
    rules.put(new Key("HI", "U", IND), receiptCase);
    rules.put(new Key("HH", "U", IND), noActionRequired);
  }

  public UacQidLink receiptCase(UacQidLink uacQidLink, EventDTO causeEvent) {
    Case caze = uacQidLink.getCaze();

    Key ruleKey = makeRulesKey(caze, uacQidLink);

    if (!rules.containsKey(ruleKey)) {
      throw new RuntimeException(ruleKey.toString() + " does not map to any known processing rule");
    }

    var unused = rules.get(ruleKey).apply(caze, causeEvent);
    return uacQidLink;
  }

  private Key makeRulesKey(Case caze, UacQidLink uacQidLink) {
    String formType = mapQuestionnaireTypeToFormType(uacQidLink.getQid());
    return new Key(caze.getCaseType(), caze.getAddressLevel(), formType);
  }

  private Case receiptCase(Case caze) {
    caze.setReceiptReceived(true);
    return caze;
  }

  BiFunction<Case, EventDTO, Case> receiptAndCancel =
      (caze, event) -> {
        if (caze.isReceiptReceived()) {
          return caze;
        }
        Case updatedCase = receiptCase(caze);
        caseService.saveCaseAndEmitCaseUpdate(
            updatedCase,
            event.getHeader().getCorrelationId(),
            event.getHeader().getOriginatingUser());
        return caze;
      };

  BiFunction<Case, EventDTO, Case> receiptCase =
      (caze, event) -> {
        if (caze.isReceiptReceived()) {
          return caze;
        }
        Case updatedCase = receiptCase(caze);
        caseService.saveCaseAndEmitCaseUpdate(
            updatedCase,
            event.getHeader().getCorrelationId(),
            event.getHeader().getOriginatingUser());
        return caze;
      };

  BiFunction<Case, EventDTO, Case> noActionRequired =
      (caze, event) -> {
        if (log.isWarnEnabled()) {
          log.warn(
              "Receipt received with questionnaire type and form type mismatch. No action rule applied. QID: {}, Correlation ID: {}, Channel: {}",
              event.getPayload().getReceipt().getQid(),
              event.getHeader().getCorrelationId(),
              event.getHeader().getChannel());
        }
        return caze;
      };

  @AllArgsConstructor
  @EqualsAndHashCode
  @ToString
  private class Key {
    private String caseType;
    private String addressLevel;
    private String formType;
  }
}
