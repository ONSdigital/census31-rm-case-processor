package uk.gov.ons.census.caseprocessor.service;

import static uk.gov.ons.census.caseprocessor.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.census.caseprocessor.utils.Constants.TEMPLATE_UAC_KEY;

import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.ons.census.caseprocessor.cache.UacQidCache;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.*;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentToProcessRepository;
import uk.gov.ons.census.caseprocessor.model.repository.SmsTemplateRepository;
import uk.gov.ons.census.caseprocessor.utils.Constants;
import uk.gov.ons.census.common.model.entity.*;

@Service
public class FulfilmentRequestService {
  private final UacQidCache uacQidCache;
  private final CaseRepository caseRepository;
  private final SmsTemplateRepository smsTemplateRepository;
  private final ExportFileTemplateRepository exportFileTemplateRepository;
  private final UacService uacService;
  private final CaseService caseService;
  private final FulfilmentToProcessRepository fulfilmentToProcessRepository;
  private static final Logger log = LoggerFactory.getLogger(FulfilmentRequestService.class);
  private final MessageSender messageSender;

  public FulfilmentRequestService(
      UacQidCache uacQidCache,
      CaseRepository caseRepository,
      SmsTemplateRepository smsTemplateRepository,
      UacService uacService,
      CaseService caseService,
      FulfilmentToProcessRepository fulfilmentToProcessRepository,
      ExportFileTemplateRepository exportFileTemplateRepository,
      MessageSender messageSender) {
    this.uacQidCache = uacQidCache;
    this.caseRepository = caseRepository;
    this.smsTemplateRepository = smsTemplateRepository;
    this.uacService = uacService;
    this.caseService = caseService;
    this.fulfilmentToProcessRepository = fulfilmentToProcessRepository;
    this.exportFileTemplateRepository = exportFileTemplateRepository;
    this.messageSender = messageSender;
  }

  public Case processPrintFulfilmentReceiver(EventDTO event, UUID caseId) {
      if (isMessageAlreadyExists(event)) {
      return null;
    }
    FulfilmentRequest printFulfilmentRequest = event.getPayload().getFulfilmentRequest();
    Case caze = caseService.getCase(caseId);

    ExportFileTemplate exportFileTemplate =
        getAllowedPrintTemplate(printFulfilmentRequest.getFulfilmentCode(), caze);

    FulfilmentToProcess fulfilmentToProcess = new FulfilmentToProcess();
    fulfilmentToProcess.setExportFileTemplate(exportFileTemplate);
    fulfilmentToProcess.setCaze(caze);
    fulfilmentToProcess.setCorrelationId(event.getHeader().getCorrelationId());
    fulfilmentToProcess.setMessageId(event.getHeader().getMessageId());
    fulfilmentToProcess.setOriginatingUser(event.getHeader().getOriginatingUser());
    fulfilmentToProcess.setPersonalisation(printFulfilmentRequest.getContact().toMap());

    fulfilmentToProcessRepository.saveAndFlush(fulfilmentToProcess);

    return caze;
  }

