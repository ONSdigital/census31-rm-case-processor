package uk.gov.ons.census.caseprocessor.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.*;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentToProcessRepository;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.caseprocessor.service.FulfilmentRequestService;
import uk.gov.ons.census.caseprocessor.utils.Constants;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.ExportFileTemplate;
import uk.gov.ons.census.common.model.entity.SmsTemplate;

@ExtendWith(MockitoExtension.class)
public class FulfilmentRequestReceiverTest {
  private final String QID = "1234567890123456";

  @Mock private CaseService caseService;

  @Mock private FulfilmentToProcessRepository fulfilmentToProcessRepository;

  @Mock private EventLogger eventLogger;

  @Mock private FulfilmentRequestService fulfilmentRequestService;

  @InjectMocks FulfilmentRequestReceiver underTest;

  @Test
  void testReceiveMessage_print_fulfilment_success() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "PACK1");
    Message<byte[]> msg = buildMessage(event);

    Case parentCase = new Case();
    parentCase.setId(caseId);
    parentCase.setCaseType("HH");

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode("PACK1");

    Case returnedCase = new Case();
    returnedCase.setId(caseId);

    when(caseService.getCase(caseId)).thenReturn(parentCase);

    when(fulfilmentRequestService.getExportFileTemplate("PACK1")).thenReturn(Optional.of(eft));

    when(fulfilmentRequestService.getSmsTemplate("PACK1")).thenReturn(Optional.empty());

    when(fulfilmentRequestService.processPrintFulfilmentReceiver(event, parentCase))
        .thenReturn(returnedCase);

    underTest.receiveMessage(msg);

    verify(eventLogger)
        .logCaseEvent(
            eq(returnedCase),
            eq("Print fulfilment requested"),
            eq(EventType.PRINT_FULFILMENT),
            any(),
            eq(msg));

    verify(fulfilmentRequestService, never()).processFulfilmentForIndividual(any(), any(), any());
  }

  @Test
  void testReceiveMessage_sms_fulfilment_success() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "PACK1");
    Message<byte[]> msg = buildMessage(event);

    Case parentCase = new Case();
    parentCase.setId(caseId);
    parentCase.setCaseType("HH");

    SmsTemplate smsTemplate = setupSmsTemplate();

    SmsRequestEnriched smsRequestEnriched = buildSMSRequestEnriched(event);
    PayloadDTO payload = new PayloadDTO();
    payload.setSmsRequestEnriched(smsRequestEnriched);

    EventDTO smsRequestEnrichedEvent = new EventDTO();
    smsRequestEnrichedEvent.setHeader(event.getHeader());
    smsRequestEnrichedEvent.setPayload(payload);

    Case returnedCase = new Case();
    returnedCase.setId(caseId);

    when(caseService.getCase(caseId)).thenReturn(parentCase);

    when(fulfilmentRequestService.getExportFileTemplate("PACK1")).thenReturn(Optional.empty());

    when(fulfilmentRequestService.getSmsTemplate("PACK1")).thenReturn(Optional.of(smsTemplate));

    when(fulfilmentRequestService.validatePhoneNumber(any())).thenReturn(true);

    when(fulfilmentRequestService.processSMSRequestReceiver(eq(event), any(), eq(caseId)))
        .thenReturn(smsRequestEnrichedEvent);

    when(fulfilmentRequestService.processSMSFulfilmentService(
            eq(smsRequestEnrichedEvent), any(), eq(parentCase)))
        .thenReturn(returnedCase);

    underTest.receiveMessage(msg);

    verify(eventLogger)
        .logCaseEvent(
            eq(returnedCase),
            eq("SMS fulfilment request received"),
            eq(EventType.SMS_FULFILMENT),
            eq(smsRequestEnrichedEvent),
            eq(msg)); // TODO: Check warning and fix it.
  }

  @Test
  void testReceiveMessage_noTemplate_throws() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "PACK1");
    Message<byte[]> msg = buildMessage(event);

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode("PACK1");

    // Given
    when(fulfilmentRequestService.getSmsTemplate("PACK1")).thenReturn(Optional.empty());
    when(fulfilmentRequestService.getExportFileTemplate("PACK1")).thenReturn(Optional.empty());

    RuntimeException ex = assertThrows(RuntimeException.class, () -> underTest.receiveMessage(msg));

    assertTrue(ex.getMessage().contains("Invalid pack code on fulfilment request message"));

    verify(fulfilmentRequestService, never()).processPrintFulfilmentReceiver(any(), any());
    verify(fulfilmentRequestService, never()).processSMSRequestReceiver(any(), any(), any());

    verify(eventLogger, never())
        .logCaseEvent(
            (Case) any(),
            (String) any(),
            (EventType) any(),
            (EventDTO) any(),
            (Message<byte[]>) any());
  }

  @Test
  void testReceiveMessage_print_fulfilment_for_individual_success() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "P_OR_I1");
    Message<byte[]> msg = buildMessage(event);

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode("P_OR_I1");

    Case caze = new Case();
    caze.setId(caseId);
    caze.setCaseType("HH");

    Case childCase = new Case();
    childCase.setId(UUID.randomUUID());
    childCase.setCaseType("HI");

    when(caseService.getCase(caseId)).thenReturn(caze);

    when(fulfilmentRequestService.getExportFileTemplate("P_OR_I1")).thenReturn(Optional.of(eft));

    when(fulfilmentRequestService.getSmsTemplate("P_OR_I1")).thenReturn(Optional.empty());

    when(fulfilmentRequestService.processFulfilmentForIndividual(eq(event), eq(caze), any()))
        .thenReturn(childCase);

    when(fulfilmentRequestService.processPrintFulfilmentReceiver(
            any(EventDTO.class), eq(childCase)))
        .thenReturn(childCase);

    underTest.receiveMessage(msg);

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("Print fulfilment requested"),
            eq(EventType.PRINT_FULFILMENT),
            any(EventDTO.class),
            eq(msg));

    verify(eventLogger)
        .logCaseEvent(
            eq(childCase),
            eq("New case created"),
            eq(EventType.NEW_CASE),
            any(EventDTO.class),
            eq(msg));

    verify(eventLogger)
        .logCaseEvent(
            eq(childCase),
            eq("Print fulfilment requested"),
            eq(EventType.PRINT_FULFILMENT),
            any(EventDTO.class),
            eq(msg));
  }

  @Test
  void testReceiveMessage_sms_fulfilment_for_individual_success() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "P_OR_I1");
    Message<byte[]> msg = buildMessage(event);

    SmsRequestEnriched smsRequestEnriched = buildSMSRequestEnriched(event);
    PayloadDTO payload = new PayloadDTO();
    payload.setSmsRequestEnriched(smsRequestEnriched);

    EventDTO smsRequestEnrichedEvent = new EventDTO();
    smsRequestEnrichedEvent.setHeader(event.getHeader());
    smsRequestEnrichedEvent.setPayload(payload);

    SmsTemplate smsTemplate = setupSmsTemplate();

    Case caze = new Case();
    caze.setId(caseId);
    caze.setCaseType("HH");

    Case childCase = new Case();
    childCase.setId(UUID.randomUUID());
    childCase.setCaseType("HI");

    when(caseService.getCase(caseId)).thenReturn(caze);

    when(fulfilmentRequestService.getExportFileTemplate("P_OR_I1")).thenReturn(Optional.empty());

    when(fulfilmentRequestService.getSmsTemplate("P_OR_I1")).thenReturn(Optional.of(smsTemplate));

    when(fulfilmentRequestService.processFulfilmentForIndividual(eq(event), eq(caze), any()))
        .thenReturn(childCase);

    when(fulfilmentRequestService.validatePhoneNumber(any())).thenReturn(true);

    when(fulfilmentRequestService.processSMSRequestReceiver(
            eq(event), any(), eq(childCase.getId())))
        .thenReturn(smsRequestEnrichedEvent);

    when(fulfilmentRequestService.processSMSFulfilmentService(
            eq(smsRequestEnrichedEvent), any(), eq(childCase)))
        .thenReturn(childCase);

    underTest.receiveMessage(msg);

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("SMS fulfilment request received"),
            eq(EventType.SMS_FULFILMENT),
            any(EventDTO.class),
            eq(msg));

    verify(eventLogger)
        .logCaseEvent(
            eq(childCase),
            eq("New case created"),
            eq(EventType.NEW_CASE),
            any(EventDTO.class),
            eq(msg));

    verify(eventLogger)
        .logCaseEvent(
            eq(childCase),
            eq("SMS fulfilment request received"),
            eq(EventType.SMS_FULFILMENT),
            any(EventDTO.class),
            eq(msg));
  }

  @Test
  void testReceiveMessage_not_HH_throws() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "P_OR_I1");
    Message<byte[]> msg = buildMessage(event);

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode("P_OR_I1");

    Case caze = new Case();
    caze.setId(caseId);
    caze.setCaseType("XX");

    // Given

    when(fulfilmentRequestService.getSmsTemplate("P_OR_I1")).thenReturn(Optional.empty());
    when(fulfilmentRequestService.getExportFileTemplate("P_OR_I1")).thenReturn(Optional.of(eft));
    when(caseService.getCase(caseId)).thenReturn(caze);

    RuntimeException ex = assertThrows(RuntimeException.class, () -> underTest.receiveMessage(msg));

    assertTrue(
        ex.getMessage().contains("Case is not a House Hold Type on fulfilment request message"));

    verify(fulfilmentRequestService, never()).processPrintFulfilmentReceiver(any(), any());
    verify(fulfilmentRequestService, never()).processSMSRequestReceiver(any(), any(), any());

    verify(eventLogger, never())
        .logCaseEvent(
            (Case) any(),
            (String) any(),
            (EventType) any(),
            (EventDTO) any(),
            (Message<byte[]>) any());
  }

  @Test
  void testReceiveMessage_noCase_throws() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "PACK1");
    Message<byte[]> msg = buildMessage(event);

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode("PACK1");

    // Given
    when(fulfilmentRequestService.getSmsTemplate("PACK1")).thenReturn(Optional.empty());
    when(fulfilmentRequestService.getExportFileTemplate("PACK1")).thenReturn(Optional.of(eft));

    when(caseService.getCase(caseId)).thenThrow(new RuntimeException("Case not found"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> underTest.receiveMessage(msg));

    assertTrue(ex.getMessage().contains("Case not found"));

    verify(fulfilmentRequestService, never()).processPrintFulfilmentReceiver(any(), any());
    verify(fulfilmentRequestService, never()).processSMSRequestReceiver(any(), any(), any());

    verify(eventLogger, never())
        .logCaseEvent(
            (Case) any(),
            (String) any(),
            (EventType) any(),
            (EventDTO) any(),
            (Message<byte[]>) any());
  }

  private Message<byte[]> buildMessage(EventDTO event) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    byte[] payload = mapper.writeValueAsBytes(event);
    return MessageBuilder.withPayload(payload).build();
  }

  private EventDTO buildEvent(UUID caseId, String packCode) {
    EventHeaderDTO header = new EventHeaderDTO();
    header.setMessageId(UUID.randomUUID());
    header.setCorrelationId(UUID.randomUUID());
    header.setOriginatingUser("test-user");
    header.setMessageType(EventType.FULFILMENT_REQUEST);
    header.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    header.setTopic("topic");

    Contact contact = new Contact();
    contact.setTelNo("07788990011");

    FulfilmentRequest fr = new FulfilmentRequest();
    fr.setCaseId(caseId);
    fr.setFulfilmentCode(packCode);
    fr.setContact(contact);

    PayloadDTO payload = new PayloadDTO();
    payload.setFulfilmentRequest(fr);

    EventDTO event = new EventDTO();
    event.setHeader(header);
    event.setPayload(payload);
    return event;
  }

  private SmsTemplate setupSmsTemplate() {
    String[] template =
        new String[] {
          "__pack_code__",
          "__uac__",
          "__qid__",
          "__caseref__",
          "ADDRESS_LINE1",
          "ADDRESS_LINE2",
          "ADDRESS_LINE3",
          "TOWN_NAME",
          "POSTCODE"
        };

    // Mock SMS Template
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("PACK1");
    smsTemplate.setQuestionnaireType(10);
    smsTemplate.setTemplate(template);

    return smsTemplate;
  }

  private SmsRequestEnriched buildSMSRequestEnriched(EventDTO event) {

    // Mock UAC/QID pair
    UacQidDTO uacQid = new UacQidDTO();
    uacQid.setUac("UAC123");
    uacQid.setQid("QID123");

    FulfilmentRequest fulfilmentRequest = event.getPayload().getFulfilmentRequest();
    SmsRequestEnriched smsRequestEnriched = new SmsRequestEnriched();
    smsRequestEnriched.setPhoneNumber(fulfilmentRequest.getContact().getTelNo());
    smsRequestEnriched.setPersonalisation(fulfilmentRequest.getContact().toMap());
    smsRequestEnriched.setUac(uacQid.getUac());
    smsRequestEnriched.setQid(uacQid.getQid());

    return smsRequestEnriched;
  }
}
