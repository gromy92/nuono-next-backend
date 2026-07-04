package com.nuono.next.product;

final class ProductNoonCredential {

    private final String noonUser;
    private final String noonPassword;
    private final String noonEmailAuthCode;
    private final String noonCookie;
    private final String projectCode;

    ProductNoonCredential(
            String noonUser,
            String noonPassword,
            String noonEmailAuthCode,
            String noonCookie,
            String projectCode
    ) {
        this.noonUser = noonUser;
        this.noonPassword = noonPassword;
        this.noonEmailAuthCode = noonEmailAuthCode;
        this.noonCookie = noonCookie;
        this.projectCode = projectCode;
    }

    String getNoonUser() {
        return noonUser;
    }

    String getNoonPassword() {
        return noonPassword;
    }

    String getNoonEmailAuthCode() {
        return noonEmailAuthCode;
    }

    String getNoonCookie() {
        return noonCookie;
    }

    String getProjectCode() {
        return projectCode;
    }
}
