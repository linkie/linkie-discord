package me.shedaniel.linkie;

public class InvalidUsageException extends RuntimeException {
    
    public InvalidUsageException() {
    }
    
    public InvalidUsageException(String message) {
        super(message);
    }
    
}
