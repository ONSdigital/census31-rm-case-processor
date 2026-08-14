package uk.gov.ons.census.caseprocessor.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;

@Component
public class PubSubHelper {
  private final PubSubTemplate pubSubTemplate;

  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  public PubSubHelper(PubSubTemplate pubSubTemplate) {
    this.pubSubTemplate = pubSubTemplate;
  }

  public void publishAndConfirm(String topic, EventDTO payload) {
    try {
      pubSubTemplate.publish(topic, objectMapper.writeValueAsBytes(payload)).get();
    } catch (ExecutionException e) {
      throw new RuntimeException("Error publishing message to PubSub topic ", e);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error mapping event to JSON", e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
