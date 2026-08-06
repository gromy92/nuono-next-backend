package com.nuono.next.noon;

import java.net.http.HttpResponse;

/** Creates a Noon HTTP failure while discarding the raw Retry-After header value. */
final class NoonHttpFailureFactory {
    private NoonHttpFailureFactory() {
    }

    static NoonHttpException from(HttpResponse<?> response, String body, String requestPath) {
        return new NoonHttpException(
                response.statusCode(),
                body,
                requestPath,
                NoonRetryAfterParser.parse(
                        response.headers().firstValue("Retry-After").orElse(null)
                )
        );
    }
}
