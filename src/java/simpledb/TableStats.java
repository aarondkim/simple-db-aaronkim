package simpledb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TableStats represents statistics (e.g., histograms) about base tables in a
 * query. 
 * 
 * This class is not needed in implementing lab1 and lab2.
 */
public class TableStats {

    private static final ConcurrentHashMap<String, TableStats> statsMap = new ConcurrentHashMap<String, TableStats>();

    static final int IOCOSTPERPAGE = 1000;

    public static TableStats getTableStats(String tablename) {
        return statsMap.get(tablename);
    }

    public static void setTableStats(String tablename, TableStats stats) {
        statsMap.put(tablename, stats);
    }
    
    public static void setStatsMap(HashMap<String,TableStats> s)
    {
        try {
            java.lang.reflect.Field statsMapF = TableStats.class.getDeclaredField("statsMap");
            statsMapF.setAccessible(true);
            statsMapF.set(null, s);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public static Map<String, TableStats> getStatsMap() {
        return statsMap;
    }

    public static void computeStatistics() {
        Iterator<Integer> tableIt = Database.getCatalog().tableIdIterator();

        System.out.println("Computing table stats.");
        while (tableIt.hasNext()) {
            int tableid = tableIt.next();
            TableStats s = new TableStats(tableid, IOCOSTPERPAGE);
            setTableStats(Database.getCatalog().getTableName(tableid), s);
        }
        System.out.println("Done.");
    }

    /**
     * Number of bins for the histogram. Feel free to increase this value over
     * 100, though our tests assume that you have at least 100 bins in your
     * histograms.
     */
    static final int NUM_HIST_BINS = 100;
    private int ntups;
    private Object[] histograms;
    private HeapFile file;
    private int ioCostPerPage;

    /**
     * Create a new TableStats object, that keeps track of statistics on each
     * column of a table
     * 
     * @param tableid
     *            The table over which to compute statistics
     * @param ioCostPerPage
     *            The cost per page of IO. This doesn't differentiate between
     *            sequential-scan IO and disk seeks.
     */
    public TableStats(int tableid, int ioCostPerPage) {
        // For this function, you'll have to get the
        // DbFile for the table in question,
        // then scan through its tuples and calculate
        // the values that you need.
        // You should try to do this reasonably efficiently, but you don't
        // necessarily have to (for example) do everything
        // in a single scan of the table.
        // some code goes here

        // Retrieve the file for the table we are interested in
        file = (HeapFile) Database.getCatalog().getDatabaseFile(tableid);
        this.ioCostPerPage = ioCostPerPage;
        TransactionId tid = new TransactionId();
        DbFileIterator itr = file.iterator(tid);
        TupleDesc td = file.getTupleDesc();

        int numFields = td.numFields();
        ntups = 0;
        histograms = new Object[numFields];

        // For each field number, we map to an array where the first
        // element represents the min and the second represents the
        // max of the values of the field for that field number
        HashMap<Integer, Integer[]> ranges = new HashMap<>();

        // Iterate through all tuples of the table
        try{
            itr.open();
            while(itr.hasNext()){
                Tuple t = itr.next();
                ntups++;

                // For each field of each tuple
                for(int i = 0; i < numFields; i++){
                    Field f = t.getField(i);
                    Type type = f.getType();

                    // If we have an int field, make updates to min
                    // and max values for that field as we go through
                    // new tuples
                    if(type == Type.INT_TYPE){
                        IntField intField = (IntField) f;
                        int v = intField.getValue();
                        if(!ranges.containsKey(i)){
                            Integer[] minMax = {v, v};
                            ranges.put(i, minMax);
                        } else {
                            Integer[] minMax = ranges.get(i);
                            if(minMax[0] > v){
                                minMax[0] = v;
                            } else if(minMax[1] < v){
                                minMax[1] = v;
                            }
                        }
                    } else{
                        // If field is of type string, no min max
                        // kept track of
                        if(!ranges.containsKey(i)){
                            ranges.put(i,null);
                        }
                    }
                }
            }

            // At this point, we have the found the min and max for each field for
            // all tuples of this table. Can now construct the histograms.

            // For each field number, get the min and max array, and then construct the
            // histogram for corresponding field number index in histograms array. If min
            // and max is null, means it is a string field and so StringHistogram constructed
            for(int i = 0; i < numFields; i++){
                Integer[] minMax = ranges.get(i);
                if(minMax == null){
                    histograms[i] = new StringHistogram(NUM_HIST_BINS);
                } else{
                    histograms[i] = new IntHistogram(NUM_HIST_BINS, minMax[0],minMax[1]);
                }
            }

            // We now make a second pass through the table to populate the histograms for each
            // field to get accurate frequencies in values. Rewind the table iterator:
            itr.rewind();

            while(itr.hasNext()){
                Tuple t = itr.next();
                // For each field of each tuple, get the histogram for the field we are on
                // and add value of the field for this tuple
                for(int i = 0; i < numFields; i++){
                    Field f = t.getField(i);
                    Type type = f.getType();
                    if(type == Type.INT_TYPE){
                        IntField intField = (IntField) f;
                        IntHistogram intH = (IntHistogram) histograms[i];
                        intH.addValue(intField.getValue());
                    }else{
                        StringField sField = (StringField) f;
                        StringHistogram stringH = (StringHistogram) histograms[i];
                        stringH.addValue(sField.getValue());
                    }
                }
            }

        } catch(DbException | TransactionAbortedException e){
            System.out.println("uh oh");
        }


    }

    /**
     * Estimates the cost of sequentially scanning the file, given that the cost
     * to read a page is costPerPageIO. You can assume that there are no seeks
     * and that no pages are in the buffer pool.
     * 
     * Also, assume that your hard drive can only read entire pages at once, so
     * if the last page of the table only has one tuple on it, it's just as
     * expensive to read as a full page. (Most real hard drives can't
     * efficiently address regions smaller than a page at a time.)
     * 
     * @return The estimated cost of scanning the table.
     */
    public double estimateScanCost() {
        // Just the number of pages we have for this table/file multiplied by the
        // IO cost per page
        double np = file.numPages();
        return np * ioCostPerPage;
    }

    /**
     * This method returns the number of tuples in the relation, given that a
     * predicate with selectivity selectivityFactor is applied.
     * 
     * @param selectivityFactor
     *            The selectivity of any predicates over the table
     * @return The estimated cardinality of the scan with the specified
     *         selectivityFactor
     */
    public int estimateTableCardinality(double selectivityFactor) {
        // Given a selectivity factor, we are selecting that fraction of all
        // the tuples or ntups of this table so we just multiply
        return (int) (selectivityFactor * ntups);
    }

    /**
     * The average selectivity of the field under op.
     * @param field
     *        the index of the field
     * @param op
     *        the operator in the predicate
     * The semantic of the method is that, given the table, and then given a
     * tuple, of which we do not know the value of the field, return the
     * expected selectivity. You may estimate this value from the histograms.
     * */
    public double avgSelectivity(int field, Predicate.Op op) {
        // some code goes here
        return 1.0;
    }

    /**
     * Estimate the selectivity of predicate <tt>field op constant</tt> on the
     * table.
     * 
     * @param field
     *            The field over which the predicate ranges
     * @param op
     *            The logical operation in the predicate
     * @param constant
     *            The value against which the field is compared
     * @return The estimated selectivity (fraction of tuples that satisfy) the
     *         predicate
     */
    public double estimateSelectivity(int field, Predicate.Op op, Field constant) {
        TupleDesc td = file.getTupleDesc();
        Type type = td.getFieldType(field);
        // Get histogram for specific field. Get the selectivity based on the
        // predicate condition and value to compare against. Return selectivity
        // factor calculation using method in our histogram.
        if(type == Type.INT_TYPE){
            IntField intV = (IntField) constant;
            IntHistogram intH = (IntHistogram) histograms[field];
            return intH.estimateSelectivity(op, intV.getValue());
        } else {
            StringField sV = (StringField) constant;
            StringHistogram stringH = (StringHistogram) histograms[field];
            return stringH.estimateSelectivity(op, sV.getValue());
        }
    }

    /**
     * return the total number of tuples in this table
     * */
    public int totalTuples() {
        // some code goes here
        return 0;
    }

}
