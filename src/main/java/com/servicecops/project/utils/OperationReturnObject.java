package com.servicecops.project.utils;

import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class OperationReturnObject extends OperationReturn{
    Object returnObject;

    public void setReturnCodeAndReturnObject(Integer returnCode, Object returnObject){
        this.returnObject = returnObject;
        this.returnCode = returnCode;
    }
    public void setCodeAndMessageAndReturnObject(Integer returnCode, String message, Object returnObject){
        this.returnObject = returnObject;
        this.returnCode = returnCode;
        this.returnMessage = message;
    }

    public OperationReturnObject success(String smsSentSuccessfully) {
        OperationReturnObject operationReturnObject = new OperationReturnObject();
        operationReturnObject.setCodeAndMessageAndReturnObject(0, smsSentSuccessfully, null);
        return operationReturnObject;
    }

    public OperationReturnObject success(String smsSentSuccessfully, Object returnObject) {
        OperationReturnObject operationReturnObject = new OperationReturnObject();
        operationReturnObject.setCodeAndMessageAndReturnObject(0, smsSentSuccessfully, returnObject);
        return operationReturnObject;
    }
}
