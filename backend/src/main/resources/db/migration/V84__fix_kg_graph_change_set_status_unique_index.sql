ALTER TABLE kg_graph_change_set
    DROP INDEX uk_kg_graph_change_set_entity,
    ADD INDEX idx_kg_graph_change_set_entity_operation (entity_type, entity_id, operation_type);
