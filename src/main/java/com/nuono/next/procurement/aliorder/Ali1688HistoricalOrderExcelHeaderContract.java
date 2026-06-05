package com.nuono.next.procurement.aliorder;

import java.util.Arrays;
import java.util.List;

public final class Ali1688HistoricalOrderExcelHeaderContract {

    private static final List<String> EXPECTED_HEADERS = Arrays.asList(
            "订单编号",
            "买家公司名",
            "买家会员名",
            "卖家公司名",
            "卖家会员名",
            "货品总价(元)",
            "运费(元)",
            "涨价或折扣(元)",
            "实付款(元)",
            "订单状态",
            "订单创建时间",
            "订单付款时间",
            "发货方",
            "收货人姓名",
            "收货地址",
            "邮编",
            "联系电话",
            "联系手机",
            "货品标题",
            "单价(元)",
            "数量",
            "单位",
            "货号",
            "型号",
            "Offer ID",
            "SKU ID",
            "物料编号",
            "单品货号",
            "货品种类",
            "买家留言",
            "物流公司",
            "运单号",
            "发票：购货单位名称",
            "发票：纳税人识别号",
            "发票：地址、电话",
            "发票：开户行及账号",
            "发票收取地址",
            "关联编号",
            "代理商姓名",
            "代理商联系方式",
            "是否代发订单",
            "代发服务商id",
            "微商订单号",
            "下单批次号",
            "下游渠道",
            "下游订单号",
            "下单公司主体",
            "发起人登录名",
            "是否发起免密支付(1:淘货源诚e赊免密支付2:批量下单免密支付)"
    );

    private Ali1688HistoricalOrderExcelHeaderContract() {
    }

    public static List<String> expectedHeaders() {
        return EXPECTED_HEADERS;
    }

    public static Ali1688HistoricalOrderExcelParseResult.HeaderValidation validate(List<String> actualHeaders) {
        Ali1688HistoricalOrderExcelParseResult.HeaderValidation validation =
                new Ali1688HistoricalOrderExcelParseResult.HeaderValidation();
        validation.setExpectedHeaderCount(EXPECTED_HEADERS.size());
        validation.setActualHeaderCount(actualHeaders == null ? 0 : actualHeaders.size());
        if (actualHeaders == null) {
            validation.setValid(false);
            validation.setMissingHeaders(EXPECTED_HEADERS);
            validation.setMessage("未读取到 1688 历史订单表头。");
            return validation;
        }
        for (int index = 0; index < EXPECTED_HEADERS.size(); index++) {
            String expected = EXPECTED_HEADERS.get(index);
            String actual = index < actualHeaders.size() ? actualHeaders.get(index) : null;
            if (!expected.equals(actual)) {
                validation.getMismatchedHeaders().add(new Ali1688HistoricalOrderExcelParseResult.HeaderMismatch(
                        index + 1,
                        expected,
                        actual
                ));
                if (actual == null || actual.isBlank()) {
                    validation.getMissingHeaders().add(expected);
                }
            }
        }
        validation.setValid(validation.getMismatchedHeaders().isEmpty());
        validation.setMessage(validation.isValid()
                ? "表头匹配 1688 历史订单 49 列格式。"
                : "表头与 1688 历史订单 49 列格式不匹配。");
        return validation;
    }
}
