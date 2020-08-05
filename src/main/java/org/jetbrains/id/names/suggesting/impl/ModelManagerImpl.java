package org.jetbrains.id.names.suggesting.impl;

import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.api.ModelManager;

import java.util.HashMap;
import java.util.Map;

public class ModelManagerImpl implements ModelManager {
    private final Map<String, IdNamesSuggestingModelRunner> myModelRunners = new HashMap<>();

    @Override
    public IdNamesSuggestingModelRunner getModelRunner(String name){
        return myModelRunners.get(name);
    }

    @Override
    public void putModelRunner(String name, IdNamesSuggestingModelRunner modelRunner) {
        myModelRunners.put(name, modelRunner);
    }
}
