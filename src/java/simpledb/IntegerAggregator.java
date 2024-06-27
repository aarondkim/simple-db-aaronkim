package simpledb;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    private final int gbfield;
    private final Type gbfieldtype;
    private final int afield;
    private final Op aop;
    private String gbfieldname;
    private HashMap<Field, Integer> groupAgg;
    private HashMap<Field, Integer> groupCount;

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.aop = what;
        // Merges tuples and maps to aggregate calculation
        this.groupAgg = new HashMap<>();
        // Merges tuples and maps to count of tuples in group
        this.groupCount = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // Value of aggregate field of this tuple
        int aggField = ((IntField) tup.getField(afield)).getValue();
        Field gField;

        if(gbfield == Aggregator.NO_GROUPING){
            // If no grouping, all tuples just hashed/merged to this field key
            gField = new IntField(0);
            gbfieldname = null;
        } else {
            gField = tup.getField(gbfield);
            gbfieldname = tup.getTupleDesc().getFieldName(gbfield);
        }

        if(!this.groupAgg.containsKey(gField)){
            // new group, count of 1
            groupCount.put(gField,1);
            // new group, value just aggregate field of that tuple
            this.groupAgg.put(gField, aggField);
        } else {

            int newCount = groupCount.get(gField) + 1;
            // aggregate value of this group before merging new tuple
            int oldAgg = groupAgg.get(gField);
            groupCount.put(gField, newCount);

            if(aop == Op.COUNT){
                groupAgg.put(gField, newCount);
            } else if(aop == Op.MIN){
                groupAgg.put(gField,Math.min(oldAgg, aggField));
            } else if(aop == Op.MAX){
                groupAgg.put(gField,Math.max(oldAgg, aggField));
            } else {
                // for sum, sum_count, avg, and sc_avg
                groupAgg.put(gField, oldAgg + aggField);
            }
        }

    }

    /**
     * Create a OpIterator over group aggregate results.
     * 
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {

        ArrayList<Tuple> tuples = new ArrayList<>();
        Type[] type;
        String[] fName;
        TupleDesc td;

        //Define TupleDesc of aggregate tuples depending on whether there is grouping or not
        if(gbfield == Aggregator.NO_GROUPING){
            if(this.aop != Op.SUM_COUNT) {
                type = new Type[]{Type.INT_TYPE};
                fName = new String[]{this.aop.toString()};
            } else {
                // started defining different TupleDesc for SUM_COUNT but not used
                type = new Type[]{Type.INT_TYPE, Type.INT_TYPE};
                fName = new String[]{"sum", "count"};
            }
        } else {
            if(this.aop != Op.SUM_COUNT) {
                type = new Type[]{this.gbfieldtype, Type.INT_TYPE};
                fName = new String[]{this.gbfieldname, this.aop.toString()};
                td = new TupleDesc(type, fName);
            } else{
                // started defining different TupleDesc for SUM_COUNT but not used
                type = new Type[]{this.gbfieldtype, Type.INT_TYPE, Type.INT_TYPE};
                fName = new String[]{this.gbfieldname, "sum", "count"};
            }
        }
        td = new TupleDesc(type, fName);

        // Iterate through merged groups and make new aggregate tuple for each merged group
        // add them to Tuple list so that they can be used to return iterator of the aggregate tuples
        for(Field group : groupAgg.keySet()){
            Tuple t = new Tuple(td);
            int aggVal = this.groupAgg.get(group);
            if(aop != Op.AVG && aop != Op.SUM_COUNT && aop != Op.SC_AVG){
                //If the aggregate op is not AVG, SUM_COUNT, SC_AVG, no more calculations needed
                if(gbfield == Aggregator.NO_GROUPING){
                    t.setField(0, new IntField(aggVal));
                } else{
                    t.setField(0, group);
                    t.setField(1, new IntField(aggVal));
                }
            } else {
                //Currently just considering AVG operator (no case for SC_AVG or SUM_COUNT yet)
                //Calculate average for aggregate value field
                int count = groupCount.get(group);
                if(aop == Op.AVG){
                    int avg = aggVal / count;
                    if(gbfield == Aggregator.NO_GROUPING){
                        t.setField(0, new IntField(avg));
                    } else {
                        t.setField(0, group);
                        t.setField(1, new IntField(avg));
                    }
                }
            }

            tuples.add(t);
        }
        return new TupleIterator(td, tuples);
    }

}
