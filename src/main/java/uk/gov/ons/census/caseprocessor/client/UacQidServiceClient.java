package uk.gov.ons.census.caseprocessor.client;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.census.caseprocessor.model.dto.UacQidDTO;

@Component
public class UacQidServiceClient {

  @Value("${uacservice.connection.scheme}")
  private String scheme;

  @Value("${uacservice.connection.host}")
  private String host;

  @Value("${uacservice.connection.port}")
  private String port;

  private static final Logger log = LoggerFactory.getLogger(UacQidServiceClient.class);

  public List<UacQidDTO> getUacQids(int questionnaireType, int numberToCreate) {
    RestTemplate restTemplate = new RestTemplate();

    UriComponents uriComponents =
        createUriComponents(questionnaireType, numberToCreate, "multiple_qids");
    ResponseEntity<UacQidDTO[]> responseEntity =
        restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, null, UacQidDTO[].class);

    return Arrays.asList(responseEntity.getBody());
  }

  private UriComponents createUriComponents(
      int questionnaireType, int numberToCreate, String path) {
    return UriComponentsBuilder.newInstance()
        .scheme(scheme)
        .host(host)
        .port(port)
        .path(path)
        .queryParam("questionnaireType", questionnaireType)
        .queryParam("numberToCreate", numberToCreate)
        .build()
        .encode();
  }

  private UriComponents createUriComponents(Integer questionnaireType) {
    return UriComponentsBuilder.newInstance()
        .scheme(scheme)
        .host(host)
        .port(port)
        .queryParam("questionnaireType", questionnaireType)
        .build()
        .encode();
  }

  public UacQidDTO generateUacQid(Integer questionnaireType) {
    log.atError()
        .setMessage("HTTP call to generate a UAC and QID")
        .addKeyValue("method", "generateUacQid")
        .log();

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents(questionnaireType);
    ResponseEntity<UacQidDTO> responseEntity =
        restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, null, UacQidDTO.class);
    return responseEntity.getBody();
  }
}
