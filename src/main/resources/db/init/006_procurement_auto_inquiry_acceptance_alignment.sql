-- Nuono Next procurement auto-inquiry acceptance alignment delta
-- Purpose:
-- 1. Re-open auto-inquiry session capacity for repeated local send-stage runs.
-- 2. Align one normal procurement-path candidate with a real 1688 detail page so
--    product validation can happen from the normal `采购单` flow instead of a fixed dev-only sample.

UPDATE `procurement_auto_inquiry_session`
SET `status` = 'READY',
    `leased_task_id` = NULL,
    `note` = '阶段收口：释放历史发送占用，供正常采购路径自动询价复验',
    `lease_updated_at` = NOW(),
    `updated_by` = 10002,
    `gmt_updated` = NOW()
WHERE `id` IN (43999, 44001, 44002, 44003);

UPDATE `procurement_candidate`
SET `candidate_url` = 'https://detail.1688.com/offer/798448779771.html?offerId=798448779771&hotSaleSkuId=5613239587877&spm=a260k.home2025.recommendpart.2',
    `title` = 'USB 充电轻奢焚香炉现货款',
    `supplier_name` = '苍南县永诚复合材料有限公司',
    `price_text` = '10.8-13.5 元',
    `moq_text` = '300 件起',
    `location_text` = '浙江温州',
    `result_card_text` = '结果卡片；USB充电轻奢焚香炉现货款；价格10.8-13.5元；300件起；浙江温州；48小时响应',
    `detail_highlight_text` = '详情卖点；现货款；支持打样；可直接进入自动询价',
    `attribute_snapshot_text` = '属性快照；材质 复合材料；供电方式 无电；尺寸 标准卷装；包装 普通包装',
    `shipping_snapshot_text` = '物流说明；48小时响应；支持首轮打样沟通',
    `package_snapshot_text` = '包装说明；普通包装；规格细节待二次确认',
    `inquiry_summary` = '可先发起自动询价，确认首轮报价、打样和交期。',
    `next_action` = 'PREPARE_INQUIRY',
    `badges_text` = '现货供应|支持打样|48小时响应',
    `reasons_text` = '可直接进入询价|供应商响应快|支持首轮打样',
    `warnings_text` = '规格细节待确认|包装仍需人工复核',
    `updated_by` = 10002,
    `gmt_updated` = NOW()
WHERE `id` = 43006;
