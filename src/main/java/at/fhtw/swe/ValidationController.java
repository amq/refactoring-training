package at.fhtw.swe;

import at.fhtw.swe.model.ValidationError;
import at.fhtw.swe.service.Validator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
public class ValidationController {

    private final transient Validator validator;

    public ValidationController(final Validator validator) {
        this.validator = validator;
    }

    @GetMapping("/healthCheck")
    public ResponseEntity<Boolean> getHealthCheck(){
        return ResponseEntity.ok(true);
    }

    @PostMapping("/internal")
    public ResponseEntity<Set<ValidationError>> postInternalValidation(final @RequestBody() ValidationRequestBody body) {
        final Set<ValidationError> validationErrors = this.validator.validateForm(body.getTemplate(), body.getData(), true);
        return ResponseEntity.ok(validationErrors);
    }


    @PostMapping("/external")
    public ResponseEntity<Set<ValidationError>> postExternalValidation(final @RequestBody() ValidationRequestBody body) {
        final Set<ValidationError> validationErrors = this.validator.validateForm(body.getTemplate(), body.getData(), false);
        return ResponseEntity.ok(validationErrors);
    }
}
