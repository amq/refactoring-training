package at.fhtw.swe.model;

import java.util.Objects;

public class ValidationError {
    private String key;
    private String violation;

    public ValidationError key(String key) {
        this.key = key;
        return this;
    }

    public ValidationError violation(String violation) {
        this.violation = violation;
        return this;
    }

    public String getKey() {
        return key;
    }

    public String getViolation() {
        return violation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationError that = (ValidationError) o;
        return Objects.equals(key, that.key) &&
                Objects.equals(violation, that.violation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, violation);
    }

    @Override
    public String toString() {
        return "ValidationError{" +
                "key='" + key + '\'' +
                ", violation='" + violation + '\'' +
                '}';
    }
}
