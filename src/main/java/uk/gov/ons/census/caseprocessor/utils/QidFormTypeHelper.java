package uk.gov.ons.census.caseprocessor.utils;

import static java.util.Map.entry;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QidFormTypeHelper {

  private static final Map<Integer, String> FORM_TYPE_MAP =
      Map.ofEntries(
          entry(1, "H"),
          entry(2, "H"),
          entry(3, "H"),
          entry(4, "H"),
          entry(5, "H"),
          entry(6, "HA"),
          entry(7, "HB"),
          entry(21, "I"),
          entry(22, "I"),
          entry(23, "I"),
          entry(24, "I"),
          entry(25, "I"),
          entry(26, "IA"),
          entry(27, "IB"));

  private QidFormTypeHelper() {}

  @SuppressWarnings("PMD.GuardLogStatement")
  public static String mapQidToFormType(String qid) {
    Integer questionnaireType = parseQuestionnaireTypePrefix(qid);
    if (questionnaireType == null) {
      return null;
    }

    String formType = FORM_TYPE_MAP.get(questionnaireType);
    if (formType == null) {
      log.atWarn()
          .setMessage("Unable to parse form type from QID: unknown questionnaire type")
          .addKeyValue("qid", qid)
          .addKeyValue("questionnaireType", questionnaireType)
          .log();
    }
    return formType;
  }

  @SuppressWarnings("PMD.GuardLogStatement")
  private static Integer parseQuestionnaireTypePrefix(String qid) {
    if (qid == null || qid.length() < 2) {
      log.atWarn()
          .setMessage("Unable to parse form type from QID: QID is null or too short")
          .addKeyValue("qid", qid)
          .log();
      return null;
    }

    String prefix = qid.substring(0, 2);
    if (!prefix.chars().allMatch(Character::isDigit)) {
      log.atWarn()
          .setMessage("Unable to parse form type from QID: non-numeric prefix")
          .addKeyValue("qid", qid)
          .addKeyValue("prefix", prefix)
          .log();
      return null;
    }

    return Integer.parseInt(prefix);
  }
}
