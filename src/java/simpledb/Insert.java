package simpledb;

import java.io.IOException;

/**
 * Inserts tuples read from the child operator into the tableId specified in the
 * constructor
 */
public class Insert extends Operator {

    private static final long serialVersionUID = 1L;

    private final TransactionId t;
    private final int tableId;
    private OpIterator child;
    private Tuple tuple;

    /**
     * Constructor.
     *
     * @param t
     *            The transaction running the insert.
     * @param child
     *            The child operator from which to read tuples to be inserted.
     * @param tableId
     *            The table in which to insert tuples.
     * @throws DbException
     *             if TupleDesc of child differs from table into which we are to
     *             insert.
     */
    public Insert(TransactionId t, OpIterator child, int tableId)
            throws DbException {
        this.t = t;
        this.child = child;
        this.tableId = tableId;
        this.tuple = null;
    }

    public TupleDesc getTupleDesc() {
        return new TupleDesc(new Type[]{Type.INT_TYPE});
    }

    public void open() throws DbException, TransactionAbortedException {
        super.open();
        child.open();
    }

    public void close() {
        super.close();
        child.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        close();
        open();
    }

    /**
     * Inserts tuples read from child into the tableId specified by the
     * constructor. It returns a one field tuple containing the number of
     * inserted records. Inserts should be passed through BufferPool. An
     * instances of BufferPool is available via Database.getBufferPool(). Note
     * that insert DOES NOT need check to see if a particular tuple is a
     * duplicate before inserting it.
     *
     * @return A 1-field tuple containing the number of inserted records, or
     *         null if called more than once.
     * @see Database#getBufferPool
     * @see BufferPool#insertTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // First time being called so tuple is null (if not first time called, should return null)
        if(tuple == null){

            // Define TupleDesc according to javadoc
            Type[] type = new Type[]{Type.INT_TYPE};
            String[] fName = new String[]{"num inserts"};
            int count = 0;
            TupleDesc td = new TupleDesc(type, fName);
            this.tuple = new Tuple(td);

            // While child operator has more tuples to insert, keep inserting tuple
            while(child.hasNext()){
                Tuple tp = child.next();
                try {
                    Database.getBufferPool().insertTuple(this.t, this.tableId, tp);
                } catch(IOException e){
                    System.out.println("insert failed");
                    e.printStackTrace();
                }
                count++;
            }
            this.tuple.setField(0, new IntField(count));
            return this.tuple;
        } else {
            return null;
        }
    }

    @Override
    public OpIterator[] getChildren() {
        return new OpIterator[] { this.child };
    }

    @Override
    public void setChildren(OpIterator[] children) {
        if (this.child!=children[0])
        {
            this.child = children[0];
        }
    }
}
