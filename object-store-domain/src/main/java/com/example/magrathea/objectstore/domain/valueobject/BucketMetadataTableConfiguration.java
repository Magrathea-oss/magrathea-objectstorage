package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * BucketMetadataTableConfiguration — a value object for bucket metadata table configuration.
 * <p>
 * Defines the table name, key columns, and optional inventory and journal table settings
 * for S3 object metadata storage.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record BucketMetadataTableConfiguration(
    String tableName,
    List<String> keyColumns,
    InventoryTableConfiguration inventoryConfiguration,
    JournalTableConfiguration journalConfiguration
) {

    public BucketMetadataTableConfiguration {
        Objects.requireNonNull(tableName);
        Objects.requireNonNull(keyColumns);
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
        keyColumns = List.copyOf(keyColumns);
    }

    /**
     * Factory method — create full configuration.
     */
    public static BucketMetadataTableConfiguration of(
            String tableName,
            List<String> keyColumns,
            InventoryTableConfiguration inventoryConfiguration,
            JournalTableConfiguration journalConfiguration) {
        return new BucketMetadataTableConfiguration(tableName, keyColumns,
            inventoryConfiguration, journalConfiguration);
    }

    /**
     * Factory method — create with only table name and key columns (no inventory/journal).
     */
    public static BucketMetadataTableConfiguration basic(String tableName, List<String> keyColumns) {
        return new BucketMetadataTableConfiguration(tableName, keyColumns, null, null);
    }

    /**
     * Returns a new configuration with the inventory table configuration replaced.
     */
    public BucketMetadataTableConfiguration withInventoryConfiguration(InventoryTableConfiguration inventory) {
        Objects.requireNonNull(inventory);
        return new BucketMetadataTableConfiguration(tableName, keyColumns, inventory, journalConfiguration);
    }

    /**
     * Returns a new configuration with the journal table configuration replaced.
     */
    public BucketMetadataTableConfiguration withJournalConfiguration(JournalTableConfiguration journal) {
        Objects.requireNonNull(journal);
        return new BucketMetadataTableConfiguration(tableName, keyColumns, inventoryConfiguration, journal);
    }

    public List<String> keyColumns() {
        return Collections.unmodifiableList(keyColumns);
    }

    /**
     * InventoryTableConfiguration — sub-configuration for metadata inventory table.
     */
    public record InventoryTableConfiguration(
        String inventoryTableName,
        List<String> inventoryKeyColumns,
        boolean enabled
    ) {

        public InventoryTableConfiguration {
            Objects.requireNonNull(inventoryTableName);
            Objects.requireNonNull(inventoryKeyColumns);
            if (inventoryTableName.isBlank()) {
                throw new IllegalArgumentException("inventoryTableName must not be blank");
            }
            inventoryKeyColumns = List.copyOf(inventoryKeyColumns);
        }

        /**
         * Factory method.
         */
        public static InventoryTableConfiguration of(
                String inventoryTableName,
                List<String> inventoryKeyColumns,
                boolean enabled) {
            return new InventoryTableConfiguration(inventoryTableName, inventoryKeyColumns, enabled);
        }

        /**
         * Factory method — disabled inventory.
         */
        public static InventoryTableConfiguration disabled() {
            return new InventoryTableConfiguration("", List.of(), false);
        }

        public List<String> inventoryKeyColumns() {
            return Collections.unmodifiableList(inventoryKeyColumns);
        }
    }

    /**
     * JournalTableConfiguration — sub-configuration for metadata journal table.
     */
    public record JournalTableConfiguration(
        String journalTableName,
        List<String> journalKeyColumns,
        boolean enabled
    ) {

        public JournalTableConfiguration {
            Objects.requireNonNull(journalTableName);
            Objects.requireNonNull(journalKeyColumns);
            if (journalTableName.isBlank()) {
                throw new IllegalArgumentException("journalTableName must not be blank");
            }
            journalKeyColumns = List.copyOf(journalKeyColumns);
        }

        /**
         * Factory method.
         */
        public static JournalTableConfiguration of(
                String journalTableName,
                List<String> journalKeyColumns,
                boolean enabled) {
            return new JournalTableConfiguration(journalTableName, journalKeyColumns, enabled);
        }

        /**
         * Factory method — disabled journal.
         */
        public static JournalTableConfiguration disabled() {
            return new JournalTableConfiguration("", List.of(), false);
        }

        public List<String> journalKeyColumns() {
            return Collections.unmodifiableList(journalKeyColumns);
        }
    }
}
