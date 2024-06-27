package simpledb;

import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    private File f;
    private TupleDesc td;

    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        this.f = f;
        this.td = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return this.f;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        return this.f.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return this.td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        int pgNum = pid.getPageNumber();
        int pgSize = BufferPool.getPageSize();
        try {
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            int offset = pgNum * pgSize;
            byte[] data = new byte[pgSize];
            raf.seek(offset);
            raf.read(data);
            raf.close();
            return new HeapPage((HeapPageId) pid, data);
        } catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        int offset = page.getId().getPageNumber() * BufferPool.getPageSize();
        RandomAccessFile raf;
        raf = new RandomAccessFile(f, "rw");
        raf.seek(offset);
        raf.write(page.getPageData());
        raf.close();
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) this.f.length() / BufferPool.getPageSize();
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {

        ArrayList<Page> modified = new ArrayList<>();

        // Iterate through pages in this file to see if there is room in any to insert tuple
        for(int i = 0; i < this.numPages(); i++){
            HeapPageId pid = new HeapPageId(this.getId(), i);
            HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
            if(page.getNumEmptySlots() != 0){
                page.insertTuple(t);
                modified.add(page);
                return modified;
            } else{
                Database.getBufferPool().releasePage(tid, pid);
            }
        }

        // At this point, no pages in this file have space to insert tuple, need new page in this file
        // Make a new page with empty data for the next page number, write that blank page into this file
        HeapPageId pid = new HeapPageId(this.getId(), this.numPages());
        HeapPage newPage = new HeapPage(pid, HeapPage.createEmptyPageData());
        writePage(newPage);
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
        page.insertTuple(t);
        modified.add(page);
        return modified;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        ArrayList<Page> modified = new ArrayList<>();
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, t.getRecordId().getPageId(),
                Permissions.READ_WRITE);
        page.deleteTuple(t);
        modified.add(page);
        return modified;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {


        class HeapFileIterator implements DbFileIterator {

            private final TransactionId tId;
            private final HeapFile heapFile;
            private final int numPages;
            private int pgNo;
            private Iterator<Tuple> tuples;
            private boolean open;

            HeapFileIterator(HeapFile heapFile){
                this.tId = tid;
                this.heapFile = heapFile;
                this.numPages = this.heapFile.numPages();
                this.tuples = null;
                this.open = false;
            }

            @Override
            public void open() throws DbException, TransactionAbortedException {
                this.open = true;
                this.pgNo = 0;
                updatePage();
            }

            @Override
            public boolean hasNext() throws DbException, TransactionAbortedException {
                if(!open){
                    return false;
                }

                if(tuples.hasNext()){
                    return true;
                } else{
                    while((pgNo < numPages - 1) && !tuples.hasNext()){
                        pgNo++;
                        updatePage();
                    }
                    return tuples.hasNext();
                }
            }

            @Override
            public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
                /*
                if(this.hasNext()){
                    while(!tuples.hasNext()) {
                        if(this.pgNo >= this.numPages){
                            break;
                        }
                        this.pgNo++;
                        updatePage();
                    }
                    return this.tuples.next();
                } else {
                    throw new NoSuchElementException();
                }
                 */
                if(this.hasNext()){
                    return tuples.next();
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void rewind() throws DbException, TransactionAbortedException {
                boolean prevState = this.open;
                this.open();
                this.open = prevState;
            }

            @Override
            public void close() {
                this.open = false;
            }

            private void updatePage() throws TransactionAbortedException, DbException {
                HeapPageId pid = new HeapPageId(this.heapFile.getId(), this.pgNo);
                HeapPage page = (HeapPage) Database.getBufferPool().getPage(tId, pid, Permissions.READ_ONLY);
                this.tuples = page.iterator();
            }
        }

        return new HeapFileIterator(this);
    }

}

