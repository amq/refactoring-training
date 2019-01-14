package at.fhtw.swe.model;

import com.fasterxml.jackson.databind.JsonNode;

public class ValidationValue {
    private JsonNode value;
    private JsonNode instruction;
    private String key;
    private String type;
    private Integer row;
    private boolean internal;

    public JsonNode getValue() {
        return value;
    }

    public void setValue(final JsonNode value) {
        this.value = value;
    }

    public JsonNode getInstruction() {
        return instruction;
    }

    public void setInstruction(final JsonNode instruction) {
        this.instruction = instruction;
    }

    public String getKey() {
        return key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public Integer getRow() {
        return row;
    }

    public void setRow(final Integer row) {
        this.row = row;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(final boolean internal) {
        this.internal = internal;
    }
}
