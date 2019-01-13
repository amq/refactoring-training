package at.fhtw.swe.service;

import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;

import at.fhtw.swe.model.ValidationError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.springframework.stereotype.Service;

@Service
public class Validator {

    private static final String TYPE_GRID = "grid";
    private static final String COMPONENT_KEY = "id";
    private static final String TYPE_KEY = "type";
    private static final String COMPONENT_TAG = "components";
    private static final String CUSTOM_KEY = "custom";
    private static final String MAX_LENGTH_KEY = "maxLength";
    private static final String VALIDATE_KEY = "validate";
    private static final String REQUIRED_KEY = "required";
    private static final String EXTERNAL_KEY = "external";
    private static final String MIN_KEY = "min";
    private static final String DATE_MAX_KEY = "maxDate";
    private static final String TYPE_DATETIME = "datetime";
    private static final String MAX_KEY = "max";
    private static final String ROW_NUM_PLACEHOLER = "@eval:rownum@";
    private static final String PATTERN_KEY = "pattern";
    private static final String INTERNAL_KEY = "internal";
    private static final String CURRENT_DATE_PLACEHOLER = "@date:now@";
    private static final String JSONATA_KEY = "jsonata";
    private static final String DATE_MIN_KEY = "minDate";
    private static final String GRID_INPUT_KEYS_QUERY =
            "$.." + COMPONENT_TAG + "[?].." + COMPONENT_TAG + "[?]." + COMPONENT_KEY;
    private static final String INPUTS_TO_VALIDATE_QUERY = "$.." + COMPONENT_TAG + "[?]";
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

    private JsonataEngine jsonataEngine;
    private JsonPath getInputsToValidate = JsonPath.compile(INPUTS_TO_VALIDATE_QUERY, filter(where("@." + VALIDATE_KEY).exists(true)));
    private JsonPath getInputKeysInsideGrids = JsonPath.compile(GRID_INPUT_KEYS_QUERY, filter(where("@." + TYPE_KEY).eq(TYPE_GRID)), filter(where("@." + VALIDATE_KEY).exists(true)));

    public Validator(JsonataEngine jsonataEngine) {
        this.jsonataEngine = jsonataEngine;

        Configuration.setDefaults(
                new Configuration.Defaults() {
                    private final JsonProvider jsonProvider = new JacksonJsonProvider();
                    private final MappingProvider mappingProvider = new JacksonMappingProvider();

                    @Override
                    public JsonProvider jsonProvider() {
                        return jsonProvider;
                    }

                    @Override
                    public MappingProvider mappingProvider() {
                        return mappingProvider;
                    }

                    @Override
                    public Set<Option> options() {
                        return EnumSet.noneOf(Option.class);
                    }
                });

    }

    public Set<ValidationError> validateForm(
            String form, String formdata, boolean internal) {
        final DocumentContext formContext = JsonPath.parse(form);
        final DocumentContext dataContext = JsonPath.parse(formdata);

        final Object jsonataData = jsonataEngine.parseData(formdata);
        final ArrayNode inputsWithValidations = getInputsWithValidations(formContext);
        final Set<String> gridInputs = getInputKeysInsideGrids(formContext);
        final Set<ValidationError> errors = new HashSet<>();

        inputsWithValidations.forEach(
                input -> {
                    final JsonNode validationNode = input.get(VALIDATE_KEY);
                    final String id = input.get(COMPONENT_KEY).asText();
                    final ArrayNode inspectedValue = dataContext.read("$.." + id, ArrayNode.class);
                    final String type = input.get(TYPE_KEY).asText();

                    if (gridInputs.contains(id)) {
                        // grid validation
                        for (int row = 0; row < inspectedValue.size(); row++) {
                            errors.addAll(
                                    validateSingleValue(inspectedValue.get(row), validationNode, id, type, row, internal));
                            checkJsonnata(jsonataData, validationNode, id, row, internal)
                                    .ifPresent(error -> errors.add(error));
                        }

                    } else {
                        // normal validation
                        errors.addAll(
                                validateSingleValue(inspectedValue.get(0), validationNode, id, type, null, internal));
                        checkJsonnata(jsonataData, validationNode, id, null, internal)
                                .ifPresent(error -> errors.add(error));
                    }
                });

        return errors;
    }

    private ArrayNode getInputsWithValidations(DocumentContext formContext) {
        return formContext.read(getInputsToValidate, ArrayNode.class);
    }

    private Set<String> getInputKeysInsideGrids(DocumentContext formContext) {
        return formContext.read(getInputKeysInsideGrids, Set.class);
    }

