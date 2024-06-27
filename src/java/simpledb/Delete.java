package simpledb;

import java.io.IOException;

/**
 * The delete operator. Delete reads tuples from its child operator and removes
 * them from the table they belong to.
 */
public class Delete extends Operator {

    private static final long serialVersionUID = 1L;

    private final TransactionId t;
    private OpIterator child;
    private Tuple tuple;

    /**
     * Constructor specifying the transaction that this delete belongs to as
     * well as the child to read from.
     * 
     * @param t
     *            The transaction this delete runs in
     * @param child
     *            The child operator from which to read tuples for deletion
     */
    public Delete(TransactionId t, OpIterator child) {
        this.t = t;
        this.child = child;
        this.tuple = null;
    }

    public TupleDesc getTupleDesc() {
        return new TupleDesc(new Type[]{Type.INT_TYPE});
    }

    public void open() throws DbException, TransactionAbortedException {
        child.open();
        super.open();
    }

    public void close() {
        child.close();
        super.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        close();
        open();
    }

    /**
     * Deletes tuples as they are read from the child operator. Deletes are
     * processed via the buffer pool (which can be accessed via the
     * Database.getBufferPool() method.
     * 
     * @return A 1-field tuple containing the number of deleted records.
     * @see Database#getBufferPool
     * @see BufferPool#deleteTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // First time being called so tuple is null (if not first time called, should return null)
        if(tuple == null){

            // Define TupleDesc according to javadoc
            Type[] type = new Type[]{Type.INT_TYPE};
            String[] fName = new String[]{"num deletes"};
            int count = 0;
            TupleDesc td = new TupleDesc(type, fName);
            this.tuple = new Tuple(td);

            // While child operator has more tuples to delete, keep deleting tuple
            while(child.hasNext()){
                Tuple tp = child.next();
                try {
                    Database.getBufferPool().deleteTuple(this.t, tp);
                } catch(IOException e){
                    System.out.println("delete failed");
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
