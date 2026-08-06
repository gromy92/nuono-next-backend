from pathlib import Path
import sys
import unittest


SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from ci.release_schema_mysql_postcheck_diagnostics import (  # noqa: E402
    outer_if_predicates,
)


class ReleaseSchemaMySqlPostcheckDiagnosticsTest(unittest.TestCase):
    def test_outer_predicates_ignore_sql_comments_but_preserve_literals(self):
        statement = """
        WITH sample AS (SELECT IF(1=1, 'inner--literal', 'other') value)
        SELECT IF(
          EXISTS (SELECT 1 FROM sample WHERE value='inner--literal')
          -- This comment contains AND and must not become executable SQL.
          AND 2=2
          /* This block comment also contains AND. */
          AND 'still--literal'='still--literal',
          1,0
        );
        """

        predicates = outer_if_predicates(statement)

        self.assertEqual(3, len(predicates))
        self.assertIn("inner--literal", predicates[0])
        self.assertEqual("2=2", predicates[1])
        self.assertIn("still--literal", predicates[2])
        self.assertNotIn("This comment", " ".join(predicates))


if __name__ == "__main__":
    unittest.main()
