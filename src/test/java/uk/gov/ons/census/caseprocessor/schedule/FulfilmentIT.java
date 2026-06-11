package uk.gov.ons.census.caseprocessor.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_UAC_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PrintFulfilmentDTO;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileRowRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentNextTriggerRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentSurveyExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.testutils.DeleteDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.JunkDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.PubsubHelper;
import uk.gov.ons.census.caseprocessor.testutils.QueueSpy;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.ExportFileRow;
import uk.gov.ons.census.common.model.entity.ExportFileTemplate;
import uk.gov.ons.census.common.model.entity.FulfilmentNextTrigger;
import uk.gov.ons.census.common.model.entity.FulfilmentSurveyExportFileTemplate;

@ContextConfiguration
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
class FulfilmentIT {
  private static final String FULFILMENT_TOPIC = "event_print-fulfilment";

  private static final String PACK_CODE = "test-pack-code";
  private static final String EXPORT_FILE_DESTINATION = "FOOBAR_EXPORT_FILE_DESTINATION";

  @Value("${queueconfig.uac-update-topic}")
  private String uacUpdateTopic;

  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private FulfilmentNextTriggerRepository fulfilmentNextTriggerRepository;
  @Autowired private ExportFileTemplateRepository exportFileTemplateRepository;
  @Autowired private ExportFileRowRepository exportFileRowRepository;

  @Autowired
  private FulfilmentSurveyExportFileTemplateRepository fulfilmentSurveyExportFileTemplateRepository;

  @BeforeEach
  public void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_UAC_SUBSCRIPTION, uacUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  void testFulfilmentTrigger() throws Exception {
    try (QueueSpy<EventDTO> outboundUacQueue =
        pubsubHelper.pubsubProjectListen(OUTBOUND_UAC_SUBSCRIPTION, EventDTO.class)) {
      // Given
      ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
      exportFileTemplate.setPackCode(PACK_CODE);
      exportFileTemplate.setExportFileDestination(EXPORT_FILE_DESTINATION);
      exportFileTemplate.setTemplate(new String[] {"__caseref__", "ADDRESS_LINE1", "__uac__"});
      exportFileTemplate.setDescription("Test description");
      exportFileTemplate.setQuestionnaireType(1);
      exportFileTemplateRepository.saveAndFlush(exportFileTemplate);

      Case caze = junkDataHelper.setupJunkCase();

      FulfilmentSurveyExportFileTemplate fulfilmentSurveyExportFileTemplate =
          new FulfilmentSurveyExportFileTemplate();
      fulfilmentSurveyExportFileTemplate.setId(UUID.randomUUID());
      fulfilmentSurveyExportFileTemplate.setSurvey(caze.getCollectionExercise().getSurvey());
      fulfilmentSurveyExportFileTemplate.setExportFileTemplate(exportFileTemplate);
      fulfilmentSurveyExportFileTemplateRepository.saveAndFlush(fulfilmentSurveyExportFileTemplate);

      // When
      PrintFulfilmentDTO fulfilment = new PrintFulfilmentDTO();
      fulfilment.setCaseId(caze.getId());
      fulfilment.setPackCode(PACK_CODE);

      PayloadDTO payload = new PayloadDTO();
      payload.setPrintFulfilment(fulfilment);

      EventDTO event = new EventDTO();
      event.setPayload(payload);

      EventHeaderDTO eventHeader = new EventHeaderDTO();
      eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
      eventHeader.setTopic(FULFILMENT_TOPIC);
      junkDataHelper.junkify(eventHeader);
      event.setHeader(eventHeader);

      pubsubHelper.sendMessageToPubsubProject(FULFILMENT_TOPIC, event);

      Thread.sleep(3000);

      FulfilmentNextTrigger fulfilmentNextTrigger = new FulfilmentNextTrigger();
      fulfilmentNextTrigger.setId(UUID.randomUUID());
      fulfilmentNextTrigger.setTriggerDateTime(OffsetDateTime.now());
      fulfilmentNextTriggerRepository.saveAndFlush(fulfilmentNextTrigger);

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
}
