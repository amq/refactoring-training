package at.fhtw.swe;

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
    private ValidationController sut;

    @Test
    public void basicComponents() {
        ValidationRequestBody body = new ValidationRequestBody();
        body.setTemplate(getTestFileAsString("/forms/completeForm.json"));
        body.setData(getTestFileAsString("/forms/completeData.json"));

        ResponseEntity<Set<ValidationError>> responseEntity = sut.postExternalValidation(body);

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
        ValidationRequestBody body = new ValidationRequestBody();
        body.setTemplate(getTestFileAsString("/forms/gridForm.json"));
        body.setData(getTestFileAsString("/forms/gridData.json"));

        ResponseEntity<Set<ValidationError>> responseEntity = sut.postExternalValidation(body);

        assertThat(responseEntity.getBody()).containsExactlyInAnyOrder(
                new ValidationError().key("weitereBeteiligtePersonen").violation("minLength"),
                new ValidationError().key("nachname").violation("jsonata")
        );
    }

    @Test
    public void internalAndExternal() {
        ValidationRequestBody body = new ValidationRequestBody();
        body.setTemplate(getTestFileAsString("/forms/internalValidationForm.json"));
        body.setData(getTestFileAsString("/forms/internalValidationData.json"));

        ResponseEntity<Set<ValidationError>> internalErrors = sut.postInternalValidation(body);
        ResponseEntity<Set<ValidationError>> externalErrors = sut.postExternalValidation(body);

        ValidationError[] validationErrors = {
                new ValidationError().key("email").violation("minLength"),
                new ValidationError().key("email").violation("pattern"),
                new ValidationError().key("birthdate").violation("jsonata"),
                new ValidationError().key("birthdate").violation("minDate")
        };

        assertThat(externalErrors.getBody()).containsExactlyInAnyOrder(validationErrors);
        assertThat(internalErrors.getBody()).containsExactlyInAnyOrder(validationErrors);
    }

    public String getTestFileAsString(String testFile) {
        try {
            InputStreamReader inputStreamReader = new InputStreamReader(
                    this.getClass().getResourceAsStream(testFile),
                    StandardCharsets.UTF_8.name());

            char[] buffer = new char[4096];
            StringBuilder sb = new StringBuilder();
            for (int len; (len = inputStreamReader.read(buffer)) > 0; )
                sb.append(buffer, 0, len);
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("error in reading test-file: " + testFile);
        }
    }

}
