package uk.gov.ons.census.caseprocessor.utils;

public class FormTypeHelper {
  public static final String HH_FORM_TYPE = "H";
  public static final String IND_FORM_TYPE = "I";
  public static final String CE1_FORM_TYPE = "C";
  public static final String CONT_FORM_TYPE = "Cont";

  public static String mapQuestionnaireTypeToFormType(String qid) {
    int questionnaireType = Integer.parseInt(qid.substring(0, 2));

    switch (questionnaireType) {
        // Household
      case 1:
      case 2:
      case 3:
      case 4:
        // CCS Postback
      case 51:
      case 52:
      case 53:
      case 54:
        // CCS Household
      case 71:
      case 72:
      case 73:
      case 74:
        return HH_FORM_TYPE;
        // Individual
      case 21:
      case 22:
      case 23:
      case 24:
        return IND_FORM_TYPE;
        // CE1
      case 31:
      case 32:
      case 33:
      case 34:
        // CCS CE Manager
      case 81:
      case 82:
      case 83:
      case 84:
        return CE1_FORM_TYPE;
        // Continuation forms
      case 11:
      case 12:
      case 13:
      case 14:
        // CCS Continuation
      case 61:
      case 63:
        return CONT_FORM_TYPE;
      default:
        return null;
    }
  }
}
