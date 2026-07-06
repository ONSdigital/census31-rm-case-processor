package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_UAC_METADATA;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.ons.census.caseprocessor.cache.UacQidCache;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UacQidDTO;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileRowRepository;
import uk.gov.ons.census.caseprocessor.testutils.CaseFieldsHelper;
import uk.gov.ons.census.common.model.entity.*;

@ExtendWith(MockitoExtension.class)
class ExportFileProcessorTest {
  @Mock private UacQidCache uacQidCache;
  @Mock private UacService uacService;
  @Mock private EventLogger eventLogger;
  @Mock private ExportFileRowRepository exportFileRowRepository;

  @InjectMocks ExportFileProcessor underTest;

  private static final String PACK_CODE = "test pack code";
  private static final String UAC = "test UAC";
  private static final String QID = "test QID";
  private static final String EXPORT_FILE_DESTINATION = "test export file destination";

  @Test
  void testProcessExportFileRow() {
    // Given
    Case caze = new Case();

    CaseFieldsHelper.setDummyCaseFields(caze);

    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setTemplate(new String[] {"__caseref__", "__uac__", "UPRN"});
    exportFileTemplate.setPackCode(PACK_CODE);
    exportFileTemplate.setExportFileDestination(EXPORT_FILE_DESTINATION);

    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setType(ActionRuleType.EXPORT_FILE);
    actionRule.setExportFileTemplate(exportFileTemplate);
    actionRule.setUacMetadata(TEST_UAC_METADATA);

    CaseToProcess caseToProcess = new CaseToProcess();
    caseToProcess.setActionRule(actionRule);
    caseToProcess.setCaze(caze);
    caseToProcess.setBatchId(UUID.fromString("6a127d58-c1cb-489c-a3f5-72014a0c32d6"));

    UacQidDTO uacQidDTO = new UacQidDTO();
    uacQidDTO.setUac(UAC);
    uacQidDTO.setQid(QID);

    when(uacQidCache.getUacQidPair(anyInt())).thenReturn(uacQidDTO);

    // When
    underTest.processExportFileRow(
        exportFileTemplate.getTemplate(),
        caze,
        caseToProcess.getBatchId(),
        caseToProcess.getBatchQuantity(),
        exportFileTemplate.getPackCode(),
        exportFileTemplate.getExportFileDestination(),
        actionRule.getId(),
        null,
        1,
        actionRule.getUacMetadata());

    //    // Then
    ArgumentCaptor<ExportFileRow> exportFileRowArgumentCaptor =
        ArgumentCaptor.forClass(ExportFileRow.class);
    verify(exportFileRowRepository).save(exportFileRowArgumentCaptor.capture());
    ExportFileRow actualExportFileRow = exportFileRowArgumentCaptor.getValue();
    assertThat(actualExportFileRow.getPackCode()).isEqualTo(PACK_CODE);
    assertThat(actualExportFileRow.getExportFileDestination()).isEqualTo(EXPORT_FILE_DESTINATION);
    assertThat(actualExportFileRow.getRow())
        .isEqualTo("\"123\",\"" + UAC + "\",\"" + caze.getUprn() + "\"");

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(uacQidLinkCaptor.capture(), eq(actionRule.getId()), isNull());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getUac()).isEqualTo(UAC);
    assertThat(actualUacQidLink.getQid()).isEqualTo(QID);
    assertThat(actualUacQidLink.getCaze()).isEqualTo(caze);
    assertThat(actualUacQidLink.isActive()).isTrue();
    assertThat(actualUacQidLink.getMetadata()).isEqualTo(TEST_UAC_METADATA);

