package uk.gov.ons.census.caseprocessor.cache;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uk.gov.ons.census.caseprocessor.client.UacQidServiceClient;
import uk.gov.ons.census.caseprocessor.model.dto.UacQidDTO;

@Component
public class UacQidCache {
  private final UacQidServiceClient uacQidServiceClient;

  @Value("${uacservice.uacqid-cache-min}")
  private int cacheMin;

  @Value("${uacservice.uacqid-fetch-count}")
  private int cacheFetch;

  @Value("${uacservice.uacqid-get-timeout}")
  private long uacQidGetTimout;

  private static final Executor executor = Executors.newFixedThreadPool(8);

  private BlockingQueue<UacQidDTO> uacQidLinkCache = new LinkedBlockingDeque<>();
  private boolean isToppingUpCache = false;
  private final Object lock = new Object();

  public UacQidCache(UacQidServiceClient uacQidServiceClient) {
    this.uacQidServiceClient = uacQidServiceClient;
  }

  public UacQidDTO getUacQidPair() {
    try {
      topUpCache();
      UacQidDTO uacQidDTO = uacQidLinkCache.poll(uacQidGetTimout, TimeUnit.SECONDS);

      if (uacQidDTO == null) {
        // The cache topper upper is executed in a separate thread, which can fail if uacqid api
        // down
        // So check we get a non null result otherwise throw a RunTimeException to re-enqueue msg
        throw new RuntimeException("Timeout getting UacQidDTO");
      }

      // Put the UAC-QID back into the cache if the transaction rolls back
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                  uacQidLinkCache.add(uacQidDTO);
                }
              }
            });
      }

      return uacQidDTO;
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void topUpCache() {
    // We use synchronised on an empty object instead of the isToppingUpCache bool because it's
    // bad practice to use it on a boolean literal.
    synchronized (lock) {
      if (!isToppingUpCache && uacQidLinkCache.size() < cacheMin) {
        isToppingUpCache = true;
      } else {
        return;
      }
      executor.execute(
          () -> {
            try {
              uacQidLinkCache.addAll(uacQidServiceClient.getUacQids(cacheFetch));
            } finally {
              isToppingUpCache = false;
            }
          });
    }
  }
}
