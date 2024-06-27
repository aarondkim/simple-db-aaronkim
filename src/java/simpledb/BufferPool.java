package simpledb;

import java.io.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 * 
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;

    private final int numPages;

    private ConcurrentHashMap<PageId, Page> pageCache;
    private LockManager lock;
    private Set<TransactionId> activeTids;
    
    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;


    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        pageCache = new ConcurrentHashMap<>();
        lock = new LockManager();
        activeTids = new HashSet<>();
    }
    
    public static int getPageSize() {
      return pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
    	BufferPool.pageSize = pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
    	BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {

        lock.acquireLock(tid, pid, perm);
        activeTids.add(tid);

        //lock.acquireLock(tid, pid, perm);
        if(pageCache.containsKey(pid)){
            return pageCache.get(pid);
        } else {
            if(pageCache.size() == this.numPages){
                evictPage();
                Page newPage = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                pageCache.put(pid, newPage);
                return newPage;
            } else {
                Page newPage = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                pageCache.put(pid, newPage);
                return newPage;
            }
        }

    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void releasePage(TransactionId tid, PageId pid) {
        lock.releaseLock(tid, pid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        transactionComplete(tid, true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        return lock.hasLock(tid, p);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
        throws IOException {
        Set<PageId> pages = pageCache.keySet();

        if(commit) {
            for (PageId pid : pages) {
                Page page = pageCache.get(pid);
                TransactionId dirty = page.isDirty();
                if(tid.equals(dirty)){
                    //flushPage(pid);
                    Database.getLogFile().logWrite(tid, page.getBeforeImage(), page);
                    Database.getLogFile().force();
                }
                page.setBeforeImage();
            }
        } else {
            for (PageId pid : pages) {
                Page page = pageCache.get(pid);
                TransactionId dirty = page.isDirty();
                if(tid.equals(dirty)){
                    Page ogPage = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                    ogPage.markDirty(false, null);
                    pageCache.put(pid, ogPage);

                }
            }
        }
        lock.releaseTransaction(tid);
        activeTids.remove(tid);

        /*
        Set<PageId> pages = lock.pagesTouched(tid);
        if(commit) {
            for(PageId pid : pages){
                if(pageCache.get(pid).isDirty() != null){
                    flushPage(pid);
                }
            }
            lock.releaseTransaction(tid);
        } else{
            for(PageId pid : pages){
                Page ogPage = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                pageCache.put(pid, ogPage);
            }
        }
         */
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other 
     * pages that are updated (Lock acquisition is not needed for lab2). 
     * May block if the lock(s) cannot be acquired.
     * 
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        HeapFile table = (HeapFile) Database.getCatalog().getDatabaseFile(tableId);
        // Insert tuple into the right table ie HeapFile, which returns pages modified
        ArrayList<Page> dirtyPages = table.insertTuple(tid, t);

        // For the modified pages, we mark as dirty and replace the page with dirtied page in cache
        for(Page page : dirtyPages){
            page.markDirty(true, tid);
            pageCache.put(page.getId(), page);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        int tableId = t.getRecordId().getPageId().getTableId();
        HeapFile table = (HeapFile) Database.getCatalog().getDatabaseFile(tableId);
        // Delete tuple into the right table ie HeapFile, which returns pages modified
        ArrayList<Page> dirtyPages = table.deleteTuple(tid, t);

        // For the modified pages, we mark as dirty and replace the page with dirtied page in cache
        for(Page page : dirtyPages){
            page.markDirty(true, tid);
            pageCache.put(page.getId(), page);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        for(PageId pid : pageCache.keySet()){
            flushPage(pid);
        }

    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.
        
        Also used by B+ tree files to ensure that deleted pages
        are removed from the cache so they can be reused safely
    */
    public synchronized void discardPage(PageId pid) {
        pageCache.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        HeapPage page = (HeapPage) pageCache.get(pid);
        TransactionId tid = page.isDirty();

        // isDirty returns transaction that has dirtied the page, null if it isn't dirty
        if(tid != null){
            // Get file where dirty page belongs
            HeapFile file = (HeapFile) Database.getCatalog().getDatabaseFile(pid.getTableId());

            if(activeTids.contains(tid)) {
                Database.getLogFile().logWrite(tid, page.getBeforeImage(), page);
                Database.getLogFile().force();
            }

            // Write the modified page to data file
            file.writePage(page);
            // Page no longer dirty
            page.markDirty(false, tid);
        }
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {

        // Choose the random idx's page to come out of the cache to be evicted
        for(PageId pid : pageCache.keySet()){
//            if(pageCache.get(pid).isDirty() == null){
//                pageCache.remove(pid);
//                return;
//            }

            pageCache.remove(pid);
            break;
        }
        //throw new DbException("all dirty :(");
    }

}
