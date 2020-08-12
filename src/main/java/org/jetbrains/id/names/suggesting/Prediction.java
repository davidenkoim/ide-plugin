package org.jetbrains.id.names.suggesting;

public class Prediction{
    private final String myName;
    private final double myProbability;
    private final double myPriority;

    public Prediction(String name, double probability, double priority) {
        myName = name;
        myProbability = probability;
        myPriority = priority;
    }

    public Prediction(String name, double probability) {
        myName = name;
        myProbability = probability;
        myPriority = 1.;
    }

    public String getName() {
        return myName;
    }

    public double getProbability() {
        return myProbability;
    }

    public double getPriority() {
        return myPriority;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Prediction){
            return myName.equals(((Prediction) obj).getName());
        }
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return myName + ':' + myProbability;
    }
}
