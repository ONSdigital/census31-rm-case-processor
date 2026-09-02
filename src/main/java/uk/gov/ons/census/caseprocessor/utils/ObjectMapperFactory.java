package uk.gov.ons.census.caseprocessor.utils;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ObjectMapperFactory {
  public static ObjectMapper objectMapper() {
    // JavaTimeModule and Jdk8Module are built into Jackson 3 databind - nothing to register.
    JsonMapper mapper =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    return mapper;
  }
}
