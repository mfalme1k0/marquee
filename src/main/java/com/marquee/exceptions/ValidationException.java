
package com.marquee.exceptions;

import java.util.List;

//invalid input detected by service layer
public final class ValidationException extends RuntimeException {

    private final List<String> details;

    public ValidationException(List<String> details) {
        super("Validation failed");

        if (details == null || details.isEmpty()) {
            throw new IllegalArgumentException(
                    "Validation details must not be empty"
            );
        }

        this.details = List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
