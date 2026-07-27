package com.nuono.next.product.publish;

import com.nuono.next.product.ProductPublishTaskRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ProductPublishTaskMessageFormatter {

    String message(ProductPublishTaskRecord task, List<String> changedDomains) {
        String status = normalize(task == null ? null : task.getStatus());
        if (task != null && ProductPublishCommandService.ERROR_CODE_NOON_AUTH_RECOVERY_PENDING
                .equalsIgnoreCase(normalize(task.getErrorCode()))) {
            return isDelete(task)
                    ? "Noon 授权恢复中。恢复完成后不会自动继续删除或重建，请重新核对目标商品并人工点击重试。"
                    : "Noon 授权恢复中。恢复完成后不会自动重放本次发布，请先核对 Noon 当前结果，再人工确认是否重试。";
        }
        if (isDelete(task)) {
            return deleteMessage(normalizeDeleteStatus(status));
        }
        if ("queued".equalsIgnoreCase(status)) {
            return "发布已排队，等待后台执行。";
        }
        if ("running".equalsIgnoreCase(status)) {
            return "正在提交 Noon。";
        }
        if ("submitted".equalsIgnoreCase(status)) {
            return "发布已提交，等待回读校验。";
        }
        if ("verifying".equalsIgnoreCase(status)) {
            return "正在校验 Noon 结果。";
        }
        if ("pending_effective".equalsIgnoreCase(status)) {
            return "Noon 可能延迟生效，系统将继续回读校验。";
        }
        if ("write_unknown".equalsIgnoreCase(status)) {
            return "Noon 写入请求超时，系统只回读校验，不会自动重复写入。";
        }
        if ("write_retry_scheduled".equalsIgnoreCase(status)) {
            return "发布正在后台处理，系统会自动核对 Noon 结果。";
        }
        if ("verify_timeout".equalsIgnoreCase(status)) {
            return "Noon 回读校验超时，系统稍后继续核对。";
        }
        if ("pending_manual_check".equalsIgnoreCase(status)) {
            String labels = changedDomainText(changedDomains);
            String target = StringUtils.hasText(labels) ? "【" + labels + "】" : "本次修改";
            return "Noon 多轮回读仍未确认" + target + "已生效。诺诺草稿已保留，请在官方后台核对后选择重试发布或从 Noon 同步。";
        }
        if ("synced".equalsIgnoreCase(status)) {
            return "发布已完成，本地基线已更新。";
        }
        if ("failed".equalsIgnoreCase(status)) {
            if ("publish_conflict".equalsIgnoreCase(normalize(task.getErrorCode()))) {
                return "该发布任务按旧冲突规则失败，诺诺草稿已保留。请重新点击发布当前修改，系统会按本地草稿覆盖 Noon 对应字段。";
            }
            return firstNonBlank(task.getErrorMessage(), "发布失败，诺诺草稿已保留。");
        }
        if ("cancelled".equalsIgnoreCase(status)) {
            return "发布任务已取消。";
        }
        return firstNonBlank(task == null ? null : task.getErrorMessage(), "发布任务状态已更新。");
    }

    private String deleteMessage(String status) {
        if ("queued".equalsIgnoreCase(status)) {
            return "商品删除已排队，后台会先处理 Noon 删除，成功后再清理本地商品目录。";
        }
        if ("running".equalsIgnoreCase(status)) {
            return "商品删除正在后台执行。";
        }
        if (Set.of(
                "submitted",
                "verifying",
                "pending_effective",
                "write_unknown",
                "write_retry_scheduled",
                "verify_timeout"
        ).contains(status)) {
            return "商品删除正在后台自动处理，系统会继续核对 Noon 删除结果。";
        }
        if ("pending_manual_check".equalsIgnoreCase(status)) {
            return "商品删除结果需要人工核对：请在 Noon 后台确认 ZSKU/catalog 是否已删除后重试或联系技术处理。";
        }
        if ("failed".equalsIgnoreCase(status)) {
            return "商品删除失败，诺诺本地商品未删除，请重试或联系技术处理。";
        }
        if ("synced".equalsIgnoreCase(status)) {
            return "商品删除已完成。";
        }
        if ("cancelled".equalsIgnoreCase(status)) {
            return "商品删除任务已取消。";
        }
        return "商品删除任务状态已更新，系统会在后台继续处理。";
    }

    private String changedDomainText(List<String> changedDomains) {
        Set<String> labels = new LinkedHashSet<>();
        for (String domain : changedDomains == null ? List.<String>of() : changedDomains) {
            String label = changedDomainLabel(domain);
            if (StringUtils.hasText(label)) {
                labels.add(label);
            }
        }
        return String.join("、", labels);
    }

    private String changedDomainLabel(String domain) {
        String normalized = normalize(domain);
        if ("main".equalsIgnoreCase(normalized)) return "商品主档";
        if ("content".equalsIgnoreCase(normalized)) return "图文内容";
        if ("attributes".equalsIgnoreCase(normalized)) return "关键属性";
        if ("site".equalsIgnoreCase(normalized) || "site_offer".equalsIgnoreCase(normalized)) {
            return "当前站点经营";
        }
        if ("grouping".equalsIgnoreCase(normalized)) return "Group 与变体";
        if ("sizes".equalsIgnoreCase(normalized)) return "尺码";
        if ("delete".equalsIgnoreCase(normalized)) return "商品删除";
        return null;
    }

    private String normalizeDeleteStatus(String status) {
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_QUEUED.equalsIgnoreCase(status)) return "queued";
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_RUNNING.equalsIgnoreCase(status)) return "running";
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_SUBMITTED.equalsIgnoreCase(status)) return "submitted";
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_VERIFYING.equalsIgnoreCase(status)) return "verifying";
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_PENDING_EFFECTIVE.equalsIgnoreCase(status)) {
            return "pending_effective";
        }
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_WRITE_RETRY_SCHEDULED.equalsIgnoreCase(status)) {
            return "write_retry_scheduled";
        }
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_VERIFY_TIMEOUT.equalsIgnoreCase(status)) {
            return "verify_timeout";
        }
        return status;
    }

    private boolean isDelete(ProductPublishTaskRecord task) {
        return ProductPublishTaskClassifier.isProductDelete(task);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