    ArgumentCaptor<EventDTO> eventCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("Export file generated with pack code " + PACK_CODE),
            eq(EventType.EXPORT_FILE),
            eventCaptor.capture(),
            any(OffsetDateTime.class));

    EventDTO actualEvent = eventCaptor.getValue();
    Assertions.assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(actionRule.getId());
    Assertions.assertThat(actualEvent.getPayload().getExportFile().getPackCode())
        .isEqualTo(PACK_CODE);
  }

  @Test
  void testProcessExportFileRowNoUacQid() {
    // Given
    Case caze = new Case();

    CaseFieldsHelper.setDummyCaseFields(caze);

    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setTemplate(new String[] {"__caseref__", "UPRN"});
    exportFileTemplate.setPackCode(PACK_CODE);
    exportFileTemplate.setExportFileDestination(EXPORT_FILE_DESTINATION);

    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setType(ActionRuleType.EXPORT_FILE);
    actionRule.setExportFileTemplate(exportFileTemplate);
    actionRule.setUacMetadata(TEST_UAC_METADATA);

    CaseToProcess caseToProcess = new CaseToProcess();
    caseToProcess.setActionRule(actionRule);
    caseToProcess.setCaze(caze);
    caseToProcess.setBatchId(UUID.fromString("6a127d58-c1cb-489c-a3f5-72014a0c32d6"));

    // When
    underTest.processExportFileRow(
        exportFileTemplate.getTemplate(),
        caze,
        caseToProcess.getBatchId(),
        caseToProcess.getBatchQuantity(),
        exportFileTemplate.getPackCode(),
        exportFileTemplate.getExportFileDestination(),
        actionRule.getId(),
        null,
        null,
        actionRule.getUacMetadata());

    //    // Then
    ArgumentCaptor<ExportFileRow> exportFileRowArgumentCaptor =
        ArgumentCaptor.forClass(ExportFileRow.class);
    verify(exportFileRowRepository).save(exportFileRowArgumentCaptor.capture());
    ExportFileRow actualExportFileRow = exportFileRowArgumentCaptor.getValue();
    assertThat(actualExportFileRow.getPackCode()).isEqualTo(PACK_CODE);
    assertThat(actualExportFileRow.getExportFileDestination()).isEqualTo(EXPORT_FILE_DESTINATION);
    assertThat(actualExportFileRow.getRow()).isEqualTo("\"123\",\"" + caze.getUprn() + "\"");

    ArgumentCaptor<EventDTO> eventCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("Export file generated with pack code " + PACK_CODE),
            eq(EventType.EXPORT_FILE),
            eventCaptor.capture(),
            any(OffsetDateTime.class));

    EventDTO actualEvent = eventCaptor.getValue();
    Assertions.assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(actionRule.getId());
    Assertions.assertThat(actualEvent.getPayload().getExportFile().getPackCode())
        .isEqualTo(PACK_CODE);
  }

  @Test
  void testProcessFulfilment() {
    // Given
    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setPackCode(PACK_CODE);
    exportFileTemplate.setExportFileDestination(EXPORT_FILE_DESTINATION);
    exportFileTemplate.setTemplate(new String[] {"__caseref__", "__uac__", "UPRN"});
    exportFileTemplate.setQuestionnaireType(1);

    Case caze = new Case();
    CaseFieldsHelper.setDummyCaseFields(caze);

    FulfilmentToProcess fulfilmentToProcess = new FulfilmentToProcess();
    fulfilmentToProcess.setExportFileTemplate(exportFileTemplate);
    fulfilmentToProcess.setCaze(caze);
    fulfilmentToProcess.setBatchId(UUID.fromString("6a127d58-c1cb-489c-a3f5-72014a0c32d6"));
    fulfilmentToProcess.setBatchQuantity(200);
    fulfilmentToProcess.setCorrelationId(TEST_CORRELATION_ID);
    fulfilmentToProcess.setOriginatingUser(TEST_ORIGINATING_USER);
    fulfilmentToProcess.setUacMetadata(TEST_UAC_METADATA);

    UacQidDTO uacQidDTO = new UacQidDTO();
    uacQidDTO.setUac(UAC);
    uacQidDTO.setQid(QID);

    when(uacQidCache.getUacQidPair(anyInt())).thenReturn(uacQidDTO);

    // When
    underTest.process(fulfilmentToProcess);

    // Then
    ArgumentCaptor<ExportFileRow> exportFileRowArgumentCaptor =
        ArgumentCaptor.forClass(ExportFileRow.class);
    verify(exportFileRowRepository).save(exportFileRowArgumentCaptor.capture());
    ExportFileRow actualExportFileRow = exportFileRowArgumentCaptor.getValue();
    assertThat(actualExportFileRow.getPackCode()).isEqualTo(PACK_CODE);
    assertThat(actualExportFileRow.getExportFileDestination()).isEqualTo(EXPORT_FILE_DESTINATION);
    assertThat(actualExportFileRow.getRow())
        .isEqualTo("\"123\",\"" + UAC + "\",\"" + caze.getUprn() + "\"");

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getUac()).isEqualTo(UAC);
    assertThat(actualUacQidLink.getQid()).isEqualTo(QID);
    assertThat(actualUacQidLink.getCaze()).isEqualTo(caze);
    assertThat(actualUacQidLink.isActive()).isTrue();
    assertThat(actualUacQidLink.getMetadata()).isEqualTo(TEST_UAC_METADATA);

    ArgumentCaptor<EventDTO> eventCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("Export file generated with pack code " + PACK_CODE),
            eq(EventType.EXPORT_FILE),
            eventCaptor.capture(),
            any(OffsetDateTime.class));

    EventHeaderDTO actualHeader = eventCaptor.getValue().getHeader();
    Assertions.assertThat(actualHeader.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    Assertions.assertThat(actualHeader.getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);
  }

  @Test
  void testProcessFulfilmentWithPersonalisation() {
    // Given
    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setPackCode(PACK_CODE);
    exportFileTemplate.setExportFileDestination(EXPORT_FILE_DESTINATION);
    exportFileTemplate.setTemplate(
        new String[] {"__caseref__", "__uac__", "__request__.foo", "__request__.spam"});
    exportFileTemplate.setQuestionnaireType(1);

    Case caze = new Case();
    CaseFieldsHelper.setDummyCaseFields(caze);

    FulfilmentToProcess fulfilmentToProcess = new FulfilmentToProcess();
    fulfilmentToProcess.setExportFileTemplate(exportFileTemplate);
    fulfilmentToProcess.setCaze(caze);
    fulfilmentToProcess.setBatchId(UUID.fromString("6a127d58-c1cb-489c-a3f5-72014a0c32d6"));
    fulfilmentToProcess.setBatchQuantity(200);
    fulfilmentToProcess.setCorrelationId(TEST_CORRELATION_ID);
    fulfilmentToProcess.setOriginatingUser(TEST_ORIGINATING_USER);
    fulfilmentToProcess.setUacMetadata(TEST_UAC_METADATA);
    fulfilmentToProcess.setPersonalisation(Map.of("foo", "bar", "spam", "eggs"));

    UacQidDTO uacQidDTO = new UacQidDTO();
    uacQidDTO.setUac(UAC);
    uacQidDTO.setQid(QID);

    when(uacQidCache.getUacQidPair(anyInt())).thenReturn(uacQidDTO);

    // When
    underTest.process(fulfilmentToProcess);

    // Then
    ArgumentCaptor<ExportFileRow> exportFileRowArgumentCaptor =
        ArgumentCaptor.forClass(ExportFileRow.class);
    verify(exportFileRowRepository).save(exportFileRowArgumentCaptor.capture());
    ExportFileRow actualExportFileRow = exportFileRowArgumentCaptor.getValue();
    assertThat(actualExportFileRow.getPackCode()).isEqualTo(PACK_CODE);
    assertThat(actualExportFileRow.getExportFileDestination()).isEqualTo(EXPORT_FILE_DESTINATION);
    assertThat(actualExportFileRow.getRow()).isEqualTo("\"123\",\"" + UAC + "\",\"bar\",\"eggs\"");

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getUac()).isEqualTo(UAC);
    assertThat(actualUacQidLink.getQid()).isEqualTo(QID);
    assertThat(actualUacQidLink.getCaze()).isEqualTo(caze);
    assertThat(actualUacQidLink.isActive()).isTrue();
    assertThat(actualUacQidLink.getMetadata()).isEqualTo(TEST_UAC_METADATA);

    ArgumentCaptor<EventDTO> eventCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("Export file generated with pack code " + PACK_CODE),
            eq(EventType.EXPORT_FILE),
            eventCaptor.capture(),
            any(OffsetDateTime.class));

    EventHeaderDTO actualHeader = eventCaptor.getValue().getHeader();
    Assertions.assertThat(actualHeader.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    Assertions.assertThat(actualHeader.getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);
  }

  @Test
  void testProcessFulfilmentNullPersonalisation() {
    // Given
    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setPackCode(PACK_CODE);
    exportFileTemplate.setExportFileDestination(EXPORT_FILE_DESTINATION);
    exportFileTemplate.setTemplate(new String[] {"__caseref__", "__uac__", "__request__.foo"});
    exportFileTemplate.setQuestionnaireType(1);

    Case caze = new Case();
    CaseFieldsHelper.setDummyCaseFields(caze);

    FulfilmentToProcess fulfilmentToProcess = new FulfilmentToProcess();
    fulfilmentToProcess.setExportFileTemplate(exportFileTemplate);
    fulfilmentToProcess.setCaze(caze);
    fulfilmentToProcess.setBatchId(UUID.fromString("6a127d58-c1cb-489c-a3f5-72014a0c32d6"));
    fulfilmentToProcess.setBatchQuantity(200);
    fulfilmentToProcess.setCorrelationId(TEST_CORRELATION_ID);
    fulfilmentToProcess.setOriginatingUser(TEST_ORIGINATING_USER);
    fulfilmentToProcess.setUacMetadata(TEST_UAC_METADATA);
    fulfilmentToProcess.setPersonalisation(null);

    UacQidDTO uacQidDTO = new UacQidDTO();
    uacQidDTO.setUac(UAC);
    uacQidDTO.setQid(QID);

    when(uacQidCache.getUacQidPair(anyInt())).thenReturn(uacQidDTO);

    // When
    underTest.process(fulfilmentToProcess);

    // Then
    ArgumentCaptor<ExportFileRow> exportFileRowArgumentCaptor =
        ArgumentCaptor.forClass(ExportFileRow.class);
    verify(exportFileRowRepository).save(exportFileRowArgumentCaptor.capture());
    ExportFileRow actualExportFileRow = exportFileRowArgumentCaptor.getValue();
    assertThat(actualExportFileRow.getPackCode()).isEqualTo(PACK_CODE);
    assertThat(actualExportFileRow.getExportFileDestination()).isEqualTo(EXPORT_FILE_DESTINATION);
    assertThat(actualExportFileRow.getRow()).isEqualTo("\"123\",\"" + UAC + "\",");

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getUac()).isEqualTo(UAC);
    assertThat(actualUacQidLink.getQid()).isEqualTo(QID);
    assertThat(actualUacQidLink.getCaze()).isEqualTo(caze);
    assertThat(actualUacQidLink.isActive()).isTrue();
    assertThat(actualUacQidLink.getMetadata()).isEqualTo(TEST_UAC_METADATA);

    ArgumentCaptor<EventDTO> eventCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("Export file generated with pack code " + PACK_CODE),
            eq(EventType.EXPORT_FILE),
            eventCaptor.capture(),
            any(OffsetDateTime.class));

    EventHeaderDTO actualHeader = eventCaptor.getValue().getHeader();
    Assertions.assertThat(actualHeader.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    Assertions.assertThat(actualHeader.getOriginatingUser()).isEqualTo(TEST_ORIGINATING_USER);
  }
}
