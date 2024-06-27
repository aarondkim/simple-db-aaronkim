package simpledb;

import java.util.*;

/**
 * The Join operator implements the relational join operation.
 */
public class Join extends Operator {

    private static final long serialVersionUID = 1L;

    private JoinPredicate p;
    private OpIterator child1;
    private OpIterator child2;
    private Tuple outerTuple;

    /**
     * Constructor. Accepts two children to join and the predicate to join them
     * on
     * 
     * @param p
     *            The predicate to use to join the children
     * @param child1
     *            Iterator for the left(outer) relation to join
     * @param child2
     *            Iterator for the right(inner) relation to join
     */
    public Join(JoinPredicate p, OpIterator child1, OpIterator child2) {
        this.p = p;
        this.child1 = child1;
        this.child2 = child2;
        this.outerTuple = null;
    }

    public JoinPredicate getJoinPredicate() {
        return p;
    }

    /**
     * @return
     *       the field name of join field1. Should be quantified by
     *       alias or table name.
     * */
    public String getJoinField1Name() {
        return child1.getTupleDesc().getFieldName(p.getField1());
    }

    /**
     * @return
     *       the field name of join field2. Should be quantified by
     *       alias or table name.
     * */
    public String getJoinField2Name() {
        return child2.getTupleDesc().getFieldName(p.getField2());
    }

    /**
     * @see simpledb.TupleDesc#merge(TupleDesc, TupleDesc) for possible
     *      implementation logic.
     */
    public TupleDesc getTupleDesc() {
        return TupleDesc.merge(child1.getTupleDesc(), child2.getTupleDesc());
    }

    public void open() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        child1.open();
        child2.open();
        super.open();
    }

    public void close() {
        child1.close();
        child2.close();
        super.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        child1.rewind();
        child2.rewind();
    }

    /**
     * Returns the next tuple generated by the join, or null if there are no
     * more tuples. Logically, this is the next tuple in r1 cross r2 that
     * satisfies the join predicate. There are many possible implementations;
     * the simplest is a nested loops join.
     * <p>
     * Note that the tuples returned from this particular implementation of Join
     * are simply the concatenation of joining tuples from the left and right
     * relation. Therefore, if an equality predicate is used there will be two
     * copies of the join attribute in the results. (Removing such duplicate
     * columns can be done with an additional projection operator if needed.)
     * <p>
     * For example, if one tuple is {1,2,3} and the other tuple is {1,5,6},
     * joined on equality of the first column, then this returns {1,2,3,1,5,6}.
     * 
     * @return The next matching tuple.
     * @see JoinPredicate#filter
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // TupleDesc for joined tuples to return
        TupleDesc desc = this.getTupleDesc();
        boolean done = false;

        while(!done){

            if(child1.hasNext() && outerTuple == null){
                outerTuple = child1.next();
                child2.rewind();
            }

            while(child2.hasNext()){
                Tuple innerTuple = child2.next();
                if(p.filter(outerTuple, innerTuple)){
                    return tupleMerge(outerTuple, innerTuple, desc);
                }
            }

            //Outer loop tuple has gone through all the tuples in inner loop
            outerTuple = null;

            //Done when every outer loop tuple has matched with all inner loop tuples
            if(!child1.hasNext() && !child2.hasNext()){
                done = true;
            }
        }
        return null;
    }

    private Tuple tupleMerge(Tuple t1, Tuple t2, TupleDesc td){

        assert (t1.getTupleDesc().numFields() + t2.getTupleDesc().numFields()) == td.numFields();

        Tuple merged = new Tuple(td);
        Iterator<Field> f1 = t1.fields();
        Iterator<Field> f2 = t2.fields();
        //Field number index used for setField
        int idx = 0;

        //Copy in fields of tuple1 for merged tuple
        while(f1.hasNext()){
            merged.setField(idx, f1.next());
            idx++;
        }

        //Copy in fields of tuple2 for merged tuple
        while(f2.hasNext()){
            merged.setField(idx, f2.next());
            idx++;
        }
        return merged;
    }

    @Override
    public OpIterator[] getChildren() {
        OpIterator[] children = new OpIterator[2];
        children[0] = child1;
        children[1] = child2;
        return children;
    }

    @Override
    public void setChildren(OpIterator[] children) {
        child1 = children[0];
        child2 = children[1];
    }

}
