package uk.gov.ons.census.caseprocessor.testutils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.model.repository.ActionRuleRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ActionRuleSurveyExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.CaseToProcessRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ClusterLeaderRepository;
import uk.gov.ons.census.caseprocessor.model.repository.CollectionExerciseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.EventRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileRowRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentNextTriggerRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentSurveyExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentToProcessRepository;
import uk.gov.ons.census.caseprocessor.model.repository.SmsTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.SurveyRepository;
import uk.gov.ons.census.caseprocessor.model.repository.UacQidLinkRepository;

@Component
@ActiveProfiles("test")
public class DeleteDataHelper {
  @Autowired private CaseRepository caseRepository;
  @Autowired private CollectionExerciseRepository collectionExerciseRepository;
  @Autowired private SurveyRepository surveyRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private FulfilmentNextTriggerRepository fulfilmentNextTriggerRepository;
  @Autowired private FulfilmentToProcessRepository fulfilmentToProcessRepository;
  @Autowired private ExportFileTemplateRepository exportFileTemplateRepository;

  @Autowired
  private FulfilmentSurveyExportFileTemplateRepository fulfilmentSurveyExportFileTemplateRepository;

  @Autowired
  private ActionRuleSurveyExportFileTemplateRepository actionRuleSurveyExportFileTemplateRepository;

  @Autowired private ActionRuleRepository actionRuleRepository;
  @Autowired private CaseToProcessRepository caseToProcessRepository;
  @Autowired private ClusterLeaderRepository clusterLeaderRepository;
  @Autowired private ExportFileRowRepository exportFileRowRepository;
  @Autowired private SmsTemplateRepository smsTemplateRepository;
  @Autowired private FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;

  @Transactional
  public void deleteAllData() {
    actionRuleRepository.deleteAllInBatch();
    fulfilmentNextTriggerRepository.deleteAllInBatch();
    caseToProcessRepository.deleteAllInBatch();
    fulfilmentSurveySmsTemplateRepository.deleteAllInBatch();

    fulfilmentToProcessRepository.deleteAllInBatch();
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
    collectionExerciseRepository.deleteAllInBatch();
    fulfilmentSurveyExportFileTemplateRepository.deleteAllInBatch();
    actionRuleSurveyExportFileTemplateRepository.deleteAllInBatch();
    exportFileTemplateRepository.deleteAllInBatch();
    surveyRepository.deleteAllInBatch();
    clusterLeaderRepository.deleteAllInBatch();
    exportFileRowRepository.deleteAllInBatch();
    smsTemplateRepository.deleteAllInBatch();
  }
}
