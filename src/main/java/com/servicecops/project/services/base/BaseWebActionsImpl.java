package com.servicecops.project.services.base;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.utils.OperationReturnObject;

public interface BaseWebActionsImpl {
    OperationReturnObject switchActions(String action, JSONObject request);
    OperationReturnObject process(String action, JSONObject request);
}
