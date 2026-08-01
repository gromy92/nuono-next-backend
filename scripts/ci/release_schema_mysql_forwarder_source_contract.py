from __future__ import annotations


SOURCE_RAW_CATEGORY_HASH = "a8ea877d8cc8fdbd249c2ea716f9cea0316b031c9d104419bb44f34e056290cf"
SOURCE_RAW_PRICE_HASH = "2be8542906f265bc3cdcf60c763f0fe949b51e15c5f843064a77481065e40029"
SOURCE_CATEGORY_HASH = "088dff7da968d51e58fea26398acf661e329218397fba05faa657a4768930e30"
SOURCE_PRICE_HASH = "902b6173f5ee366a03a79f282777a67579ab8262598bdab89e588533cfd19ff1"
SOURCE_FEE_HASH = "74ff49fbd0863e298bbb9244a8db8c2429e12ce84d9dbf7dd7ac2a8df9e832f8"

SOURCE_RAW_CATEGORY_HASH_SQL = (
    "SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),"
    "cargo_category_name,source_category_name,category_level_1,category_level_2,"
    "product_examples,product_keywords,electric_type,sensitive_tags,packing_policy,"
    "manual_confirm_required,match_priority) AS CHAR) ORDER BY id SEPARATOR '\\n'),256) "
    "FROM forwarder_quote_cargo_category WHERE quote_version_id=904002 "
    "AND service_code='YT-SAU-SEA-FBN-RUH';"
)
SOURCE_RAW_PRICE_HASH_SQL = (
    "SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(cargo_category_code,3),"
    "cargo_category_name,pricing_model,currency,unit_price,billing_unit,billing_basis,"
    "volume_divisor,sea_weight_ratio,min_billable_unit,min_billable_unit_type,"
    "min_charge,rounding_rule,target_platform,delivery_city,price_status) AS CHAR) "
    "ORDER BY id SEPARATOR '\\n'),256) FROM forwarder_quote_base_price "
    "WHERE quote_version_id=904002 AND service_code='YT-SAU-SEA-FBN-RUH';"
)
SOURCE_CATEGORY_HASH_SQL = SOURCE_RAW_CATEGORY_HASH_SQL.replace(
    "cargo_category_name,source_category_name",
    "CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '普货' WHEN '021' THEN '小家电' "
    "WHEN '022' THEN '灯具' WHEN '023' THEN '一般敏感货' ELSE cargo_category_name END," +
    "source_category_name",
)
SOURCE_PRICE_HASH_SQL = SOURCE_RAW_PRICE_HASH_SQL.replace(
    "cargo_category_name,pricing_model,currency,unit_price",
    "CASE RIGHT(cargo_category_code,3) WHEN '020' THEN '普货' WHEN '021' THEN '小家电' "
    "WHEN '022' THEN '灯具' WHEN '023' THEN '一般敏感货' ELSE cargo_category_name END," +
    "pricing_model,currency,CASE RIGHT(cargo_category_code,3) "
    "WHEN '020' THEN 1540.0000 WHEN '021' THEN 1900.0000 WHEN '022' THEN 2040.0000 "
    "WHEN '023' THEN 2290.0000 ELSE unit_price END",
)
SOURCE_FEE_HASH_SQL = (
    "SELECT SHA2(GROUP_CONCAT(CAST(JSON_ARRAY(RIGHT(fee_rule_code,4),fee_name,"
    "fee_type,target_platform,delivery_city,trigger_condition,pricing_model,currency,"
    "amount,rate,billing_unit,billing_basis,min_charge,min_billable_unit,rounding_rule,"
    "included_in_base_price) AS CHAR) ORDER BY id SEPARATOR '\\n'),256) "
    "FROM forwarder_quote_transport_fee WHERE quote_version_id=904002 "
    "AND service_code='YT-SAU-SEA-FBN-RUH';"
)


def assert_source_contract(database):
    expected = (
        (SOURCE_RAW_CATEGORY_HASH_SQL, SOURCE_RAW_CATEGORY_HASH),
        (SOURCE_RAW_PRICE_HASH_SQL, SOURCE_RAW_PRICE_HASH),
        (SOURCE_CATEGORY_HASH_SQL, SOURCE_CATEGORY_HASH),
        (SOURCE_PRICE_HASH_SQL, SOURCE_PRICE_HASH),
        (SOURCE_FEE_HASH_SQL, SOURCE_FEE_HASH),
    )
    for statement, digest in expected:
        assert database.client.execute(statement) == digest
