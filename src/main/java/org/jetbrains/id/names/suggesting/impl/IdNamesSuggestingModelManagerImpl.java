package org.jetbrains.id.names.suggesting.impl;

import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;

import java.util.HashMap;
import java.util.Map;

public class IdNamesSuggestingModelManagerImpl implements IdNamesSuggestingModelManager {
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