    private Set<ValidationError> validateSingleValue(
            JsonNode valueToInspect,
            JsonNode instruction,
            String inputKey,
            String inputType,
            Integer row,
            boolean internal) {
        final Set<ValidationError> result = new HashSet<>();
        final Consumer<ValidationError> storeError = error -> result.add(error);

        validateRequired(valueToInspect, instruction, inputKey, row, internal)
                .ifPresent(storeError);

        if (TYPE_GRID.equals(inputType)) {
            validateMinRowCount(valueToInspect, instruction, inputKey, row)
                    .ifPresent(storeError);
            validateMaxRowCount(valueToInspect, instruction, inputKey, row)
                    .ifPresent(storeError);
        } else if (TYPE_DATETIME.equals(inputType)) {
            validateDateMin(valueToInspect, instruction, inputKey, row, internal)
                    .ifPresent(storeError);
            validateDateMax(valueToInspect, instruction, inputKey, row, internal)
                    .ifPresent(storeError);
        } else {
            validateMinLength(valueToInspect, instruction, inputKey, row, internal)
                    .ifPresent(storeError);
            validateMaxLength(valueToInspect, instruction, inputKey, row, internal)
                    .ifPresent(storeError);
        }
        validatePattern(valueToInspect, instruction, inputKey, row, internal)
                .ifPresent(storeError);
        validateMin(valueToInspect, instruction, inputKey, row, internal)
                .ifPresent(storeError);
        validateMax(valueToInspect, instruction, inputKey, row, internal)
                .ifPresent(storeError);

        return result;
    }

    private Optional<ValidationError> validateRequired(
            JsonNode value,
            JsonNode validationInstruction,
            String key,
            Integer row,
            boolean internal) {
        if (extractValidationInstruction(validationInstruction, REQUIRED_KEY, internal)
                .map(JsonNode::asBoolean)
                .orElse(false)) {

            if (value == null) {
                return Optional.ofNullable(cfeateError(key, row, REQUIRED_KEY));
            }
            return Optional.ofNullable(value)
                    .map(JsonNode::asText)
                    .map(valueString -> !valueString.isEmpty())
                    .map(valid -> valid ? null : cfeateError(key, row, REQUIRED_KEY));
        }

        return Optional.empty();
    }

    private Optional<ValidationError> validateDateMin(
            JsonNode value,
            JsonNode datePickerInstruction,
            String key,
            Integer row,
            boolean internal) {
        return validateDateTime(
                value, datePickerInstruction, key, row, DATE_MIN_KEY, internal, DATE_MIN_CHECK);
    }

    private Optional<ValidationError> validateDateMax(
            JsonNode value,
            JsonNode datePickerInstruction,
            String key,
            Integer row,
            boolean internal) {
        return validateDateTime(
                value, datePickerInstruction, key, row, DATE_MAX_KEY, internal, DATE_MAX_CHECK);
    }

    private Optional<ValidationError> validateMinLength(
            JsonNode value,
            JsonNode validationInstruction,
            String key,
            Integer row,
            boolean internal) {
        return validateLength(
                value, validationInstruction, key, row, MIN_LENGTH_KEY, internal, MIN_LENGTH_CHECK);
    }

    private Optional<ValidationError> validateMaxLength(
            JsonNode value,
            JsonNode validationInstruction,
            String key,
            Integer row,
            boolean internal) {
        return validateLength(
                value, validationInstruction, key, row, MAX_LENGTH_KEY, internal, MAX_LENGTH_CHECK);
    }

