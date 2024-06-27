package simpledb;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */

// Same logic as IntegerAggregator, less cases. More comments there.
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    private final int gbfield;
    private final Type gbfieldtype;
    private final int afield;
    private final Op aop;
    private String gbfieldname;
    private HashMap<Field, Integer> groupCount;

    /**
     * Aggregate constructor
     * @param gbfield the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield the 0-based index of the aggregate field in the tuple
     * @param what aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        if(what != Op.COUNT){
            throw new IllegalArgumentException();
        }
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.aop = what;
        groupCount = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        Field gField;
        if(gbfield == Aggregator.NO_GROUPING){
            gField = new IntField(0);
            gbfieldname = null;
        } else {
            gField = tup.getField(gbfield);
            gbfieldname = tup.getTupleDesc().getFieldName(gbfield);
        }

        if(!groupCount.containsKey(gField)){
            groupCount.put(gField,1);
        } else{
            int newCount = groupCount.get(gField) + 1;
            groupCount.put(gField, newCount);
        }

    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal,
     *   aggregateVal) if using group, or a single (aggregateVal) if no
     *   grouping. The aggregateVal is determined by the type of
     *   aggregate specified in the constructor.
     */
    public OpIterator iterator() {

        ArrayList<Tuple> tuples = new ArrayList<>();
        Type[] type;
        String[] fName;
        TupleDesc td;

        if(gbfield == Aggregator.NO_GROUPING){
            type = new Type[]{Type.INT_TYPE};
            fName = new String[]{"count"};
        } else {
            type = new Type[]{gbfieldtype, Type.INT_TYPE};
            fName = new String[]{gbfieldname, "count"};
        }
        td = new TupleDesc(type, fName);

        for(Field group : groupCount.keySet()){
            Tuple t = new Tuple(td);
            int count = this.groupCount.get(group);
            if(gbfield == Aggregator.NO_GROUPING){
                t.setField(0, new IntField(count));
            } else {
                t.setField(0, group);
                t.setField(1, new IntField(count));
            }
            tuples.add(t);
        }

        return new TupleIterator(td, tuples);
    }

}
