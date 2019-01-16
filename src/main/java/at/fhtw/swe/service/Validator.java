package at.fhtw.swe.service;

import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;

import at.fhtw.swe.model.ValidationError;
import at.fhtw.swe.model.ValidationValue;
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
    private static final String VALIDATE_KEY = "validate";
    private static final String ROW_NUM_PLACEHOLER = "@eval:rownum@";
    private static final String CURRENT_DATE_PLACEHOLER = "@date:now@";
    private static final String JSONATA_KEY = "jsonata";
    private static final String GRID_INPUT_KEYS_QUERY =
            "$.." + COMPONENT_TAG + "[?].." + COMPONENT_TAG + "[?]." + COMPONENT_KEY;
    private static final String INPUTS_TO_VALIDATE_QUERY = "$.." + COMPONENT_TAG + "[?]";

    private final transient JsonataEngine jsonataEngine;
    private final transient JsonPath getInputsToValidate = JsonPath.compile(INPUTS_TO_VALIDATE_QUERY, filter(where("@." + VALIDATE_KEY).exists(true)));
    private final transient JsonPath getInputKeysInsideGrids = JsonPath.compile(GRID_INPUT_KEYS_QUERY, filter(where("@." + TYPE_KEY).eq(TYPE_GRID)), filter(where("@." + VALIDATE_KEY).exists(true)));

    private final transient SingleValueValidator singleValueValidator = new SingleValueValidator();

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
            final String form, final String formdata, final boolean internal) {
        final DocumentContext formContext = JsonPath.parse(form);
        final DocumentContext dataContext = JsonPath.parse(formdata);

        final Object jsonataData = jsonataEngine.parseData(formdata);
        final ArrayNode inputsWithValidations = getInputsWithValidations(formContext);
        final Set<String> gridInputs = getInputKeysInsideGrids(formContext);
        final Set<ValidationError> errors = new HashSet<>();
        ValidationValue validationValue = new ValidationValue();

        for (JsonNode input : inputsWithValidations) {
            final String id = input.get(COMPONENT_KEY).asText();
            final ArrayNode inspectedValue = dataContext.read("$.." + id, ArrayNode.class);

            validationValue.setInstruction(input.get(VALIDATE_KEY));
            validationValue.setKey(id);
            validationValue.setType(input.get(TYPE_KEY).asText());
            validationValue.setInternal(internal);

            if (gridInputs.contains(id)) {
                errors.addAll(validateFormGrid(jsonataData, validationValue, inspectedValue));
            } else {
                errors.addAll(validateFormNormal(jsonataData, validationValue, inspectedValue));
            }
        }

        return errors;
    }

    private Set<ValidationError> validateFormGrid(
            final Object jsonataData,
            final ValidationValue validationValue,
            final ArrayNode inspectedValue) {
        Set<ValidationError> errors = new HashSet<>();

        for (int row = 0; row < inspectedValue.size(); row++) {
            validationValue.setValue(inspectedValue.get(row));
            validationValue.setRow(row);
            errors.addAll(
                    singleValueValidator.validateSingleValue(validationValue));
            checkJsonnata(jsonataData, validationValue)
                    .ifPresent(error -> errors.add(error));
        }

        return errors;
    }

    private Set<ValidationError> validateFormNormal(
            final Object jsonataData,
            final ValidationValue validationValue,
            final ArrayNode inspectedValue) {

        Set<ValidationError> errors = new HashSet<>();
        validationValue.setValue(inspectedValue.get(0));
        validationValue.setRow(null);

        errors.addAll(
                singleValueValidator.validateSingleValue(validationValue));
        checkJsonnata(jsonataData, validationValue)
                .ifPresent(error -> errors.add(error));

        return errors;
    }

    private ArrayNode getInputsWithValidations(final DocumentContext formContext) {
        return formContext.read(getInputsToValidate, ArrayNode.class);
    }

    private Set<String> getInputKeysInsideGrids(final DocumentContext formContext) {
        return formContext.read(getInputKeysInsideGrids, Set.class);
    }

    private Optional<ValidationError> checkJsonnata(
            final Object jsonataData,
            final ValidationValue validationValue) {
        final String jsonataPattern =
                singleValueValidator.extractValidationInstruction(validationValue.getInstruction(), JSONATA_KEY, validationValue.isInternal())
                        .map(JsonNode::asText)
                        .orElse(null);
        if (jsonataPattern != null) {
            String compiledJsonataPattern =
                    Optional.ofNullable(validationValue.getValue())
                            .map(rowInt -> rowInt.toString())
                            .map(rowString -> jsonataPattern.replace(ROW_NUM_PLACEHOLER, rowString))
                            .orElse(jsonataPattern);

            compiledJsonataPattern = compiledJsonataPattern.replace(CURRENT_DATE_PLACEHOLER, Instant.now().toString());

            return Optional.ofNullable(jsonataEngine.validate(jsonataData, compiledJsonataPattern))
                    .map(jsonataResult -> Boolean.parseBoolean(jsonataResult))
                    .map(valid -> !valid ? cfeateError(validationValue.getKey(), validationValue.getRow(), JSONATA_KEY) : null);
        }

        return Optional.empty();
    }

    private ValidationError cfeateError(final String key, final Integer row, final String violation) {
        return new ValidationError().key(key).violation(violation);
    }
}