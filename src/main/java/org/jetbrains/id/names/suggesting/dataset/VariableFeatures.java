package org.jetbrains.id.names.suggesting.dataset;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class VariableFeatures implements Serializable {
    public final String variable;
    public final List<String> ngrams = new ArrayList<>();
    public final List<Object> otherFeatures = new ArrayList<>();

    public VariableFeatures(String variable){
        this.variable = variable;
    }

    public VariableFeatures(String variable, List<UsageFeatures> usages) {
        this.variable = variable;
        for (UsageFeatures usage: usages){
            this.ngrams.add(usage.ngram);
            this.otherFeatures.add(usage.otherFeatures.values());
        }
    }
}

class UsageFeatures implements Serializable{
    public final String ngram;
    public final HashMap<String, Integer> otherFeatures = new LinkedHashMap<>();

    public UsageFeatures(String ngram, int distanceToDeclaration){
        this.ngram = ngram;
        this.otherFeatures.put("distanceToDeclaration", distanceToDeclaration);
    }
}
