package analyse.timeline;

import global.DriftMeasurement;
import models.Model;
import weka.core.Instance;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by loongkuan on 14/12/2016.
 */
public class NaiveMovingWindow extends TimelineAnalysis{
    //TODO: Be able to measure different types of drift and name accordingly
    private int windowSize;
    // Maps hash for attribute subset to array of distance for each drift type
    private double[][] distanceMap;
    private double[] allAttributeDistanceMap;
    private boolean growing;

    public NaiveMovingWindow(int windowSize, Model referenceModel, DriftMeasurement[] driftMeasurementType) {
        this.windowSize = windowSize;
        this.driftMeasurementTypes = driftMeasurementType;
        this.previousModel= referenceModel.copy();
        this.currentModel = referenceModel.copy();

        this.attributeSubsets = referenceModel.getAllAttributeSubsets();
        this.distanceMap = new double[(int)Math.pow(this.currentModel.getAllInstances().numAttributes(),this.attributeSubsets.get(0).length + 1)][DriftMeasurement.values().length];
        this.allAttributeDistanceMap = new double[DriftMeasurement.values().length];
        this.growing = true;

        this.currentIndex = -1;

        this.driftPoints = new HashMap<>();
        this.driftValues = new HashMap<>();
        for (DriftMeasurement type : this.driftMeasurementTypes) {
            this.driftPoints.put(type, new ArrayList<>());
            this.driftValues.put(type, new ArrayList<>());
        }
    }

    private int hashAttributeSubset(int[] attributeSubset) {
        int base = this.currentModel.getAllInstances().numAttributes();
        int hash = 0;
        for (int i = 0; i < attributeSubset.length; i++) {
            hash += attributeSubset[i] * (Math.pow(base,i));
        }
        return hash;
    }

    public void addInstance(Instance instance) {
        this.currentIndex += 1;
        if (previousModel.size() < this.windowSize) {
            this.previousModel.addInstance(instance);
        }
        else if (currentModel.size() < this.windowSize) {
            this.currentModel.addInstance(instance);
        }
        else if (this.growing) {
            this.growing = false;
            for (int[] attributeSubset : this.attributeSubsets) {
                int hash = hashAttributeSubset(attributeSubset);
                for (DriftMeasurement driftMeasurement : DriftMeasurement.values()) {
                    this.distanceMap[hash][driftMeasurement.ordinal()] = super.getDistance(attributeSubset, driftMeasurement);
                }
            }
        }
        else {
            Instance instanceMid = this.currentModel.getAllInstances().get(0);
            Instance instanceTail = this.previousModel.getAllInstances().get(0);
            for (int[] attributeSubset : this.attributeSubsets) {
                int hash = hashAttributeSubset(attributeSubset);
                for (DriftMeasurement driftMeasurement : DriftMeasurement.values()) {
                    // Remove probability component of instance to add and remove
                    this.distanceMap[hash][driftMeasurement.ordinal()] -= this.getSingleDistance(
                            instance, attributeSubset, driftMeasurement);
                    this.distanceMap[hash][driftMeasurement.ordinal()] -= this.getSingleDistance(
                            instanceMid, attributeSubset, driftMeasurement);
                    this.distanceMap[hash][driftMeasurement.ordinal()] -= this.getSingleDistance(
                            instanceTail, attributeSubset, driftMeasurement);
                }
            }
            this.previousModel.removeInstance(0);
            this.previousModel.addInstance(this.currentModel.getAllInstances().get(0));
            this.currentModel.removeInstance(0);
            this.currentModel.addInstance(instance);

            for (int[] attributeSubset : this.attributeSubsets) {
                int hash = hashAttributeSubset(attributeSubset);
                for (DriftMeasurement driftMeasurement : DriftMeasurement.values()) {
                    this.distanceMap[hash][driftMeasurement.ordinal()] += this.getSingleDistance(
                            instance, attributeSubset, driftMeasurement);
                    this.distanceMap[hash][driftMeasurement.ordinal()] += this.getSingleDistance(
                            instanceMid, attributeSubset, driftMeasurement);
                    this.distanceMap[hash][driftMeasurement.ordinal()] += this.getSingleDistance(
                            instanceTail, attributeSubset, driftMeasurement);
                }
            }
        }
        if (previousModel.size() >= windowSize && currentModel.size() >= windowSize) {
            this.addDistance(this.currentIndex - this.windowSize);
        }
    }

    @Override
    protected double getDistance(int[] attributeSubset, DriftMeasurement driftMeasurementType) {
        int hash = this.hashAttributeSubset(attributeSubset);
        return this.distanceMap[hash][driftMeasurementType.ordinal()];
    }

    private double getSingleDistance(Instance instance, int[] attributeSubset, DriftMeasurement driftMeasurement) {
        double result = -1.0f;
        switch (driftMeasurement) {
            case COVARIATE:
                result = this.previousModel.findPv(instance, attributeSubset) -
                        this.currentModel.findPv(instance, attributeSubset);
                break;
            case JOINT:
                result = this.previousModel.findPvy(instance, attributeSubset, (int)instance.classValue()) -
                        this.currentModel.findPvy(instance, attributeSubset, (int)instance.classValue());
                break;
            case LIKELIHOOD:
                result = this.previousModel.findPvgy(instance, attributeSubset, (int)instance.classValue()) -
                        this.currentModel.findPvgy(instance, attributeSubset, (int)instance.classValue());
                break;
            case POSTERIOR:
                result = this.previousModel.findPygv(instance, attributeSubset, (int)instance.classValue()) -
                        this.currentModel.findPygv(instance, attributeSubset, (int)instance.classValue());
                break;

        }
        result = (1/2) * Math.abs(result);
        return result;
    }
}
