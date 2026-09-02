package uk.gov.ons.census.caseprocessor.utils;

public class FormTypeHelper {
  public static final String HH_FORM_TYPE = "H";
  public static final String IND_FORM_TYPE = "I";

  public static String mapQuestionnaireTypeToFormType(String qid) {
    int questionnaireType = Integer.parseInt(qid.substring(0, 2));

    switch (questionnaireType) {
      // Household
      case 1:
      case 2:
      case 3:
      case 4:
      case 5:
      case 6:
      case 7:
        return HH_FORM_TYPE;
      // Individual
      case 21:
      case 22:
      case 23:
      case 24:
      case 25:
      case 26:
      case 27:
        return IND_FORM_TYPE;
      default:
        return null;
    }
  }
}
