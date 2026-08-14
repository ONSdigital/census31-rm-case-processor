package uk.gov.ons.census.caseprocessor.messaging;

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
import uk.gov.ons.census.caseprocessor.utils.PubSubHelper;
import uk.gov.ons.census.common.model.entity.*;

@ExtendWith(MockitoExtension.class)
public class FulfilmentRequestReceiverTest {
  private final String QID = "1234567890123456";

  @Mock private CaseService caseService;

  @Mock private FulfilmentToProcessRepository fulfilmentToProcessRepository;

  @Mock private EventLogger eventLogger;

  @Mock private FulfilmentRequestService fulfilmentRequestService;

  @Mock private PubSubHelper pubSubHelper;

  @InjectMocks FulfilmentRequestReceiver underTest;

  private final String smsRequestEnrichedTopic = "sms-topic";

  @Test
  void testReceiveMessage_printFulfilment_export_success() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "PACK1");
    Message<byte[]> msg = buildMessage(event);

    SmsTemplate smsTemplate = setupTemplate();

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode("PACK1");

    Case caze = new Case();
    caze.setId(caseId);

    when(fulfilmentRequestService.getExportFileTemplate("PACK1")).thenReturn(Optional.of(eft));

    when(fulfilmentRequestService.getSmsTemplate("PACK1")).thenReturn(Optional.empty());

    when(fulfilmentRequestService.processPrintFulfilmentReceiver(any())).thenReturn(caze);

    underTest.receiveMessage(msg);

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("Print fulfilment requested"),
            eq(EventType.PRINT_FULFILMENT),
            any(),
            eq(msg));
  }

  @Test
  void testReceiveMessage_printFulfilment_sms_success() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "PACK1");
    Message<byte[]> msg = buildMessage(event);

    SmsRequestEnriched smsRequestEnriched = buildSMSRequestEnriched(event);
    PayloadDTO payload = new PayloadDTO();
    payload.setSmsRequestEnriched(smsRequestEnriched);

    EventDTO smsRequestEnrichedEvent = new EventDTO();
    smsRequestEnrichedEvent.setHeader(event.getHeader());
    smsRequestEnrichedEvent.setPayload(payload);

    SmsTemplate smsTemplate = setupTemplate();

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode("PACK1");

    Case caze = new Case();
    caze.setId(caseId);

    // Given

    when(fulfilmentRequestService.getExportFileTemplate("PACK1")).thenReturn(Optional.empty());

    when(fulfilmentRequestService.getSmsTemplate("PACK1")).thenReturn(Optional.of(smsTemplate));

    when(fulfilmentRequestService.processSMSRequestReceiver(any(), any()))
        .thenReturn(smsRequestEnrichedEvent);

    when(fulfilmentRequestService.validatePhoneNumber(any())).thenReturn(true);

    when(fulfilmentRequestService.processSMSFulfilmentReceiptService(smsRequestEnrichedEvent))
        .thenReturn(caze);

    underTest.receiveMessage(msg);

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("SMS fulfilment request received"),
            eq(EventType.SMS_FULFILMENT),
            eq(smsRequestEnrichedEvent),
            (Message<byte[]>) any()); // TODO: Check warning and fix it.
  }

  @Test
  void testReceiveMessage_noTemplate_noAction() throws Exception {
    UUID caseId = UUID.randomUUID();
    EventDTO event = buildEvent(caseId, "PACK1");
    Message<byte[]> msg = buildMessage(event);

    ExportFileTemplate eft = new ExportFileTemplate();
    eft.setPackCode("PACK1");

    SmsTemplate smsTemplate = setupTemplate();

    // Given
    when(fulfilmentRequestService.getSmsTemplate("PACK1")).thenReturn(Optional.empty());
    when(fulfilmentRequestService.getExportFileTemplate("PACK1")).thenReturn(Optional.empty());

    underTest.receiveMessage(msg);

    verify(fulfilmentRequestService, never()).processPrintFulfilmentReceiver(any());
    verify(fulfilmentRequestService, never()).processSMSRequestReceiver(any(), any());
    verify(fulfilmentRequestService, never()).processSMSRequestReceiver(any(), any());

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

    Contact contact = new Contact();
    contact.setTitle("Mr.");
    contact.setForename("Joe");
    contact.setSurname("Blogger");
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

  private SmsTemplate setupTemplate() {
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
    smsRequestEnriched.setContact(fulfilmentRequest.getContact());

    return smsRequestEnriched;
  }
}
