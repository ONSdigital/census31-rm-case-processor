package uk.gov.ons.census.caseprocessor.cache;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uk.gov.ons.census.caseprocessor.client.UacQidServiceClient;
import uk.gov.ons.census.caseprocessor.model.dto.UacQidDTO;

@Component
public class UacQidCache {

  private static final Logger log = LoggerFactory.getLogger(UacQidCache.class);

  private final UacQidServiceClient uacQidServiceClient;

  @Value("${uacservice.uacqid-cache-min}")
  private int cacheMin;

  @Value("${uacservice.uacqid-fetch-count}")
  private int cacheFetch;

  @Value("${uacservice.uacqid-get-timeout}")
  private long uacQidGetTimout;

  private static final Executor executor = Executors.newFixedThreadPool(8);

  private final Map<Integer, BlockingQueue<UacQidDTO>> uacQidLinkQueueMap =
      new ConcurrentHashMap<>();
  private final Set<Integer> isToppingUpQueue = ConcurrentHashMap.newKeySet();

  public UacQidCache(UacQidServiceClient uacQidServiceClient) {
    this.uacQidServiceClient = uacQidServiceClient;
  }

  public UacQidDTO getUacQidPair(int questionnaireType) {
    uacQidLinkQueueMap.computeIfAbsent(questionnaireType, key -> new LinkedBlockingDeque<>());

    try {
      topUpQueue(questionnaireType);
      UacQidDTO uacQidDTO =
          uacQidLinkQueueMap.get(questionnaireType).poll(uacQidGetTimout, TimeUnit.SECONDS);

      if (uacQidDTO == null) {
        // The cache topper upper is executed in a separate thread, which can fail if uacqid api
        // down
        // So check we get a non null result otherwise throw a RunTimeException to re-enqueue msg
        throw new RuntimeException(
            "Timeout getting UacQidDTO for questionnaireType :" + questionnaireType);
      }

      // Put the UAC-QID back into the cache if the transaction rolls back
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                  uacQidLinkQueueMap.get(questionnaireType).add(uacQidDTO);
                }
              }
            });
      }

      return uacQidDTO;
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void topUpQueue(int questionnaireType) {
    synchronized (isToppingUpQueue) {
      if (!isToppingUpQueue.contains(questionnaireType)
          && uacQidLinkQueueMap.get(questionnaireType).size() < cacheMin) {
        isToppingUpQueue.add(questionnaireType);
      } else {
        return;
      }
    }
    log.atInfo()
        .setMessage("Topping up UAC-QID cache")
        .addKeyValue("questionnaireType", questionnaireType)
        .log();

    executor.execute(
        () -> {
          try {
            uacQidLinkQueueMap
                .get(questionnaireType)
                .addAll(uacQidServiceClient.getUacQids(questionnaireType, cacheFetch));
          } finally {
            isToppingUpQueue.remove(questionnaireType);
          }
        });
  }
}
