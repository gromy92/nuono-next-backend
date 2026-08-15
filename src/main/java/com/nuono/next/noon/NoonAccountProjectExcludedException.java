package com.nuono.next.noon;

/** A locally bound Project that is absent from the authenticated Noon account scope. */
final class NoonAccountProjectExcludedException extends IllegalStateException {
    private final String projectCode;

    NoonAccountProjectExcludedException(String projectCode) {
        super("Noon 账号不包含当前项目：" + projectCode);
        this.projectCode = projectCode;
    }

    String getProjectCode() {
        return projectCode;
    }
}
