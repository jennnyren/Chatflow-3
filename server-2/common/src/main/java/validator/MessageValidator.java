package validator;

import model.ChatMessage;

import java.time.Instant;

public class MessageValidator {
    public static ValidationResult validate(ChatMessage message) {
        if (message == null) {
            return new ValidationResult(false, "Message cannot be null.");
        }

        ValidationResult usernameResult = validateUsername(message.getUsername());
        if (!usernameResult.isValid()) {
            return usernameResult;
        }

        ValidationResult userIdResult = validateUserId(message.getUserId());
        if (!userIdResult.isValid()) {
            return userIdResult;
        }

        ValidationResult messageResult = validateMessage(message.getMessage());
        if (!messageResult.isValid()) {
            return messageResult;
        }

        ValidationResult typeResult = validateMessageType(message.getMessageType());
        if (!typeResult.isValid()) {
            return typeResult;
        }

        return new ValidationResult(true, "Valid");
    }

    private static ValidationResult validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            return new ValidationResult(false, "Username cannot be null or empty.");
        }
        if (username.length() < 3 || username.length() >20) {
            return new ValidationResult(false, "Username must be between 3 and 20 characters.");
        }
        return new ValidationResult(true, "Valid");
    }

    private static ValidationResult validateUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return new ValidationResult(false, "UserId cannot be null or empty.");
        }
        try {
            int id = Integer.parseInt(userId);
            if (id < 1 || id > 100000) {
                return new ValidationResult(false, "UserId must be between 1 and 100000.");
            }
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "UserId must be a number.");
        }
        return new ValidationResult(true, "Valid");
    }

    private static ValidationResult validateMessage(String message) {
        if (message == null || message.isEmpty()) {
            return new ValidationResult(false, "Message cannot be null or empty.");
        }
        if (message.length() < 1 || message.length() > 500) {
            return new ValidationResult(false, "Message must be between 1 and 500 characters.");
        }
        return new ValidationResult(true, "Valid");
    }

    private static ValidationResult validateMessageType(String messageType) {
        if (messageType == null || messageType.isEmpty()) {
            return new ValidationResult(false, "Message type cannot be null or empty.");
        }
        if (!messageType.equals("JOIN") && !messageType.equals("LEAVE") && !messageType.equals("TEXT")) {
            return new ValidationResult(false, "Message type must be or \"JOIN\", \"TEXT\" or \"LEAVE\".");
        }
        return new ValidationResult(true, "Valid");
    }
}
