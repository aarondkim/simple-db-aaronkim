package simpledb;

import java.io.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * The LockManager class handles the acquisition and release of locks on pages by transactions in SimpleDB.
 * It implements methods for acquiring shared and exclusive locks, upgrading locks, releasing locks, detecting deadlocks, 
 * and managing transaction pages and waiting transactions.
 */
public class LockManager {

    private HashMap<PageId, Set<TransactionId>> sharedLocks;
    private HashMap<PageId, TransactionId> exclusiveLocks;
    private HashMap<TransactionId, Set<PageId>> transactionPages;
    private HashMap<TransactionId, PageId> transactionWait;

    LockManager(){
        sharedLocks = new HashMap<>();
        exclusiveLocks = new HashMap<>();
        transactionPages = new HashMap<>();
        transactionWait = new HashMap<>();
    }

    /**
     * Acquires a lock on a specified page by a given transaction with the specified permissions.
     * @param tid The transaction ID requesting the lock.
     * @param pid The page ID on which the lock is requested.
     * @param perm The permissions requested by the transaction.
     * @throws TransactionAbortedException Thrown if a deadlock is detected or if the transaction is aborted.
     */
    public synchronized void acquireLock(TransactionId tid, PageId pid, Permissions perm) throws TransactionAbortedException {
        // New transaction trying to get lock on a page

        if(!transactionPages.containsKey(tid)){
            transactionPages.put(tid, new HashSet<PageId>());
        }

        transactionWait.put(tid,pid);

        if (perm == Permissions.READ_ONLY) {
            // transaction with exclusive lock on this page already

            while(exclusiveLocks.containsKey(pid)){

                // already have a read/write lock on this page
                if(exclusiveLocks.get(pid).equals(tid)){
                    return;
                }

                //deadlockDetector(tid, pid);

                try{
                    wait(300);
                } catch(InterruptedException e){
                    e.printStackTrace();
                }
                if(exclusiveLocks.containsKey(pid) && new Random().nextBoolean()){
                    throw new TransactionAbortedException();
                }
            }

            if (!sharedLocks.containsKey(pid)) {
                sharedLocks.put(pid, new HashSet<TransactionId>());
            }
            Set<TransactionId> ts = sharedLocks.get(pid);
            ts.add(tid);

        } else {
            // check if we can upgrade before we need to wait
            upgradeLock(tid,pid);
            while(exclusiveLocks.containsKey(pid) || sharedLocks.containsKey(pid)) {

                // already have a read write lock on this page
                if(exclusiveLocks.containsKey(pid)){
                    if(exclusiveLocks.get(pid).equals(tid)){
                        return;
                    }
                }
                // Check if we can upgrade our lock
                upgradeLock(tid,pid);

                //deadlockDetector(tid, pid);

                try {
                    wait(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // If we are the only shared lock holder of this page at this point, we can upgrade
                upgradeLock(tid,pid);
                if((exclusiveLocks.containsKey(pid) || sharedLocks.containsKey(pid)) && new Random().nextBoolean()){
                    throw new TransactionAbortedException();
                }

            }

            exclusiveLocks.put(pid,tid);

        }
        transactionPages.get(tid).add(pid);
        transactionWait.remove(tid);
    }

    /**
     * Upgrades a shared lock to an exclusive lock if possible.
     * @param tid The transaction ID requesting the upgrade.
     * @param pid The page ID for which the lock is being upgraded.
     */
    public void upgradeLock(TransactionId tid, PageId pid){
        if(sharedLocks.containsKey(pid) && !exclusiveLocks.containsKey(pid)){
            Set<TransactionId> ts = sharedLocks.get(pid);
            if (ts.contains(tid) && ts.size() == 1) {
                sharedLocks.remove(pid);
                exclusiveLocks.put(pid,tid);
                return;
            }
        }
    }

    // Thought process for our initial dependency cycle deadlock detection, later opted out for time out approach

    // t1 wants p2
    // what transactions have p2
    // t2 has p2
    // what does t2 want
    // t2 wants p1
    // who has p1? if t1, deadlock

    //
    // getTransactions(p2)
    // for transactions of p2
    //    page = transactionWait.get(transaction)
    //    set = getTransactions(page)
    //    if ogTransaction in set: deadlock

    // t1 wants p2
    // what transactions have p2
    // t2 has p2
    // what does t2 want
    // t2 wants p3
    // what transactions have p3
    // t3 and t4 have p3
    // t3 or t4 want p1
    // how has p1? if t1, deadlock

    //
    // getTransactions(p2)
    // for transactions of p2
    //    p3 <- transactionWait.get(transaction)
    //    getTransactions(p3)
    //    for transactions of p3 (ie t3, t4)
    //      page <- transactionWait.get(transaction)
    //      if ogTransaction in getTransactions(page): deadlock


    // Helper function for our initial dependency cycle deadlock detection
    /**
     * Retrieves the transactions holding locks on a specified page.
     * @param pid The page ID for which transactions are to be retrieved.
     * @return A set of transaction IDs holding locks on the page.
     */
    public Set<TransactionId> getTransactions(PageId pid){
        if(sharedLocks.containsKey(pid)){
            return sharedLocks.get(pid);
        }
        Set<TransactionId> tid = new HashSet<>();
        if(exclusiveLocks.containsKey(pid)){
            tid.add(exclusiveLocks.get(pid));
        }
        return tid;
    }

    // We do not actually end up using this but this is how we initially tried to solve deadlocks
    /**
     * Detects deadlocks by checking for circular dependencies between transactions waiting for locks.
     * @param ogTransaction The original transaction requesting the lock.
     * @param waitPage The page for which the transaction is waiting.
     * @throws TransactionAbortedException Thrown if a deadlock is detected.
     */
    private void deadlockDetector(TransactionId ogTransaction, PageId waitPage)
            throws TransactionAbortedException {
        Set<TransactionId> pageOwners = getTransactions(waitPage);
        for(TransactionId ownerTid : pageOwners){
            if(transactionWait.containsKey(ownerTid)){
                PageId ownerWait = transactionWait.get(ownerTid);
                if(getTransactions(ownerWait).contains(ogTransaction)){
                    transactionWait.remove(ogTransaction);
                    throw new TransactionAbortedException();
                } else {
                    deadlockDetector(ogTransaction, ownerWait);
                }
            }
        }
    }

    /**
     * Releases the lock held by a transaction on a specified page.
     * @param tid The transaction ID releasing the lock.
     * @param pid The page ID for which the lock is being released.
     */
    public synchronized void releaseLock(TransactionId tid, PageId pid)
            {
        if(exclusiveLocks.containsKey(pid)){
            exclusiveLocks.remove(pid);
        } else if(sharedLocks.containsKey(pid)){
            Set<TransactionId> ts = sharedLocks.get(pid);
            ts.remove(tid);
            if(ts.isEmpty()){
                sharedLocks.remove(pid);
            }
        }
        notifyAll();
    }

    /**
     * Releases all locks held by a specified transaction.
     * @param tid The transaction ID for which locks are to be released.
     */
    public synchronized void releaseTransaction(TransactionId tid){
        Set<PageId> pages = transactionPages.get(tid);
        if(pages != null) {
            for (PageId pid : pages) {
                releaseLock(tid, pid);
            }
        }
    }

    /**
     * Retrieves the set of page IDs accessed by a specified transaction.
     * @param tid The transaction ID for which page IDs are to be retrieved.
     * @return A set of page IDs accessed by the transaction.
     */
    public synchronized Set<PageId> pagesTouched (TransactionId tid){
        return transactionPages.get(tid);
    }

    /**
     * Checks if a transaction holds a lock on a specified page.
     * @param tid The transaction ID to be checked.
     * @param p The page ID for which the lock ownership is to be checked.
     * @return True if the transaction holds a lock on the page, false otherwise.
     */
    public synchronized boolean hasLock(TransactionId tid, PageId p){
        if(exclusiveLocks.containsKey(p)){
            return tid.equals(exclusiveLocks.get(p));
        } else if(sharedLocks.containsKey(p)){
            Set<TransactionId> ts = sharedLocks.get(p);
            return ts.contains(tid);
        } else {
            return false;
        }
    }



}
