package com.nuono.next.product;

final class ProductNoonCredential {

    private final String noonUser;
    private final String noonPassword;
    private final String noonCookie;
    private final String projectCode;

    ProductNoonCredential(String noonUser, String noonPassword, String noonCookie, String projectCode) {
        this.noonUser = noonUser;
        this.noonPassword = noonPassword;
        this.noonCookie = noonCookie;
        this.projectCode = projectCode;
    }

    String getNoonUser() {
        return noonUser;
    }

    String getNoonPassword() {
        return noonPassword;
    }

    String getNoonCookie() {
        return noonCookie;
    }

    String getProjectCode() {
        return projectCode;
    }
}
