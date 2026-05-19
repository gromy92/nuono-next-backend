-- Clean stale draft rows that are byte-for-byte identical to their baseline snapshot.
-- These rows are not user changes and should not make list projections look like pending drafts.
DELETE pmd
FROM product_master_draft pmd
JOIN product_master_snapshot pms
  ON pms.id = pmd.baseline_snapshot_id
 AND pms.is_deleted = 0
WHERE pmd.is_deleted = 0
  AND COALESCE(pmd.draft_json, '') = COALESCE(pms.snapshot_json, '');

UPDATE product_master pm
JOIN product_master_draft pmd
  ON pmd.product_master_id = pm.id
 AND pmd.is_deleted = 0
JOIN product_master_snapshot pms
  ON pms.id = pmd.baseline_snapshot_id
 AND pms.is_deleted = 0
SET pm.sync_status = 'draft',
    pm.gmt_updated = NOW()
WHERE pm.is_deleted = 0
  AND pm.sync_status = 'synced'
  AND COALESCE(pmd.draft_json, '') <> COALESCE(pms.snapshot_json, '');
