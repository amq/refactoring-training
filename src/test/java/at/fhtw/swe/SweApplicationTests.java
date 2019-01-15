package at.fhtw.swe;

import at.fhtw.swe.controller.ValidationController;
import at.fhtw.swe.model.ValidationRequestBody;
import at.fhtw.swe.model.ValidationError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SweApplicationTests {

    @Autowired
    private transient ValidationController sut;

    @Test
    public void basicComponents() {
        final ValidationRequestBody body = new ValidationRequestBody();
        body.setTemplate(getTestFileAsString("/forms/completeForm.json"));
        body.setData(getTestFileAsString("/forms/completeData.json"));

        final ResponseEntity<Set<ValidationError>> responseEntity = sut.postExternalValidation(body);

        assertThat(responseEntity.getBody()).containsExactlyInAnyOrder(
                new ValidationError().key("firstName").violation("minLength"),
                new ValidationError().key("lastName").violation("maxLength"),
                new ValidationError().key("email").violation("pattern"),
                new ValidationError().key("birthdate").violation("minDate"),
                new ValidationError().key("birthdate").violation("jsonata"),
                new ValidationError().key("number").violation("required")
        );
    }

    @Test
    public void grid() {
        final ValidationRequestBody body = new ValidationRequestBody();
        body.setTemplate(getTestFileAsString("/forms/gridForm.json"));
        body.setData(getTestFileAsString("/forms/gridData.json"));

        final ResponseEntity<Set<ValidationError>> responseEntity = sut.postExternalValidation(body);

        assertThat(responseEntity.getBody()).containsExactlyInAnyOrder(
                new ValidationError().key("weitereBeteiligtePersonen").violation("minLength"),
                new ValidationError().key("nachname").violation("jsonata")
        );
    }

    @Test
    public void internalAndExternal() {
        final ValidationRequestBody body = new ValidationRequestBody();
        body.setTemplate(getTestFileAsString("/forms/internalValidationForm.json"));
        body.setData(getTestFileAsString("/forms/internalValidationData.json"));

        final ResponseEntity<Set<ValidationError>> internalErrors = sut.postInternalValidation(body);
        final ResponseEntity<Set<ValidationError>> externalErrors = sut.postExternalValidation(body);

        final ValidationError[] validationErrors = {
                new ValidationError().key("email").violation("minLength"),
                new ValidationError().key("email").violation("pattern"),
                new ValidationError().key("birthdate").violation("jsonata"),
                new ValidationError().key("birthdate").violation("minDate")
        };

        assertThat(externalErrors.getBody()).containsExactlyInAnyOrder(validationErrors);
        assertThat(internalErrors.getBody()).containsExactlyInAnyOrder(validationErrors);
    }

    public String getTestFileAsString(final String testFile) {
        try {
            final InputStreamReader inputStreamReader = new InputStreamReader(
                    this.getClass().getResourceAsStream(testFile),
                    StandardCharsets.UTF_8.name());

            final char[] buffer = new char[4096];
            final StringBuilder sb = new StringBuilder();
            for (int len; (len = inputStreamReader.read(buffer)) > 0; ) {
                sb.append(buffer, 0, len);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("error in reading test-file: " + testFile, e);
        }
    }

}
