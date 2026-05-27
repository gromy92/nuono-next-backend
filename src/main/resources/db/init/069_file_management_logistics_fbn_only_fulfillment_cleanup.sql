-- File-management logistics FBN-only cleanup.
-- Current logistics parsing does not model fulfillment mode as a user/business dimension.

SET NAMES utf8mb4;

UPDATE `file_mgmt_parse_item_standard`
SET `natural_key_json` = '{"fields":["forwarderCode","country","transportMode","serviceScope","destinationNode"]}',
    `field_schema_json` = '{"forwarderCode":"string","forwarderName":"string","country":"string","destinationNode":"string","transportMode":"string","serviceScope":"string","originWarehouse":"string","destinationWarehouse":"string","departureFrequency":"string","leadTimeText":"string","leadTimeMinDays":"integer","leadTimeMaxDays":"integer","effectiveDate":"date","sourceVersion":"string"}',
    `display_config_json` = '{"columns":["forwarderName","country","destinationNode","transportMode","serviceScope","originWarehouse","destinationWarehouse","departureFrequency","leadTimeText","effectiveDate"]}',
    `validation_rule_json` = '{"required":["forwarderCode","country","transportMode","serviceScope","destinationNode"]}',
    `diff_rule_json` = '{"compareFields":["forwarderName","country","destinationNode","transportMode","serviceScope","originWarehouse","destinationWarehouse","departureFrequency","leadTimeText","leadTimeMinDays","leadTimeMaxDays","effectiveDate","sourceVersion"]}',
    `gmt_updated` = NOW()
WHERE `standard_version_id` = 2003
  AND `item_type` = 'logistics_service_line'
  AND `is_deleted` = b'0';

UPDATE `file_mgmt_parse_result_item`
SET `natural_key` = REPLACE(`natural_key`, '|FBN|', '|'),
    `natural_key_hash` = SHA2(CONCAT(`item_type`, '|', REPLACE(`natural_key`, '|FBN|', '|')), 256),
    `normalized_payload_json` = CASE
        WHEN JSON_VALID(`normalized_payload_json`) THEN JSON_REMOVE(`normalized_payload_json`, '$.fulfillmentMode')
        ELSE `normalized_payload_json`
    END,
    `effective_payload_json` = CASE
        WHEN JSON_VALID(`effective_payload_json`) THEN JSON_REMOVE(`effective_payload_json`, '$.fulfillmentMode')
        ELSE `effective_payload_json`
    END,
    `validation_error_json` = CASE
        WHEN JSON_VALID(`validation_error_json`)
             AND JSON_CONTAINS(JSON_EXTRACT(`validation_error_json`, '$.missingRequiredFields'), JSON_QUOTE('fulfillmentMode'))
        THEN NULL
        ELSE `validation_error_json`
    END,
    `gmt_updated` = NOW()
WHERE `item_type` = 'logistics_service_line'
  AND `is_deleted` = b'0'
  AND (
      `natural_key` LIKE '%|FBN|%'
      OR (JSON_VALID(`normalized_payload_json`) AND JSON_EXTRACT(`normalized_payload_json`, '$.fulfillmentMode') IS NOT NULL)
      OR (JSON_VALID(`effective_payload_json`) AND JSON_EXTRACT(`effective_payload_json`, '$.fulfillmentMode') IS NOT NULL)
  );

UPDATE `file_mgmt_parse_item_review`
SET `override_payload_json` = CASE
        WHEN JSON_VALID(`override_payload_json`) THEN JSON_REMOVE(`override_payload_json`, '$.fulfillmentMode')
        ELSE `override_payload_json`
    END,
    `effective_payload_json` = CASE
        WHEN JSON_VALID(`effective_payload_json`) THEN JSON_REMOVE(`effective_payload_json`, '$.fulfillmentMode')
        ELSE `effective_payload_json`
    END,
    `gmt_updated` = NOW()
WHERE `result_item_id` IN (
        SELECT `id`
        FROM `file_mgmt_parse_result_item`
        WHERE `item_type` = 'logistics_service_line'
          AND `is_deleted` = b'0'
    )
  AND `is_deleted` = b'0';

UPDATE `file_mgmt_parse_version_item`
SET `natural_key` = REPLACE(`natural_key`, '|FBN|', '|'),
    `natural_key_hash` = SHA2(CONCAT(`item_type`, '|', REPLACE(`natural_key`, '|FBN|', '|')), 256),
    `version_payload_json` = CASE
        WHEN JSON_VALID(`version_payload_json`) THEN JSON_REMOVE(`version_payload_json`, '$.fulfillmentMode')
        ELSE `version_payload_json`
    END,
    `gmt_updated` = NOW()
WHERE `item_type` = 'logistics_service_line'
  AND `is_deleted` = b'0'
  AND (
      `natural_key` LIKE '%|FBN|%'
      OR (JSON_VALID(`version_payload_json`) AND JSON_EXTRACT(`version_payload_json`, '$.fulfillmentMode') IS NOT NULL)
  );

UPDATE `logistics_service_line`
SET `natural_key` = REPLACE(`natural_key`, '|FBN|', '|'),
    `fulfillment_mode` = NULL,
    `gmt_updated` = NOW()
WHERE `fulfillment_mode` = 'FBN'
   OR `natural_key` LIKE '%|FBN|%';
