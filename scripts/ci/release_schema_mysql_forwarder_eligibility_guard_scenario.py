from __future__ import annotations

from schema_migrations.mysql_support import MySqlExecutionError


def verify_forwarder_eligibility_binary_guards(test_case, database):
    invalid_scopes = (
        (379991, "SA", "ET", "AIR", "unsupported"),
        (379992, "sa", "ET", "AIR", "UNSUPPORTED"),
        (379993, "SA ", "ET", "AIR", "UNSUPPORTED"),
        (379994, "SA", "et", "AIR", "UNSUPPORTED"),
        (379995, "SA", "ET", "sea", "UNSUPPORTED"),
    )
    for row_id, site, forwarder, mode, status in invalid_scopes:
        with test_case.assertRaises(MySqlExecutionError) as caught:
            database.client.execute(
                "INSERT INTO product_forwarder_transport_eligibility "
                "(id,owner_user_id,product_variant_id,site_code,forwarder_code,"
                "transport_mode,eligibility_status,effective_from) VALUES "
                f"({row_id},307,{row_id},'{site}','{forwarder}','{mode}','{status}','2026-08-01');"
            )
        test_case.assertEqual(3819, caught.exception.error_code)

    database.client.execute(
        "INSERT INTO product_forwarder_transport_eligibility "
        "(id,owner_user_id,product_variant_id,site_code,forwarder_code,"
        "transport_mode,eligibility_status,effective_from) VALUES "
        "(379999,307,379999,'SA','ET','AIR','UNSUPPORTED','2026-08-01');"
    )
    test_case.assertEqual(
        "307:379999:SA:ET:AIR",
        database.client.execute(
            "SELECT active_scope_slot FROM product_forwarder_transport_eligibility "
            "WHERE id=379999;"
        ),
    )
    database.client.execute(
        "DELETE FROM product_forwarder_transport_eligibility WHERE id=379999;"
    )

    for value in ("unsupported", "SUPPORTED "):
        with test_case.assertRaises(MySqlExecutionError) as caught:
            database.client.execute(
                "UPDATE procurement_shipping_order_line "
                f"SET eligibility_status_snapshot='{value}' WHERE id=1;"
            )
        test_case.assertEqual(3819, caught.exception.error_code)
