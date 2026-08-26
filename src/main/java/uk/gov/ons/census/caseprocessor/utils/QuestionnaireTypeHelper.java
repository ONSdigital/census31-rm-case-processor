package uk.gov.ons.census.caseprocessor.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuestionnaireTypeHelper {
  public static final String HH_FORM_TYPE = "H";
  public static final String IND_FORM_TYPE = "I";

  private QuestionnaireTypeHelper() {}

  @SuppressWarnings("PMD.GuardLogStatement")
  public static String mapQuestionnaireTypeToFormType(String qid) {
    if (qid == null || qid.length() < 2) {
      log.warn("Unable to parse questionnaire type from QID: QID is null or too short");
      return null;
    }

    int questionnaireType;
    try {
      questionnaireType = Integer.parseInt(qid.substring(0, 2));
    } catch (NumberFormatException e) {
      log.warn(
          "Unable to parse questionnaire type from QID: non-numeric prefix '{}'",
          qid.substring(0, 2));
      return null;
    }

    return switch (questionnaireType) {
      case 1, 2, 3, 4, 5, 6, 7 -> HH_FORM_TYPE;
      case 21, 22, 23, 24, 25, 26, 27 -> IND_FORM_TYPE;
      default -> {
        log.warn(
            "Unable to parse questionnaire type from QID: unknown type '{}'", questionnaireType);
        yield null;
      }
    };
  }
}
