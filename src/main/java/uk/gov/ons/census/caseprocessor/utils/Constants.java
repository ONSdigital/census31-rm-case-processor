package uk.gov.ons.census.caseprocessor.utils;

import java.util.Set;

public class Constants {
  public static final String OUTBOUND_EVENT_SCHEMA_VERSION = "0.5.0";
  public static final Set<String> ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS =
      Set.of("v0.3_RELEASE", "0.4.0-DRAFT", "0.4.0", "0.5.0-DRAFT", "0.5.0", "0.6.0-DRAFT");

  public static final String REQUEST_PERSONALISATION_PREFIX = "__request__.";

  public static final String TEMPLATE_UAC_KEY = "__uac__";
  public static final String TEMPLATE_QID_KEY = "__qid__";
}
