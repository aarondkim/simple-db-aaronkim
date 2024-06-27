package simpledb;

import java.util.HashMap;

/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {


    // Inner private class used to make the intervals for each bucket
    // Just contains the inclusive lower and upper interval the bucket
    // is determining frequency for
    private class Interval{
        public final int lower;
        public final int upper;
        public Interval(int lower, int upper){
            this.lower = lower;
            this.upper = upper;
        }

        public int getWidth(){
            return upper - lower + 1;
        }

    }

    Interval[] buckets;
    Integer[] frequency;
    int min;
    int max;
    int bucketSize;
    int nBuckets;

    /**
     * Create a new IntHistogram.
     * 
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * 
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * 
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't 
     * simply store every value that you see in a sorted list.
     * 
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */
    public IntHistogram(int buckets, int min, int max) {
        // Corresponding buckets and frequency based on array index
        // ie bucket[0] is bucket for the first interval and frequency[0]
        // is frequency for that first interval
    	this.frequency = new Integer[buckets];
        this.buckets = new Interval[buckets];

        this.min = min;
        this.max = max;
        // constant number of buckets
        this.nBuckets = buckets;

        // If there is some overflow in range of values and number of buckets
        // we are to put it in the last bucket. To not make extra bucket, range
        // of interval for each bucket accounted for depending on overflow.
        int overflow = (max - min + 1) % buckets;
        int range = (max - min - overflow) / buckets;
        int lower = min;
        int upper;

        bucketSize = range + 1;

        // Go through and assign bucket intervals and initialize frequency of
        // corresponding buckets with 0
        for(int i = 0; i < buckets; i++){
            if(i == buckets-1){
                upper = max;
            } else {
                upper = lower + range;
            }
            this.buckets[i] = new Interval(lower,upper);
            this.frequency[i] = 0;
            lower += range + 1;
        }
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
        if(v>max || v<min){
            return;
        }

        // Index which bucket value will go
        int bucketIndex = (v - min) / bucketSize;
        // If it is a value that falls in the range/interval of our last
        // overflow bucket, adjust index to avoid index out of bounds exception
        if(bucketIndex == nBuckets){
            bucketIndex--;
        }

        frequency[bucketIndex]++;
    }
    private double equalSelectivity(Predicate.Op op, int v, int ntups){
        if(v < min || v > max){
            return 0;
        }

        int bucketIndex = (v - min) / bucketSize;
        if(bucketIndex == nBuckets){
            bucketIndex--;
        }

        // selectivity/fraction of the appropriate bucket that would equal v
        // relative to all values in histogram
        return ((double) frequency[bucketIndex] / buckets[bucketIndex].getWidth()) / ntups;
    }

    private double gtSelectivity(Predicate.Op op, int v, int ntups){
        if(v < min){
            return 1;
        }
        if(v >= max){
            return 0;
        }

        int bucketIndex = (v - min) / bucketSize;
        if(bucketIndex == nBuckets){
            bucketIndex--;
        }

        // Find variables used to find selectivity/fraction of the appropriate bucket
        // for v that is greater than specified v relative to all values in histogram
        Interval b = buckets[bucketIndex];
        double b_part = ((double) (b.upper - v)) / b.getWidth();
        double b_f = ((double) frequency[bucketIndex]) / ntups;

        // Selectivity/fraction of buckets that represent intervals greater than the
        // bucket where v would belong, relative to all values in histogram
        double b_greater = 0;
        for(int i = bucketIndex + 1; i < nBuckets; i++){
            if(buckets[i].upper > max){
                break;
            }
            b_greater += frequency[i];
        }
        return (b_f * b_part) + (b_greater / ntups);
    }

    private double ltSelectivity(Predicate.Op op, int v, int ntups){
        if(v <= min){
            return 0;
        }
        if(v > max){
            return 1;
        }

        int bucketIndex = (v - min) / bucketSize;
        if(bucketIndex == nBuckets){
            bucketIndex--;
        }

        // Find variables used to find selectivity/fraction of the appropriate bucket
        // for v that is less than specified v relative to all values in histogram
        Interval b = buckets[bucketIndex];
        double b_part = ((double) (v - b.lower)) / b.getWidth();
        double b_f = ((double) frequency[bucketIndex]) / ntups;

        // Selectivity/fraction of buckets that represent intervals less than the
        // bucket where v would belong, relative to all values in histogram
        double b_lesser = 0;
        for(int i = bucketIndex - 1; i >= 0; i--){
            b_lesser += frequency[i];
        }
        return (b_f * b_part) + (b_lesser / ntups);
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * 
     * For example, if "op" is "GREATER_THAN" and "v" is 5, 
     * return your estimate of the fraction of elements that are greater than 5.
     * 
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {
        int ntups = 0;
        for (Integer integer : frequency) {
            ntups += integer;
        }

        if(op == Predicate.Op.EQUALS){
            return equalSelectivity(op,v,ntups);
        } else if(op == Predicate.Op.GREATER_THAN) {
            return gtSelectivity(op,v,ntups);
        } else if(op == Predicate.Op.LESS_THAN){
            return ltSelectivity(op,v,ntups);
        } else if(op == Predicate.Op.GREATER_THAN_OR_EQ){
            //combined selectivity equals and greater than
            return equalSelectivity(op,v,ntups) + gtSelectivity(op,v,ntups);
        } else if(op == Predicate.Op.LESS_THAN_OR_EQ){
            //combined selectivity equals and less than
            return equalSelectivity(op,v,ntups) + ltSelectivity(op,v,ntups);
        }else if(op == Predicate.Op.NOT_EQUALS){
            //selectivity would be complement of equals selectivity
            return 1 - equalSelectivity(op,v,ntups);
        }
        return 0;
    }
    
    /**
     * @return
     *     the average selectivity of this histogram.
     *     
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity()
    {
        // some code goes here
        return 1.0;
    }
    
    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        // some code goes here
        return null;
    }
}
