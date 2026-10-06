# Flyway Migration Reconciliation

The duplicate `V41` and `V49` scripts were assigned the new versions `V67` and
`V68`. This is valid for a database that has not run either duplicate script.
It is not automatically compatible with a database whose Flyway history was
created from an earlier repository revision.

Before deploying this release to an existing environment, run:

```sql
SELECT installed_rank, version, description, script, checksum
FROM flyway_schema_history
WHERE version IN ('41', '49', '67', '68')
ORDER BY installed_rank;
```

Use the following decision table:

| History result | Required action |
| --- | --- |
| No rows for `41` or `49` | Deploy normally. Flyway runs `V67` and `V68`. |
| `V41__Create_governance_and_memory_tables.sql` or `V49__Add_emp_ability_unique_constraint.sql` was applied | Stop the deployment. Back up the schema and reconcile that environment before startup. Do not disable Flyway validation. |
| `V41__Add_follow_up_realtime_fields.sql` and `V49__Add_layered_evaluation_columns_to_matching_record.sql` were applied | Deploy normally after confirming the governance tables and employee ability unique index have not already been created manually. |

For an environment in the second row, the DBA must first verify the physical
schema, apply any missing follow-up columns, then run Flyway `repair` only as
part of the reviewed release procedure. `repair` changes recorded metadata; it
does not execute missing DDL. Record the resulting `flyway_schema_history` in
the deployment ticket before starting the application.