    private Optional<ValidationError> validateMin(
            JsonNode value,
            JsonNode validationInstruction,
            String key,
            Integer row,
            boolean internal) {
        if (validationInstruction.has(MIN_KEY)) {
            return validateNumberValue(
                    value, validationInstruction, key, row, MIN_KEY, internal, MIN_CHECK);
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateMax(
            JsonNode value,
            JsonNode validationInstruction,
            String key,
            Integer row,
            boolean internal) {
        if (validationInstruction.has(MAX_KEY)) {
            return validateNumberValue(
                    value, validationInstruction, key, row, MAX_KEY, internal, MAX_CHECK);
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateMinRowCount(
            JsonNode value, JsonNode validationInstruction, String key, Integer row) {
        if (validationInstruction.has(MIN_LENGTH_KEY)) {
            return validateRowCountValue(
                    value, validationInstruction, key, row, MIN_LENGTH_KEY, MIN_ROW_COUNT_CHECK);
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateMaxRowCount(
            JsonNode value, JsonNode validationInstruction, String key, Integer row) {
        if (validationInstruction.has(MAX_LENGTH_KEY)) {
            return validateRowCountValue(
                    value, validationInstruction, key, row, MAX_LENGTH_KEY, MAX_ROW_COUNT_CHECK);
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validatePattern(
            JsonNode value,
            JsonNode validationInstruction,
            String key,
            Integer row,
            boolean internal) {
        final String regexPattern =
                extractValidationInstruction(validationInstruction, PATTERN_KEY, internal)
                        .map(JsonNode::asText)
                        .orElse(null);
        if (regexPattern != null) {
            return Optional.ofNullable(value)
                    .map(JsonNode::asText)
                    .map(valueString -> valueString.matches(regexPattern))
                    .map(valid -> valid ? null : cfeateError(key, row, PATTERN_KEY));
        }

        return Optional.empty();
    }

    private Optional<ValidationError> checkJsonnata(
            Object jsonataData,
            JsonNode validationInstruction,
            String key,
            Integer row,
            boolean internal) {
        final String jsonataPattern =
                extractValidationInstruction(validationInstruction, JSONATA_KEY, internal)
                        .map(JsonNode::asText)
                        .orElse(null);
        if (jsonataPattern != null) {
            String compiledJsonataPattern =
                    Optional.ofNullable(row)
                            .map(rowInt -> rowInt.toString())
                            .map(rowString -> jsonataPattern.replace(ROW_NUM_PLACEHOLER, rowString))
                            .orElse(jsonataPattern);

            compiledJsonataPattern = compiledJsonataPattern.replace(CURRENT_DATE_PLACEHOLER, Instant.now().toString());

            return Optional.ofNullable(jsonataEngine.validate(jsonataData, compiledJsonataPattern))
                    .map(jsonataResult -> Boolean.parseBoolean(jsonataResult))
                    .map(valid -> !valid ? cfeateError(key, row, JSONATA_KEY) : null);
        }

        return Optional.empty();
    }

    private Optional<ValidationError> validateLength(
            JsonNode value,
            JsonNode validationInstruction,
            String hashKey,
            Integer row,
            String validationKey,
            boolean internal,
            BiFunction<String, Integer, Boolean> lengthCheck) {
        final Integer length =
                extractValidationInstruction(validationInstruction, validationKey, internal)
                        .map(JsonNode::asInt)
                        .orElse(null);
        if (length != null) {
            return Optional.ofNullable(value)
                    .map(JsonNode::asText)
                    .map(valueString -> lengthCheck.apply(valueString, length))
                    .map(valid -> valid ? null : cfeateError(hashKey, row, validationKey));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateDateTime(
            JsonNode value,
            JsonNode datePickerInstruction,
            String hashKey,
            Integer row,
            String validationKey,
            boolean internal,
            BiFunction<Instant, Instant, Boolean> dateCheck) {
        final String currDate =
                extractValidationInstruction(datePickerInstruction, validationKey, internal)
                        .map(JsonNode::asText)
                        .orElse(null);
        if (currDate != null) {
            return Optional.ofNullable(value)
                    .map(JsonNode::asText)
                    .map(valueString -> dateCheck.apply(Instant.parse(valueString), Instant.parse(currDate)))
                    .map(valid -> valid ? null : cfeateError(hashKey, row, validationKey));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateNumberValue(
            JsonNode value,
            JsonNode validationInstruction,
            String hashKey,
            Integer row,
            String validationKey,
            boolean internal,
            BiFunction<Double, Double, Boolean> numberCheck) {
        final Double val =
                extractValidationInstruction(validationInstruction, validationKey, internal)
                        .map(JsonNode::asDouble)
                        .orElse(null);
        return Optional.ofNullable(value)
                .map(JsonNode::asDouble)
                .map(valueNumber -> numberCheck.apply(valueNumber, val))
                .map(valid -> valid ? null : cfeateError(hashKey, row, validationKey));
    }

    private Optional<ValidationError> validateRowCountValue(
            JsonNode value,
            JsonNode validationInstruction,
            String hashKey,
            Integer row,
            String validationKey,
            BiFunction<Integer, Integer, Boolean> rowCountCheck) {
        final Integer val =
                Optional.ofNullable(getExternalValidations(validationInstruction, validationKey))
                        .map(JsonNode::asInt)
                        .orElse(null);
        return Optional.ofNullable(value)
                .map(JsonNode::size)
                .map(valueNumber -> rowCountCheck.apply(valueNumber, val))
                .map(valid -> valid ? null : cfeateError(hashKey, row, validationKey));
    }

    private ValidationError cfeateError(String key, Integer row, String violation) {
        final ValidationError error = new ValidationError().key(key).violation(violation);
        return error;
    }

    private Optional<JsonNode> extractValidationInstruction(
            JsonNode validationInstruction, String validationKey, boolean internal) {
        return internal ? Optional.ofNullable(
                getInternalValidations(validationInstruction, validationKey))
                : Optional.ofNullable(
                getExternalValidations(validationInstruction, validationKey));
    }

    private JsonNode getExternalValidations(
            JsonNode validationInstruction, String validationKey) {
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
            JsonNode validationInstruction, String validationKey) {
        return Optional.ofNullable(validationInstruction.get(CUSTOM_KEY))
                .map(customTag -> customTag.get(INTERNAL_KEY))
                .map(internalTag -> internalTag.get(validationKey))
                .orElse(null);
    }
}