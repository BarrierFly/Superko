package dev.superko.core;

/**
 * Kind of event that starts a chain. Each start event gets its own chain; the chain ends
 * when the start event's synchronous call stack returns.
 */
public enum ChainType {
    PACKET("player packet"),
    SCHEDULED_TICK("scheduled tick"),
    BLOCK_EVENT("block event"),
    BLOCK_ENTITY_TICK("block entity tick"),
    ENTITY("entity tick"),
    ENTITY_PASSENGER("passenger entity tick");

    public final String label;

    ChainType(String label) {
        this.label = label;
    }
}
