package com.reedelk.file.exception;

import com.reedelk.runtime.api.exception.ESBException;

public class FileDeleteException extends ESBException {

    public FileDeleteException(String message, Throwable exception) {
        super(message, exception);
    }
}