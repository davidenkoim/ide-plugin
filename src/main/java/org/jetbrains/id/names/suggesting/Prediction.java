package org.jetbrains.id.names.suggesting;

public class Prediction{
    private final String myName;
    private final double myProbability;
    private final double myPriority;
    private int myIndex = -1;

    public Prediction(String myName, double myProbability, double myPriority) {
        this.myName = myName;
        this.myProbability = myProbability;
        this.myPriority = myPriority;
    }

    public Prediction(int myIndex, String myName, double myProbability) {
        this.myIndex = myIndex;
        this.myName = myName;
        this.myProbability = myProbability;
        myPriority = 1.;
    }

    public Prediction(String myName, double myProbability) {
        this.myName = myName;
        this.myProbability = myProbability;
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

    public int getIndex() {
        return myIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Prediction){
            if (getIndex() == -1 && ((Prediction) obj).getIndex() == -1){
                return getName().equals(((Prediction) obj).getName());
            }
            return getIndex() == ((Prediction) obj).getIndex();
        }
        return super.equals(obj);
    }
}
