package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.util.StringUtils;

final class NoonProjectSessionValidator {

    private NoonProjectSessionValidator() {
    }

    static boolean matchesTargetProject(JsonNode whoami, String targetProjectCode) {
        String normalizedTarget = normalizeProjectCode(targetProjectCode);
        if (!StringUtils.hasText(normalizedTarget) || whoami == null || !whoami.isObject()) {
            return false;
        }
        Set<String> confirmedProjectCodes = whoamiProjectCodes(whoami);
        return confirmedProjectCodes.size() == 1 && confirmedProjectCodes.contains(normalizedTarget);
    }

    static boolean validatesProjectSession(
            JsonNode whoami,
            String expectedEmail,
            String targetProjectCode,
            NoonSessionGateway.ProjectSessionCookie projectSession
    ) {
        String normalizedTarget = normalizeProjectCode(targetProjectCode);
        if (!StringUtils.hasText(normalizedTarget) || whoami == null || !whoami.isObject()) {
            return false;
        }

        Set<String> confirmedProjectCodes = whoamiProjectCodes(whoami);
        if (!confirmedProjectCodes.isEmpty()) {
            return confirmedProjectCodes.size() == 1
                    && confirmedProjectCodes.contains(normalizedTarget);
        }

        return matchesIdentityEmail(whoami, expectedEmail)
                && projectSessionMatchesTarget(projectSession, normalizedTarget);
    }

    private static Set<String> whoamiProjectCodes(JsonNode whoami) {
        Set<String> confirmedProjectCodes = new LinkedHashSet<>();
        collectWhoamiProjectCodes(whoami, confirmedProjectCodes, 0);
        return confirmedProjectCodes;
    }

    private static boolean matchesIdentityEmail(JsonNode whoami, String expectedEmail) {
        if (!StringUtils.hasText(expectedEmail)) {
            return false;
        }
        JsonNode emailNode = whoami.get("email");
        return emailNode != null
                && emailNode.isTextual()
                && expectedEmail.trim().equalsIgnoreCase(emailNode.asText("").trim());
    }

    private static boolean projectSessionMatchesTarget(
            NoonSessionGateway.ProjectSessionCookie projectSession,
            String normalizedTarget
    ) {
        if (projectSession == null
                || projectSession.getProject() == null
                || !normalizedTarget.equals(normalizeProjectCode(projectSession.getProject().getProjectCode()))
                || !StringUtils.hasText(projectSession.getCookie())) {
            return false;
        }

        boolean targetContextFound = false;
        for (String segment : projectSession.getCookie().split(";")) {
            String normalizedSegment = segment == null ? "" : segment.trim();
            int separatorIndex = normalizedSegment.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            String name = normalizedSegment.substring(0, separatorIndex).trim();
            if (!"projectCode".equals(name)) {
                continue;
            }
            String value = normalizeProjectCode(normalizedSegment.substring(separatorIndex + 1));
            if (!normalizedTarget.equals(value)) {
                return false;
            }
            targetContextFound = true;
        }
        return targetContextFound;
    }

    private static void collectWhoamiProjectCodes(
            JsonNode node,
            Set<String> projectCodes,
            int depth
    ) {
        if (node == null || !node.isObject() || depth > 2) {
            return;
        }
        addProjectCode(node, projectCodes,
                "projectCode",
                "project_code",
                "currentProjectCode",
                "current_project_code",
                "selectedProjectCode",
                "selected_project_code");
        collectProjectNode(node.get("project"), projectCodes);
        collectProjectNode(node.get("currentProject"), projectCodes);
        collectProjectNode(node.get("current_project"), projectCodes);
        collectProjectNode(node.get("selectedProject"), projectCodes);
        collectProjectNode(node.get("selected_project"), projectCodes);

        collectWhoamiProjectCodes(node.get("data"), projectCodes, depth + 1);
        collectWhoamiProjectCodes(node.get("context"), projectCodes, depth + 1);
        collectWhoamiProjectCodes(node.get("result"), projectCodes, depth + 1);
        collectWhoamiProjectCodes(node.get("identity"), projectCodes, depth + 1);
        collectWhoamiProjectCodes(node.get("user"), projectCodes, depth + 1);
    }

    private static void collectProjectNode(JsonNode projectNode, Set<String> projectCodes) {
        if (projectNode == null || projectNode.isNull() || projectNode.isMissingNode()) {
            return;
        }
        if (projectNode.isTextual()) {
            addProjectCode(projectNode.asText(null), projectCodes);
            return;
        }
        if (projectNode.isObject()) {
            addProjectCode(projectNode, projectCodes, "code", "projectCode", "project_code");
        }
    }

    private static void addProjectCode(
            JsonNode node,
            Set<String> projectCodes,
            String... fieldNames
    ) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isTextual()) {
                addProjectCode(value.asText(null), projectCodes);
            }
        }
    }

    private static void addProjectCode(String value, Set<String> projectCodes) {
        String normalized = normalizeProjectCode(value);
        if (StringUtils.hasText(normalized)) {
            projectCodes.add(normalized);
        }
    }

    private static String normalizeProjectCode(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
