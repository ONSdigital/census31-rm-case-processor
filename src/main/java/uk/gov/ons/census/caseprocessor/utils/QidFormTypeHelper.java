package uk.gov.ons.census.caseprocessor.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QidFormTypeHelper {

  private QidFormTypeHelper() {}

  @SuppressWarnings("PMD.GuardLogStatement")
  public static String mapQidToFormType(String qid) {
    if (qid == null || qid.length() < 2) {
      log.warn("Unable to parse form type from QID: QID is null or too short");
      return null;
    }

    int questionnaireType;
    try {
      questionnaireType = Integer.parseInt(qid.substring(0, 2));
    } catch (NumberFormatException e) {
      log.warn("Unable to parse form type from QID: non-numeric prefix '{}'", qid.substring(0, 2));
      return null;
    }

    return switch (questionnaireType) {
      case 1, 2, 3, 4, 5 -> "H";
      case 6 -> "HA";
      case 7 -> "HB";
      case 21, 22, 23, 24, 25 -> "I";
      case 26 -> "IA";
      case 27 -> "IB";
      default -> {
        log.warn(
            "Unable to parse form type from QID: unknown questionnaire type '{}'",
            questionnaireType);
        yield null;
      }
    };
  }
}
