package uk.gov.ons.census.caseprocessor.config;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;

public class DefaultListenerSupport
    implements org.springframework.core.retry.RetryListener,
        org.springframework.retry.RetryListener {

  @Override
  public <T, E extends Throwable> void close(
      RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
    org.springframework.retry.RetryListener.super.close(context, callback, throwable);
  }

  @Override
  public <T, E extends Throwable> void onError(
      RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
    org.springframework.retry.RetryListener.super.onError(context, callback, throwable);
  }

  @Override
  public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
    return org.springframework.retry.RetryListener.super.open(context, callback);
  }
}
