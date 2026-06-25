package uk.gov.ons.census.caseprocessor.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_UAC_SUBSCRIPTION;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.repository.ActionRuleRepository;
import uk.gov.ons.census.caseprocessor.model.repository.EmailTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.EventRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileRowRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.SmsTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.caseprocessor.testutils.ActionRulePoller;
import uk.gov.ons.census.caseprocessor.testutils.DeleteDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.JunkDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.PubsubHelper;
import uk.gov.ons.census.caseprocessor.testutils.QueueSpy;
import uk.gov.ons.census.common.model.entity.ActionRule;
import uk.gov.ons.census.common.model.entity.ActionRuleStatus;
import uk.gov.ons.census.common.model.entity.ActionRuleType;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.EmailTemplate;
import uk.gov.ons.census.common.model.entity.ExportFileRow;
import uk.gov.ons.census.common.model.entity.ExportFileTemplate;
import uk.gov.ons.census.common.model.entity.SmsTemplate;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ContextConfiguration
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class ActionRuleIT {
  private static final String PACK_CODE = "test-pack-code";
  private static final String EXPORT_FILE_DESTINATION = "test-export-file-destination";
  private static final String CREATED_BY_USER = "test@ons.gov.uk";
  private static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");

  @Value("${queueconfig.uac-update-topic}")
  private String uacUpdateTopic;

  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private UacQidLinkRepository uacQidLinkRepository;
  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private ExportFileTemplateRepository exportFileTemplateRepository;
  @Autowired private ActionRuleRepository actionRuleRepository;
  @Autowired private ExportFileRowRepository exportFileRowRepository;
  @Autowired private SmsTemplateRepository smsTemplateRepository;
  @Autowired private EmailTemplateRepository emailTemplateRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private ActionRulePoller actionRulePoller;

  @BeforeEach
  public void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_UAC_SUBSCRIPTION, uacUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  void testExportFileRule() throws Exception {
    try (QueueSpy<EventDTO> outboundUacQueue =
        pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class)) {
      // Given
      Case caze = junkDataHelper.setupJunkCase();
      ExportFileTemplate exportFileTemplate = setUpExportFileTemplate(1);

      // When
      setUpActionRule(
          ActionRuleType.EXPORT_FILE,
          caze.getCollectionExercise(),
          exportFileTemplate,
          null,
          null,
          null);
      EventDTO rme = outboundUacQueue.getQueue().poll(20, TimeUnit.SECONDS);
      List<ExportFileRow> exportFileRows = exportFileRowRepository.findAll();
      ExportFileRow exportFileRow = exportFileRows.get(0);

      // Then
      assertThat(exportFileRow).isNotNull();
      assertThat(exportFileRow.getBatchQuantity()).isEqualTo(1);
      assertThat(exportFileRow.getPackCode()).isEqualTo(PACK_CODE);
      assertThat(exportFileRow.getExportFileDestination()).isEqualTo(EXPORT_FILE_DESTINATION);
      assertThat(exportFileRow.getRow())
          .startsWith("\"" + caze.getCaseRef() + "\",\"" + caze.getAddressLine1() + "\",\"");

      assertThat(rme).isNotNull();
      assertThat(rme.getHeader().getTopic()).isEqualTo(uacUpdateTopic);
      assertThat(rme.getPayload().getUacUpdate().getCaseId()).isEqualTo(caze.getId());
    }
  }

  @Test
  void testExportFileRuleNoUacQid() throws Exception {
    try (QueueSpy<EventDTO> outboundUacQueue =
        pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class)) {
      // Given
      Case caze = junkDataHelper.setupJunkCase();
      ExportFileTemplate exportFileTemplate = setUpExportFileTemplateNoUac();

      // When
      setUpActionRule(
          ActionRuleType.EXPORT_FILE,
          caze.getCollectionExercise(),
          exportFileTemplate,
          null,
          null,
          null);
      EventDTO rme = outboundUacQueue.getQueue().poll(20, TimeUnit.SECONDS);
      List<ExportFileRow> exportFileRows = exportFileRowRepository.findAll();
      ExportFileRow exportFileRow = exportFileRows.get(0);

      // Then
      assertThat(exportFileRow).isNotNull();
      assertThat(exportFileRow.getBatchQuantity()).isEqualTo(1);
      assertThat(exportFileRow.getPackCode()).isEqualTo(PACK_CODE);
      assertThat(exportFileRow.getExportFileDestination()).isEqualTo(EXPORT_FILE_DESTINATION);
      assertThat(exportFileRow.getRow())
          .startsWith("\"" + caze.getCaseRef() + "\",\"" + caze.getAddressLine1() + "\"");
    }
  }

  @Test
  void testUacTemplateWithNoQidType(CapturedOutput output) throws Exception {
    Case caze = junkDataHelper.setupJunkCase();
    ExportFileTemplate exportFileTemplate = setUpExportFileTemplate(null);

    // When
    ActionRule actionRule =
        setUpActionRule(
            ActionRuleType.EXPORT_FILE,
            caze.getCollectionExercise(),
            exportFileTemplate,
            null,
            null,
            "NoneExistantColumn = 'Throw A SQL Exception");

    actionRulePoller.getTriggeredActionRule(actionRule.getId());

    String expectedErrorMessage =
        "ActionRule "
            + actionRule.getId()
            + " failed with an IllegalStateException,"
            + " it has been marked Triggered to stop it running until it is fixed.";

    assertThat(output).contains(expectedErrorMessage);
  }

  @Test
  void testDeactivateUacRule() throws Exception {
    try (QueueSpy<EventDTO> outboundUacQueue =
        pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class)) {
      // Given
      Case caze = junkDataHelper.setupJunkCase();
      UacQidLink uacQidLink = setupUacQidLink(caze);

      // When
      setUpActionRule(
          ActionRuleType.DEACTIVATE_UAC, caze.getCollectionExercise(), null, null, null, null);
      EventDTO rme = outboundUacQueue.getQueue().poll(20, TimeUnit.SECONDS);

      // Then
      assertThat(rme).isNotNull();
      assertThat(rme.getHeader().getTopic()).isEqualTo(uacUpdateTopic);
      assertThat(rme.getPayload().getUacUpdate().getCaseId()).isEqualTo(caze.getId());
      assertThat(rme.getPayload().getUacUpdate().isActive()).isFalse();
      assertThat(rme.getPayload().getUacUpdate().getQid()).isEqualTo(uacQidLink.getQid());

      assertThat(uacQidLinkRepository.findByQid(uacQidLink.getQid()).get().isActive()).isFalse();
    }
  }

  @Test
  void testBadSQLIsHandled(CapturedOutput output) throws Exception {
    Case caze = junkDataHelper.setupJunkCase();
    ExportFileTemplate exportFileTemplate = setUpExportFileTemplate(1);

    // When
    ActionRule actionRule =
        setUpActionRule(
            ActionRuleType.EXPORT_FILE,
            caze.getCollectionExercise(),
            exportFileTemplate,
            null,
            null,
            "NoneExistantColumn = 'Throw A SQL Exception");

    actionRulePoller.getTriggeredActionRule(actionRule.getId());

    String expectedErrorMessage =
        "ActionRule "
            + actionRule.getId()
            + " failed with a BadSqlGrammarException"
            + ", it has been marked Triggered to stop it running until it is fixed";

    assertThat(output).contains(expectedErrorMessage);
  }

  private ExportFileTemplate setUpExportFileTemplate(Integer questionnaireType) {
    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setTemplate(new String[] {"__caseref__", "ADDRESS_LINE1", "__uac__"});
    exportFileTemplate.setPackCode(PACK_CODE);
    exportFileTemplate.setExportFileDestination(EXPORT_FILE_DESTINATION);
    exportFileTemplate.setDescription("Test description");
    exportFileTemplate.setQuestionnaireType(questionnaireType);
    return exportFileTemplateRepository.saveAndFlush(exportFileTemplate);
  }

  private ExportFileTemplate setUpExportFileTemplateNoUac() {
    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setTemplate(new String[] {"__caseref__", "ADDRESS_LINE1"});
    exportFileTemplate.setPackCode(PACK_CODE);
    exportFileTemplate.setExportFileDestination(EXPORT_FILE_DESTINATION);
    exportFileTemplate.setDescription("Test description");
    exportFileTemplate.setQuestionnaireType(null);
    return exportFileTemplateRepository.saveAndFlush(exportFileTemplate);
  }

  private ActionRule setUpActionRule(
      ActionRuleType type,
      CollectionExercise collectionExercise,
      ExportFileTemplate exportFileTemplate,
      SmsTemplate smsTemplate,
      EmailTemplate emailTemplate,
      String classifiers) {
    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setTriggerDateTime(OffsetDateTime.now());
    actionRule.setHasTriggered(false);
    actionRule.setType(type);
    actionRule.setCollectionExercise(collectionExercise);
    actionRule.setExportFileTemplate(exportFileTemplate);
    actionRule.setCreatedBy(CREATED_BY_USER);
    actionRule.setUacMetadata(TEST_UAC_METADATA);
    actionRule.setClassifiers(classifiers);
    actionRule.setSelectedCaseCount(null);
    actionRule.setActionRuleStatus(ActionRuleStatus.SCHEDULED);

    return actionRuleRepository.saveAndFlush(actionRule);
  }

  private UacQidLink setupUacQidLink(Case caze) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setQid("123456789");
    uacQidLink.setUac("abc");
    uacQidLink.setUacHash("fakeHash");
    uacQidLink.setActive(true);
    uacQidLink.setCaze(caze);
    return uacQidLinkRepository.saveAndFlush(uacQidLink);
  }
}
