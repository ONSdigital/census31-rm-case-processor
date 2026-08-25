package uk.gov.ons.census.caseprocessor.testutils;

import java.util.Map;
import java.util.UUID;

public class TestConstants {
  public static final String OUR_PUBSUB_PROJECT = "our-project";
  public static final String OUTBOUND_UAC_SUBSCRIPTION = "event_uac-update_rh";
  public static final String OUTBOUND_CASE_SUBSCRIPTION = "event_case-update_rh";
  public static final String NEW_CASE_TOPIC = "event_new-case";
  public static final String EMAIL_CONFIRMATION_TOPIC = "rm-internal-email-confirmation";
  public static final String FULFILMENT_REQUEST_TOPIC = "event_fulfilment-request";

  public static final UUID TEST_CORRELATION_ID = UUID.randomUUID();
  public static final String TEST_ORIGINATING_USER = "foo@bar.com";
  public static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");
}