  public EventDTO processSMSRequestReceiver(
      EventDTO fulfilmentRequestEvent, String smsRequestEnrichedTopic, UUID caseId) {
    EventHeaderDTO fulfilmentRequestHeader = fulfilmentRequestEvent.getHeader();
    FulfilmentRequest fulfilmentRequest =
        fulfilmentRequestEvent.getPayload().getFulfilmentRequest();
    fulfilmentRequest.setCaseId(caseId);

    SmsTemplate smsTemplate =
        smsTemplateRepository
            .findById(String.valueOf(caseId))
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "SMS Template not found: " + fulfilmentRequest.getFulfilmentCode()));

    if (!caseRepository.existsById(caseId)) {
      throw new RuntimeException("Case not found with ID: " + caseId);
    }

    Optional<UacQidDTO> newUacQidPair;
    try {
      newUacQidPair =
          fetchNewUacQidPairIfRequired(
              smsTemplate.getQuestionnaireType(), smsTemplate.getTemplate());
    } catch (IllegalArgumentException illegalArgumentException) {
      log.atError()
          .setMessage("Failed to fetch UAC QID pair for SMS request event")
          .addKeyValue("messageId", fulfilmentRequestHeader.getMessageId())
          .addKeyValue("correlationId", fulfilmentRequestHeader.getCorrelationId())
          .addKeyValue("caseId", fulfilmentRequest.getCaseId())
          .addKeyValue("packCode", smsTemplate.getPackCode())
          .log();
      throw new RuntimeException(
          String.format(
              "Failed to fetch UAC/QID pair for SMS request event for pack code: %s",
              smsTemplate.getPackCode()),
          illegalArgumentException);
    }

    EventDTO smsRequestEnrichedEvent =
        buildSmsRequestEnrichedEvent(
            fulfilmentRequest, fulfilmentRequestHeader, newUacQidPair, smsRequestEnrichedTopic);

    return smsRequestEnrichedEvent;
  }

  public Case processSMSFulfilmentReceiptService(
      EventDTO smsRequestEnrichedEvent, String smsRequestEnrichedTopic) {
    SmsRequestEnriched smsRequestEnriched =
        smsRequestEnrichedEvent.getPayload().getSmsRequestEnriched();

    Case caze = caseService.getCase(smsRequestEnriched.getCaseId());

    uacService.createLinkAndEmitNewUacQid(
        caze,
        smsRequestEnriched.getUac(),
        smsRequestEnriched.getQid(),
        null,
        smsRequestEnrichedEvent.getHeader().getCorrelationId(),
        smsRequestEnrichedEvent.getHeader().getOriginatingUser());

    messageSender.sendMessage(smsRequestEnrichedTopic, smsRequestEnrichedEvent);
    return caze;
  }

  private ExportFileTemplate getAllowedPrintTemplate(String packCode, Case caze) {
    Survey survey = caze.getCollectionExercise().getSurvey();

    for (FulfilmentSurveyExportFileTemplate fulfilmentSurveyExportFileTemplate :
        survey.getFulfilmentExportFileTemplates()) {
      if (fulfilmentSurveyExportFileTemplate
          .getExportFileTemplate()
          .getPackCode()
          .equals(packCode)) {
        return fulfilmentSurveyExportFileTemplate.getExportFileTemplate();
      }
    }

    throw new RuntimeException(
        String.format(
            "Pack code %s is not allowed as a fulfilment on survey %s",
            packCode, survey.getName()));
  }

  public boolean validatePhoneNumber(String phoneNumber) {
    // Remove valid leading country code or 0
    String sanitisedPhoneNumber = phoneNumber.replaceFirst("^(44|0044|\\+44|0)", "");

    // The sanitized number must then be 10 digits, starting with 7
    return sanitisedPhoneNumber.length() == 10 && sanitisedPhoneNumber.matches("^7[0-9]+$");
  }

  public Optional<UacQidDTO> fetchNewUacQidPairIfRequired(
      Integer questionnaireType, String... smsTemplate) {
    if (doesTemplateRequireNewUacQid(smsTemplate)) {
      if (questionnaireType == null) {
        throw new IllegalArgumentException("SMS template is missing questionnaire type");
      }
      return Optional.of(uacQidCache.getUacQidPair(questionnaireType));
    }
    return Optional.empty();
  }

  public static boolean doesTemplateRequireNewUacQid(String... template) {
    return CollectionUtils.containsAny(
        Arrays.asList(template), List.of(TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY));
  }

    public Case processFulfilmentForIndividual(EventDTO event, Optional<SmsTemplate> smsTemplate , Optional<ExportFileTemplate> printTemplate) {

        EventHeaderDTO fulfilmentRequestHeader = event.getHeader();
        FulfilmentRequest fulfilmentRequest =
                event.getPayload().getFulfilmentRequest();

        if (isMessageAlreadyExists(event)) {
            return null;
        }

        Case caze = caseService.getCase(fulfilmentRequest.getCaseId());
        Case childCase = createChildCase(caze);
        childCase.setCaseType("HI");
        childCase.setRefusalReceived(null);
        childCase.setReceiptReceived(false);
        childCase.setId(UUID.randomUUID());

        Case newCase = caseRepository.saveAndFlush(childCase);

        //should trigger the emitCaseUpdate or trigger the New Case Event?
        caseService.emitCaseUpdate(
                newCase, event.getHeader().getCorrelationId(), event.getHeader().getOriginatingUser());


        return newCase;



    }

    private boolean isMessageAlreadyExists(EventDTO event){
        if (fulfilmentToProcessRepository.existsByMessageId(event.getHeader().getMessageId())) {
            log.atInfo()
                    .setMessage(
                            "Received duplicate fulfilment message ID, ignoring and acking the duplicate message")
                    .addKeyValue("correlationId", event.getHeader().getCorrelationId())
                    .addKeyValue("messageId", event.getHeader().getMessageId())
                    .log();
            return true;
        }
        return false;
    }

    private EventDTO buildSmsRequestEnrichedEvent(
      FulfilmentRequest smsRequest,
      EventHeaderDTO fulfilmentRequestHeader,
      Optional<UacQidDTO> uacQidPair,
      String smsRequestEnrichedTopic) {
    SmsRequestEnriched smsRequestEnriched = new SmsRequestEnriched();
    smsRequestEnriched.setCaseId(smsRequest.getCaseId());
    smsRequestEnriched.setPhoneNumber(smsRequest.getContact().getTelNo());
    smsRequestEnriched.setPackCode(smsRequest.getFulfilmentCode());
    smsRequestEnriched.setScheduled(false);
    smsRequestEnriched.setPersonalisation(smsRequest.getContact().toMap());

    if (uacQidPair.isPresent()) {
      smsRequestEnriched.setUac(uacQidPair.get().getUac());
      smsRequestEnriched.setQid(uacQidPair.get().getQid());
    }

    EventHeaderDTO enrichedEventHeader = new EventHeaderDTO();
    enrichedEventHeader.setMessageId(UUID.randomUUID());
    enrichedEventHeader.setCorrelationId(fulfilmentRequestHeader.getCorrelationId());
    enrichedEventHeader.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    enrichedEventHeader.setChannel(fulfilmentRequestHeader.getChannel());
    enrichedEventHeader.setSource(fulfilmentRequestHeader.getSource());
    enrichedEventHeader.setOriginatingUser(fulfilmentRequestHeader.getOriginatingUser());
    enrichedEventHeader.setTopic(smsRequestEnrichedTopic);
    enrichedEventHeader.setDateTime(OffsetDateTime.now());
    enrichedEventHeader.setMessageType(EventType.FULFILMENT_SMS_CONFIRMATION);

    PayloadDTO enrichedPayload = new PayloadDTO();
    enrichedPayload.setSmsRequestEnriched(smsRequestEnriched);

    EventDTO smsRequestEnrichedEvent = new EventDTO();
    smsRequestEnrichedEvent.setHeader(enrichedEventHeader);
    smsRequestEnrichedEvent.setPayload(enrichedPayload);
    return smsRequestEnrichedEvent;
  }

  public Optional<SmsTemplate> getSmsTemplate(String packCode) {
    Optional<SmsTemplate> smsTemplate = smsTemplateRepository.findById(packCode);
    return smsTemplate;
  }

  public Optional<ExportFileTemplate> getExportFileTemplate(String packCode) {
    Optional<ExportFileTemplate> exportFileTemplate =
        exportFileTemplateRepository.findById(packCode);
    return exportFileTemplate;
  }

  private Case createChildCase(Case caze){
      Case childCase = new Case();
      childCase.setCollectionExercise(caze.getCollectionExercise());
      childCase.setInvalid(caze.isInvalid());
      childCase.setAddressLevel(caze.getAddressLevel());
      childCase.setAddressType(caze.getAddressType());
      childCase.setUacQidLinks(caze.getUacQidLinks());//TODO: Check should this copied to child case
      childCase.setAbpCode(caze.getAbpCode());
      childCase.setAddressLine1(caze.getAddressLine1());
      childCase.setAddressLine2(caze.getAddressLine2());
      childCase.setAddressLine3(caze.getAddressLine3());
      childCase.setCaseRef(caze.getCaseRef());
      childCase.setCeExpectedCapacity(caze.getCeExpectedCapacity());
      childCase.setEstabType(caze.getEstabType());
      childCase.setEstabUprn(caze.getEstabUprn());
      childCase.setFieldCoordinatorId(caze.getFieldCoordinatorId());
      childCase.setFieldOfficerId(caze.getFieldOfficerId());
      childCase.setHtcDigital(caze.getHtcDigital());
      childCase.setHtcWillingness(caze.getHtcWillingness());
      childCase.setLad(caze.getLad());
      childCase.setLatitude(caze.getLatitude());
      childCase.setLongitude(caze.getLongitude());
      childCase.setLsoa(caze.getLsoa());
      childCase.setMsoa(caze.getMsoa());
      childCase.setOa(caze.getOa());
      childCase.setOrganisationName(caze.getOrganisationName());
      childCase.setPrintBatch(caze.getPrintBatch());
      childCase.setPostcode(caze.getPostcode());
      childCase.setRegion(caze.getRegion());
      childCase.setSecureEstablishment(caze.isSecureEstablishment());
      childCase.setSurveyLaunched(caze.isSurveyLaunched());
      childCase.setSecretSequenceNumber(caze.getSecretSequenceNumber());
      childCase.setTownName(caze.getTownName());
      childCase.setTreatmentCode(caze.getTreatmentCode());
      childCase.setUprn(caze.getUprn());
      childCase.setId(UUID.randomUUID());
      return childCase;
  }
}
