package uk.gov.ons.census.caseprocessor.utils;

public class QuestionnaireTypeHelper {
  public static final String HH_FORM_TYPE = "H";
  public static final String IND_FORM_TYPE = "I";

  private QuestionnaireTypeHelper() {}

  /**
   * Derives the coarse receipting form type (household or individual) for a QID by delegating to
   * {@link QidFormTypeHelper} for the detailed form type and collapsing it to HH_FORM_TYPE or
   * IND_FORM_TYPE.
   */
  public static String mapQuestionnaireTypeToFormType(String qid) {
    String formType = QidFormTypeHelper.mapQidToFormType(qid);
    if (formType == null) {
      return null;
    }

    if (formType.startsWith(HH_FORM_TYPE)) {
      return HH_FORM_TYPE;
    }
    if (formType.startsWith(IND_FORM_TYPE)) {
      return IND_FORM_TYPE;
    }
    return null;
  }
}
