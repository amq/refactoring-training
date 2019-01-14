package at.fhtw.swe.model;

import java.util.Objects;

public class ValidationError {
    private transient String key;
    private transient String violation;

    public ValidationError key(final String key) {
        this.key = key;
        return this;
    }

    public ValidationError violation(final String violation) {
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
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        ValidationError that = (ValidationError) object;
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
