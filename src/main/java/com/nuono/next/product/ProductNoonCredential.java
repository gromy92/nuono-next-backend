package com.nuono.next.product;

final class ProductNoonCredential {

    private final String noonUser;
    private final String noonCookie;
    private final String projectCode;

    ProductNoonCredential(
            String noonUser,
            String noonCookie,
            String projectCode
    ) {
        this.noonUser = noonUser;
        this.noonCookie = noonCookie;
        this.projectCode = projectCode;
    }

    String getNoonUser() {
        return noonUser;
    }

    String getNoonCookie() {
        return noonCookie;
    }

    String getProjectCode() {
        return projectCode;
    }
}
