package simpledb;

import java.util.*;

/**
 * SeqScan is an implementation of a sequential scan access method that reads
 * each tuple of a table in no particular order (e.g., as they are laid out on
 * disk).
 */
public class SeqScan implements OpIterator {

    private static final long serialVersionUID = 1L;

    private TransactionId tid;
    private int tableid;
    private String tableAlias;
    private DbFileIterator tableItr;
    private boolean open;

    /**
     * Creates a sequential scan over the specified table as a part of the
     * specified transaction.
     *
     * @param tid
     *            The transaction this scan is running as a part of.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public SeqScan(TransactionId tid, int tableid, String tableAlias) {
        this.tid = tid;
        this.tableid = tableid;
        this.tableAlias = tableAlias;
        this.open = false;
    }

    /**
     * @return
     *       return the table name of the table the operator scans. This should
     *       be the actual name of the table in the catalog of the database
     * */
    public String getTableName() {
        return Database.getCatalog().getTableName(this.tableid);
    }

    /**
     * @return Return the alias of the table this operator scans.
     * */
    public String getAlias()
    {
        return this.tableAlias;
    }

    /**
     * Reset the tableid, and tableAlias of this operator.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public void reset(int tableid, String tableAlias) {
        this.tableid = tableid;
        this.tableAlias = tableAlias;
    }

    public SeqScan(TransactionId tid, int tableId) {
        this(tid, tableId, Database.getCatalog().getTableName(tableId));
    }

    public void open() throws DbException, TransactionAbortedException {
        this.open = true;
        this.tableItr = Database.getCatalog().getDatabaseFile(this.tableid).iterator(this.tid);
        this.tableItr.open();
    }

    /**
     * Returns the TupleDesc with field names from the underlying HeapFile,
     * prefixed with the tableAlias string from the constructor. This prefix
     * becomes useful when joining tables containing a field(s) with the same
     * name.  The alias and name should be separated with a "." character
     * (e.g., "alias.fieldName").
     *
     * @return the TupleDesc with field names from the underlying HeapFile,
     *         prefixed with the tableAlias string from the constructor.
     */
    public TupleDesc getTupleDesc() {
        TupleDesc tupleDesc = Database.getCatalog().getTupleDesc(this.tableid);
        Iterator<TupleDesc.TDItem> tdItems = tupleDesc.iterator();
        int size = tupleDesc.numFields();
        Type[] typeArr = new Type[size];
        String[] fieldArr = new String[size];
        int i = 0;
        while(tdItems.hasNext()){
            TupleDesc.TDItem item = tdItems.next();
            typeArr[i] = item.fieldType;
            fieldArr[i] = this.tableAlias + "." + item.fieldName;
            i++;
        }
        return new TupleDesc(typeArr, fieldArr);
    }

    public boolean hasNext() throws TransactionAbortedException, DbException {
        if(!this.open){
            throw new IllegalStateException();
        }
        return this.tableItr.hasNext();
    }

    public Tuple next() throws NoSuchElementException,
            TransactionAbortedException, DbException {
        if(!this.open){
            throw new IllegalStateException();
        }
        if(!this.hasNext()){
            throw new NoSuchElementException();
        }
        return this.tableItr.next();
    }

    public void close() {
        this.open = false;
        this.tableItr.close();
    }

    public void rewind() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        if(!this.open){
            throw new IllegalStateException();
        }
        this.open();
    }
}
