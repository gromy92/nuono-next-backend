package com.nuono.next.noon;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class NoonHardDeadlineHttpClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private NoonHardDeadlineHttpClient() {
    }

    static <T> HttpResponse<T> send(
            HttpClient client,
            HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler
    ) throws IOException, InterruptedException {
        long timeoutMillis = Math.max(1L, request.timeout().orElse(DEFAULT_TIMEOUT).toMillis());
        CompletableFuture<HttpResponse<T>> future = client.sendAsync(request, bodyHandler);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new HttpTimeoutException("Noon request exceeded hard timeout of " + timeoutMillis + " ms");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw exception;
        } catch (CancellationException exception) {
            throw new IOException("Noon request was cancelled", exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception.getCause());
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw (InterruptedException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IOException("Noon request failed", cause);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
