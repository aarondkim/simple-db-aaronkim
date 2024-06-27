package simpledb;

import java.util.*;

/**
 * Filter is an operator that implements a relational select.
 */
public class Filter extends Operator {

    private static final long serialVersionUID = 1L;

    private final Predicate p;
    private OpIterator child;
    private final TupleDesc td;

    /**
     * Constructor accepts a predicate to apply and a child operator to read
     * tuples to filter from.
     * 
     * @param p
     *            The predicate to filter tuples with
     * @param child
     *            The child operator
     */
    public Filter(Predicate p, OpIterator child) {
        this.p = p;
        this.child = child;
        this.td = child.getTupleDesc();
    }

    public Predicate getPredicate() {
        return this.p;
    }

    public TupleDesc getTupleDesc() {
        return this.td;
    }

    public void open() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        child.open();
        super.open();
    }

    public void close() {
        super.close();
        child.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        child.rewind();
    }

    /**
     * AbstractDbIterator.readNext implementation. Iterates over tuples from the
     * child operator, applying the predicate to them and returning those that
     * pass the predicate (i.e. for which the Predicate.filter() returns true.)
     * 
     * @return The next tuple that passes the filter, or null if there are no
     *         more tuples
     * @see Predicate#filter
     */
    protected Tuple fetchNext() throws NoSuchElementException,
            TransactionAbortedException, DbException {
        while(child.hasNext()){
            Tuple next = child.next();
            if(p.filter(next)){
                return next;
            }
        }
        return null;
    }

    @Override
    public OpIterator[] getChildren() {
        OpIterator[] children = new OpIterator[1];
        children[0] = this.child;
        return children;
    }

    @Override
    public void setChildren(OpIterator[] children) {
        this.child = children[0];
    }

}
