package simpledb;

import java.io.Serializable;
import java.util.*;

/**
 * TupleDesc describes the schema of a tuple.
 */
public class TupleDesc implements Serializable {

    /**
     * A help class to facilitate organizing the information of each field
     * */
    public static class TDItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * The type of the field
         * */
        public final Type fieldType;
        
        /**
         * The name of the field
         * */
        public final String fieldName;

        public TDItem(Type t, String n) {
            this.fieldName = n;
            this.fieldType = t;
        }

        public String toString() {
            return fieldName + "(" + fieldType + ")";
        }
    }

    /**
     * @return
     *        An iterator which iterates over all the field TDItems
     *        that are included in this TupleDesc
     * */
    public Iterator<TDItem> iterator() {
        List<TDItem> list = Arrays.asList(TDItems);
        return list.iterator();
    }

    private static final long serialVersionUID = 1L;

    private TDItem[] TDItems;

    /**
     * Create a new TupleDesc with typeAr.length fields with fields of the
     * specified types, with associated named fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     * @param fieldAr
     *            array specifying the names of the fields. Note that names may
     *            be null.
     */
    public TupleDesc(Type[] typeAr, String[] fieldAr) {
        TDItems = new TDItem[typeAr.length];
        for(int i = 0; i < typeAr.length; i++){
            TDItems[i] = new TDItem(typeAr[i], fieldAr[i]);
        }
    }

    /**
     * Constructor. Create a new tuple desc with typeAr.length fields with
     * fields of the specified types, with anonymous (unnamed) fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     */
    public TupleDesc(Type[] typeAr) {
        TDItems = new TDItem[typeAr.length];
        for(int i = 0; i < typeAr.length; i++){
            TDItems[i] = new TDItem(typeAr[i], null);
        }
    }

    /**
     * @return the number of fields in this TupleDesc
     */
    public int numFields() {
        return TDItems.length;
    }

    /**
     * Gets the (possibly null) field name of the ith field of this TupleDesc.
     * 
     * @param i
     *            index of the field name to return. It must be a valid index.
     * @return the name of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public String getFieldName(int i) throws NoSuchElementException {
        if(i < 0 || i > TDItems.length){
            throw new NoSuchElementException();
        }
        TDItem item = TDItems[i];
        return item.fieldName;
    }

    /**
     * Gets the type of the ith field of this TupleDesc.
     * 
     * @param i
     *            The index of the field to get the type of. It must be a valid
     *            index.
     * @return the type of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public Type getFieldType(int i) throws NoSuchElementException {
        if(i < 0 || i > TDItems.length){
            throw new NoSuchElementException();
        }
        TDItem item = TDItems[i];
        return item.fieldType;
    }

    /**
     * Find the index of the field with a given name.
     * 
     * @param name
     *            name of the field.
     * @return the index of the field that is first to have the given name.
     * @throws NoSuchElementException
     *             if no field with a matching name is found.
     */
    public int fieldNameToIndex(String name) throws NoSuchElementException {
        for(int i = 0; i < TDItems.length; i++){
            String fieldName = TDItems[i].fieldName;
            if(fieldName == null && name != null){
                continue;
            }
            if(fieldName.equals(name)){
                return i;
            }
        }
        throw new NoSuchElementException();
    }

    /**
     * @return The size (in bytes) of tuples corresponding to this TupleDesc.
     *         Note that tuples from a given TupleDesc are of a fixed size.
     */
    public int getSize() {
        int total = 0;
        for (TDItem tdItem : TDItems) {
            Type type = tdItem.fieldType;
            total += type.getLen();
        }
        return total;
    }

    /**
     * Merge two TupleDescs into one, with td1.numFields + td2.numFields fields,
     * with the first td1.numFields coming from td1 and the remaining from td2.
     * 
     * @param td1
     *            The TupleDesc with the first fields of the new TupleDesc
     * @param td2
     *            The TupleDesc with the last fields of the TupleDesc
     * @return the new TupleDesc
     */
    public static TupleDesc merge(TupleDesc td1, TupleDesc td2) {
        int mergeSize = td1.numFields() + td2.numFields();
        Type[] types = new Type[mergeSize];
        String[] names = new String[mergeSize];

        Iterator<TDItem> td1Itr = td1.iterator();
        Iterator<TDItem> td2Itr = td2.iterator();

        int i = 0;
        while(td1Itr.hasNext()){
            TDItem item = td1Itr.next();
            types[i] = item.fieldType;
            names[i] = item.fieldName;
            i++;
        }
        while(td2Itr.hasNext()){
            TDItem item = td2Itr.next();
            types[i] = item.fieldType;
            names[i] = item.fieldName;
            i++;
        }

        return new TupleDesc(types, names);
    }

    /**
     * Compares the specified object with this TupleDesc for equality. Two
     * TupleDescs are considered equal if they have the same number of items
     * and if the i-th type in this TupleDesc is equal to the i-th type in o
     * for every i.
     * 
     * @param o
     *            the Object to be compared for equality with this TupleDesc.
     * @return true if the object is equal to this TupleDesc.
     */

    public boolean equals(Object o) {
        if(!(o instanceof TupleDesc))
            return false;

        TupleDesc thatTD = (TupleDesc) o;
        if(thatTD.numFields() != TDItems.length){
            return false;
        }

        Iterator<TDItem> items = thatTD.iterator();
        int i = 0;
        while(items.hasNext()){
            TDItem item = items.next();
            TDItem thisItem = TDItems[i];
            /*
            if(thisItem.fieldName == null && item.fieldName == null){
                continue;
            }

            if(!thisItem.fieldName.equals(item.fieldName)){
                return false;
            }
             */

            if(!thisItem.fieldType.equals(item.fieldType)){
                return false;
            }

            i++;
        }
        return true;
    }

    public int hashCode() {
        // If you want to use TupleDesc as keys for HashMap, implement this so
        // that equal objects have equals hashCode() results
        throw new UnsupportedOperationException("unimplemented");
    }

    /**
     * Returns a String describing this descriptor. It should be of the form
     * "fieldType[0](fieldName[0]), ..., fieldType[M](fieldName[M])", although
     * the exact format does not matter.
     * 
     * @return String describing this descriptor.
     */
    public String toString() {
        String string = "";
        string = string + TDItems[0].toString();
        for(int i = 1; i < TDItems.length; i++){
            TDItem item = TDItems[i];
            string = string + ",";
            string = string + item.toString();
        }
        return string;
    }
}
