package me.shedaniel.linkie;

public class InvalidPermissionException extends RuntimeException {
    
    public InvalidPermissionException() {
    }
    
    public InvalidPermissionException(String message) {
        super(message);
    }
    
}
