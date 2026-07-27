package com.nuono.next.noonauth.gateway;

public enum NoonTransientErrorType {
    NETWORK_EOF,
    CONNECT_TIMEOUT,
    HTTP_408,
    HTTP_500,
    HTTP_502,
    HTTP_503,
    HTTP_504
}
