package at.fhtw.swe.service;

import at.fhtw.swe.model.ValidationError;
import at.fhtw.swe.model.ValidationValue;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class SingleValueValidator {
    private static final String TYPE_GRID = "grid";
    private static final String CUSTOM_KEY = "custom";
    private static final String MAX_LENGTH_KEY = "maxLength";
    private static final String REQUIRED_KEY = "required";
    private static final String EXTERNAL_KEY = "external";
    private static final String MIN_KEY = "min";
    private static final String DATE_MAX_KEY = "maxDate";
    private static final String TYPE_DATETIME = "datetime";
    private static final String MAX_KEY = "max";
    private static final String PATTERN_KEY = "pattern";
    private static final String INTERNAL_KEY = "internal";
    private static final String JSONATA_KEY = "jsonata";
    private static final String DATE_MIN_KEY = "minDate";
    private static final String MIN_LENGTH_KEY = "minLength";
    private static final BiFunction<String, Integer, Boolean>
            MIN_LENGTH_CHECK = (value, length) -> value.length() >= length,
            MAX_LENGTH_CHECK = (value, length) -> value.length() <= length;
    private static final BiFunction<Integer, Integer, Boolean>
            MIN_ROW_COUNT_CHECK = (value, minRowCountVal) -> value >= minRowCountVal,
            MAX_ROW_COUNT_CHECK = (value, maxRowCountVal) -> value <= maxRowCountVal;
    private static final BiFunction<Double, Double, Boolean>
            MIN_CHECK = (value, minVal) -> value >= minVal,
            MAX_CHECK = (value, maxVal) -> value <= maxVal;
    private static final BiFunction<Instant, Instant, Boolean>
            DATE_MIN_CHECK = (value, dateMinVal) -> value.isAfter(dateMinVal),
            DATE_MAX_CHECK = (value, dateMaxVal) -> value.isBefore(dateMaxVal);

    Set<ValidationError> validateSingleValue(
            final ValidationValue validationValue) {
        final Set<ValidationError> result = new HashSet<>();
        final Consumer<ValidationError> storeError = error -> result.add(error);

        validateRequired(validationValue)
                .ifPresent(storeError);

        if (TYPE_GRID.equals(validationValue.getType())) {
            validateMinRowCount(validationValue)
                    .ifPresent(storeError);
            validateMaxRowCount(validationValue)
                    .ifPresent(storeError);
        } else if (TYPE_DATETIME.equals(validationValue.getType())) {
            validateDateMin(validationValue)
                    .ifPresent(storeError);
            validateDateMax(validationValue)
                    .ifPresent(storeError);
        } else {
            validateMinLength(validationValue)
                    .ifPresent(storeError);
            validateMaxLength(validationValue)
                    .ifPresent(storeError);
        }
        validatePattern(validationValue)
                .ifPresent(storeError);
        validateMin(validationValue)
                .ifPresent(storeError);
        validateMax(validationValue)
                .ifPresent(storeError);

        return result;
    }

    private Optional<ValidationError> validateRequired(
            final ValidationValue validationValue) {
        if (extractValidationInstruction(validationValue.getInstruction(), REQUIRED_KEY, validationValue.isInternal())
                .map(JsonNode::asBoolean)
                .orElse(false)) {

            if (validationValue.getValue() == null) {
                return Optional.ofNullable(cfeateError(validationValue.getKey(), validationValue.getRow(), REQUIRED_KEY));
            }
            return Optional.ofNullable(validationValue.getValue())
                    .map(JsonNode::asText)
                    .map(valueString -> !valueString.isEmpty())
                    .map(valid -> valid ? null : cfeateError(validationValue.getKey(), validationValue.getRow(), REQUIRED_KEY));
        }

        return Optional.empty();
    }

    private Optional<ValidationError> validateDateMin(
            final ValidationValue validationValue) {
        return validateDateTime(
                validationValue, DATE_MIN_KEY, DATE_MIN_CHECK);
    }

    private Optional<ValidationError> validateDateMax(
            final ValidationValue validationValue) {
        return validateDateTime(
                validationValue, DATE_MAX_KEY, DATE_MAX_CHECK);
    }

    private Optional<ValidationError> validateMinLength(
            final ValidationValue validationValue) {
        return validateLength(
                validationValue, MIN_LENGTH_KEY, MIN_LENGTH_CHECK);
    }

    private Optional<ValidationError> validateMaxLength(
            final ValidationValue validationValue) {
        return validateLength(
                validationValue, MAX_LENGTH_KEY, MAX_LENGTH_CHECK);
    }

    private Optional<ValidationError> validateMin(
            final ValidationValue validationValue) {
        if (validationValue.getInstruction().has(MIN_KEY)) {
            return validateNumberValue(
                    validationValue, MIN_KEY, MIN_CHECK);
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateMax(
            final ValidationValue validationValue) {
        if (validationValue.getInstruction().has(MAX_KEY)) {
            return validateNumberValue(
                    validationValue, MAX_KEY, MAX_CHECK);
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateMinRowCount(
            final ValidationValue validationValue) {
        if (validationValue.getInstruction().has(MIN_LENGTH_KEY)) {
            return validateRowCountValue(
                    validationValue, MIN_LENGTH_KEY, MIN_ROW_COUNT_CHECK);
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateMaxRowCount(
            final ValidationValue validationValue) {
        if (validationValue.getInstruction().has(MAX_LENGTH_KEY)) {
            return validateRowCountValue(
                    validationValue, MAX_LENGTH_KEY, MAX_ROW_COUNT_CHECK);
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validatePattern(
            final ValidationValue validationValue) {
        final String regexPattern =
                extractValidationInstruction(validationValue.getInstruction(), PATTERN_KEY, validationValue.isInternal())
                        .map(JsonNode::asText)
                        .orElse(null);
        if (regexPattern != null) {
            return Optional.ofNullable(validationValue.getValue())
                    .map(JsonNode::asText)
                    .map(valueString -> valueString.matches(regexPattern))
                    .map(valid -> valid ? null : cfeateError(validationValue.getKey(), validationValue.getRow(), PATTERN_KEY));
        }

        return Optional.empty();
    }

    private Optional<ValidationError> validateLength(
            final ValidationValue validationValue,
            final String validationKey,
            final BiFunction<String, Integer, Boolean> lengthCheck) {
        final Integer length =
                extractValidationInstruction(validationValue.getInstruction(), validationKey, validationValue.isInternal())
                        .map(JsonNode::asInt)
                        .orElse(null);
        if (length != null) {
            return Optional.ofNullable(validationValue.getValue())
                    .map(JsonNode::asText)
                    .map(valueString -> lengthCheck.apply(valueString, length))
                    .map(valid -> valid ? null : cfeateError(validationValue.getKey(), validationValue.getRow(), validationKey));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateDateTime(
            final ValidationValue validationValue,
            final String validationKey,
            final BiFunction<Instant, Instant, Boolean> dateCheck) {
        final String currDate =
                extractValidationInstruction(validationValue.getInstruction(), validationKey, validationValue.isInternal())
                        .map(JsonNode::asText)
                        .orElse(null);
        if (currDate != null) {
            return Optional.ofNullable(validationValue.getValue())
                    .map(JsonNode::asText)
                    .map(valueString -> dateCheck.apply(Instant.parse(valueString), Instant.parse(currDate)))
                    .map(valid -> valid ? null : cfeateError(validationValue.getKey(), validationValue.getRow(), validationKey));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateNumberValue(
            final ValidationValue validationValue,
            final String validationKey,
            final BiFunction<Double, Double, Boolean> numberCheck) {
        final Double val =
                extractValidationInstruction(validationValue.getInstruction(), validationKey, validationValue.isInternal())
                        .map(JsonNode::asDouble)
                        .orElse(null);
        return Optional.ofNullable(validationValue.getValue())
                .map(JsonNode::asDouble)
                .map(valueNumber -> numberCheck.apply(valueNumber, val))
                .map(valid -> valid ? null : cfeateError(validationValue.getKey(), validationValue.getRow(), validationKey));
    }

    private Optional<ValidationError> validateRowCountValue(
            final ValidationValue validationValue,
            final String validationKey,
            final BiFunction<Integer, Integer, Boolean> rowCountCheck) {
        final Integer val =
                Optional.ofNullable(getExternalValidations(validationValue.getInstruction(), validationKey))
                        .map(JsonNode::asInt)
                        .orElse(null);
        return Optional.ofNullable(validationValue.getValue())
                .map(JsonNode::size)
                .map(valueNumber -> rowCountCheck.apply(valueNumber, val))
                .map(valid -> valid ? null : cfeateError(validationValue.getKey(), validationValue.getRow(), validationKey));
    }

    private ValidationError cfeateError(final String key, final Integer row, final String violation) {
        return new ValidationError().key(key).violation(violation);
    }

    Optional<JsonNode> extractValidationInstruction(
            final JsonNode validationInstruction, final String validationKey, final boolean internal) {
        return internal ? Optional.ofNullable(
                getInternalValidations(validationInstruction, validationKey))
                : Optional.ofNullable(
                getExternalValidations(validationInstruction, validationKey));
    }

    private JsonNode getExternalValidations(
            final JsonNode validationInstruction, final String validationKey) {
        if (JSONATA_KEY.equals(validationKey)) {
            final JsonNode customTag = validationInstruction.get(CUSTOM_KEY);
            if (customTag == null) {
                return null;
            }
            final JsonNode externalTag = customTag.get(EXTERNAL_KEY);
            if (externalTag != null) {
                return externalTag.get(JSONATA_KEY);
            } else if (!customTag.has(INTERNAL_KEY)) {
                // this is a legacy support if custom tag only contains jsonata-string. // TODO: remove this
                return customTag;
            }
        } else {
            return validationInstruction.get(validationKey);
        }

        return null;
    }

    private JsonNode getInternalValidations(
            final JsonNode validationInstruction, final String validationKey) {
        return Optional.ofNullable(validationInstruction.get(CUSTOM_KEY))
                .map(customTag -> customTag.get(INTERNAL_KEY))
                .map(internalTag -> internalTag.get(validationKey))
                .orElse(null);
    }
}
