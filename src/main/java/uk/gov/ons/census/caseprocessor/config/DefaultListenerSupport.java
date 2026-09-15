package uk.gov.ons.census.caseprocessor.config;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/* Bridge both listener contracts so one bean supports migrated runtime wiring and legacy @Retryable
listeners. */
public class DefaultListenerSupport
    implements org.springframework.core.retry.RetryListener, RetryListener {

  @Override
  public <T, E extends Throwable> void close(
      RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
    RetryListener.super.close(context, callback, throwable);
  }

  @Override
  public <T, E extends Throwable> void onError(
      RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
    RetryListener.super.onError(context, callback, throwable);
  }

  @Override
  public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
    return RetryListener.super.open(context, callback);
  }
}
