/*
 *  Imixs-Workflow 
 *  
 *  Copyright (C) 2001-2020 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Project: 
 *      https://www.imixs.org
 *      https://github.com/imixs/imixs-workflow
 *  
 *  Contributors:  
 *      Imixs Software Solutions GmbH - Project Management
 *      Ralph Soika - Software Developer
 */

package org.imixs.workflow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLItem;

/**
 * This Class defines a ValueObject to be used to exchange data structures used
 * by the org.imixs.workflow Framework. Most components of this framework use
 * this wrapper class to easy transport workflow data between the different
 * workflow modules. ValueObjects, particular in J2EE Applications, have the
 * advantage to improve performance of remote method calls. The Imixs
 * ItemCollection enables a very flexibly and easy to use data structure.
 * <p>
 * A ItemCollection contains various Items (attributes). Every Item exist of a
 * Name (String) and a list of values (List of Object). Internal every Value is
 * stored inside a List Class. All values are stored internally in a Map
 * containing key values pairs.
 * <p>
 * NOTE: An ItemCollection is not serializable and can not be stored into
 * another ItemCollection. To serialize a ItemCollection use the
 * XMLItemCollection. @see XMLItemCollectionAdapter.
 * <p>
 * 
 * @author Ralph Soika
 * @version 2.1
 * @see org.imixs.workflow.WorkflowManager
 */

public final class ItemCollection implements Cloneable {
    // NOTE: ItemCollection is not serializable

    private static final Logger logger = Logger.getLogger(ItemCollection.class.getName());

    private Map<String, List<?>> hash = new HashMap<>();

    /**
     * Creates a new empty ItemCollection
     * 
     */
    public ItemCollection() {
    }

    /**
     * Creates a new ItemCollection and makes a deep copy from a given value Map
     * 
     * @param map - with item values
     */
    public ItemCollection(Map<String, List<?>> map) {
        this.replaceAllItems(map);
    }

    /**
     * Creates a new ItemCollection and makes a deep copy from a given
     * ItemCollection
     * 
     * @param itemCol - ItemCollection with values
     */
    public ItemCollection(ItemCollection itemCol) {
        this.replaceAllItems(itemCol.hash);
    }

    /**
     * Creates a new ItemCollection by a reference to a given value Map. This method
     * does not make a deep copy of the given map and sets the value map by
     * reference. This method can be used in cases where values are only read. In
     * all other cases, the constructor method 'ItemCollection(map)' should be used.
     * 
     * @param map - reference with item values
     * @return new reference
     */
    public static ItemCollection createByReference(final Map<String, List<?>> map) {
        ItemCollection reference = new ItemCollection();
        if (map != null) {
            reference.hash = map;
        }
        return reference;
    }

    /**
     * This method clones the current ItemCollection. The method makes a deep copy
     * of the current instance.
     */
    @SuppressWarnings({"override", "CloneDoesntCallSuperClone"})
    public ItemCollection clone() {
        ItemCollection clone = new ItemCollection(this);
            return clone;
    }

    /**
     * This method clones the current ItemCollection with a subset of items. The
     * method makes a deep copy of the current instance and removes items not
     * defined by the list of itemNames.
     * <p>
     * The list of itemNames can contain exact names or a regular expression.
     * <p>
     * A itemName can also be mapped into a new itemName by separating the target
     * name with a | (e.g. name|parentName)
     * 
     * @param itemNames - list of items to be copied into the clone
     * @return new ItemCollection
     */
    public ItemCollection clone(final List<String> itemNames) {
        ItemCollection clone = this.clone();
        // remove all undefined items if a list of itemNames is defined.
        if (itemNames != null && !itemNames.isEmpty()) {
            // we build a list with all items to be cloned...
            List<String> cloneItemList = new ArrayList<>();
            Set<String> originItemNameList = hash.keySet();
            for (String itemPattern : itemNames) {
                // first test an exact match....
                if (originItemNameList.contains(itemPattern.toLowerCase())) {
                    cloneItemList.add(itemPattern.toLowerCase());
                } else {

                    // if we have a | char than copy the item into a new itemname....
                    // default behavior without reg ex
                    if (itemPattern.indexOf('|') > -1) {
                        String targetItemName = itemPattern.substring(itemPattern.indexOf('|') + 1).trim();
                        String sourceItemName = itemPattern.substring(0, itemPattern.indexOf('|')).trim();
                        // dose the sourceItemName exist?
                        if (clone.hasItem(sourceItemName)) {
                            clone.replaceItemValue(targetItemName, clone.getItemValue(sourceItemName));
                            cloneItemList.add(targetItemName);
                            continue;
                        }
                    }
                    // finally we test if field is a reg ex
                    Pattern pattern = Pattern.compile(itemPattern);
                    for (String originItemName : originItemNameList) {
                        if (pattern.matcher(originItemName).find()) {
                            cloneItemList.add(originItemName);
                        }
                    }

                }
            }
            // now we have list with all items to be cloned
            for (String itemName : originItemNameList) {
                if (!cloneItemList.contains(itemName)) {
                    // remove not matching items....
                    clone.removeItem(itemName);
                }
            }
        }
        return clone;
    }

    /**
     * This method makes a deep copy of a single item value from a given source
     * ItemCollection. The method can be used in cases the item to copy represents a
     * complex data structure and can not be copied by reference. See also
     * deepCopyOfMap.
     * 
     * @param itemName
     * @param source
     */
    public void cloneItem(String itemName, ItemCollection source) {
        try {
            List<?> sourceValue = source.getItemValue(itemName);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            // serialize and pass the object
            oos.writeObject(sourceValue);
            oos.flush();
            ByteArrayInputStream bais = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            @SuppressWarnings("unchecked")
            List<? super Object> copy = (List<? super Object>) ois.readObject();
            hash.put(itemName, copy);
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.WARNING, "Unable to clone values of Item ''{0}'' - {1}", new Object[]{itemName, e});
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ItemCollection))
            return false;
        return hash.equals(((ItemCollection) o).hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int hashCode = 7;
        hashCode = 61 * hashCode + Objects.hashCode(this.hash);
        return hashCode;
    }

    /**
     * Set the value of an item. If the ItemCollection does not contain an item with
     * the specified name, the method creates a new item and adds it to the
     * ItemCollection. The ItemName is not case sensitive. Use hasItem to verify the
     * existence of an item. All item names will be lower cased.
     * <p>
     * Each item can contain a list of values (multivalue item). If a single value
     * is provided the method creates a List with one single value (singlevalue
     * item).
     * <p>
     * If the value is null the method will remove the item. This is equal to the
     * method call removeItem()
     * <p>
     * If the ItemValue is not serializable the item will be removed.
     * <p>
     * 
     * @param itemName  The name of the item or items you want to replace.
     * @param itemValue The value of the new item. The data type of the item depends
     *                  upon the data type of value, and does not need to match the
     *                  data type of the old item.
     * @return current instance
     */
    public ItemCollection setItemValue(String itemName, Object itemValue) {
        setItemValue(itemName, itemValue, false, false);
        return this;
    }

    /**
     * Set the value of an item. If the ItemCollection does not contain an item with
     * the specified name, the method creates a new item and adds it to the
     * ItemCollection. The ItemName is not case sensitive. Use hasItem to verify the
     * existence of an item. All item names will be lower cased.
     * <p>
     * Each item can contain a list of values (multivalue item). If a single value
     * is provided the method creates a List with one single value (singlevalue
     * item).
     * <p>
     * If the value is null the method will remove the item. This is equal to the
     * method call removeItem()
     * <p>
     * If the ItemValue is not serializable the item will be removed.
     * <p>
     * This method ensures that all values are unique and null or empty values will
     * be removed
     * 
     * 
     * @param itemName  The name of the item or items you want to replace.
     * @param itemValue The value of the new item. The data type of the item depends
     *                  upon the data type of value, and does not need to match the
     *                  data type of the old item.
     * @return current instance
     */
    public ItemCollection setItemValueUnique(String itemName, Object itemValue) {
        setItemValue(itemName, itemValue, false, true);
        return this;
    }

    /**
     * Appends a value to an existing item. If the ItemCollection does not contain
     * an item with the specified name, the method creates a new item and adds it to
     * the ItemCollection. The ItemName is not case sensitive. Use hasItem to verify
     * the existence of an item. All item names will be lower cased.
     * <p>
     * If a value list is provided the method appends each single value.
     * <p>
     * If the value is null the method will remove the item. This is equal to the
     * method call removeItem()
     * <p>
     * If the ItemValue is not serializable the item will be removed.
     * 
     * 
     * @param itemName  The name of the item or items you want to replace.
     * @param itemValue The value of the new item. The data type of the item depends
     *                  upon the data type of value, and does not need to match the
     *                  data type of the old item.
     * @return current instance
     */
    public ItemCollection appendItemValue(String itemName, Object itemValue) {
        setItemValue(itemName, itemValue, true, false);
        return this;
    }

    /**
     * Appends a value to an existing item. If the ItemCollection does not contain
     * an item with the specified name, the method creates a new item and adds it to
     * the ItemCollection. The ItemName is not case sensitive. Use hasItem to verify
     * the existence of an item. All item names will be lower cased.
     * <p>
     * If a value list is provided the method appends each single value.
     * <p>
     * If the value is null the method will remove the item. This is equal to the
     * method call removeItem()
     * <p>
     * If the ItemValue is not serializable the item will be removed.
     * <p>
     * This method ensures that all values are unique and null or empty values will
     * be removed
     * 
     * 
     * @param itemName  The name of the item or items you want to replace.
     * @param itemValue The value of the new item. The data type of the item depends
     *                  upon the data type of value, and does not need to match the
     *                  data type of the old item.
     * @return current instance
     */
    public ItemCollection appendItemValueUnique(String itemName, Object itemValue) {
        setItemValue(itemName, itemValue, true, true);
        return this;
    }

    /**
     * Returns the value list for the specified Item. The returned list is untyped
     * and the values contained in the list are not converted to a specific type.
     * The values have the same object type as set by calling the method
     * <code>setItemValue(String itemName, Object itemValue)</code>. To get a typed
     * value list, see the method <code>getItemValue(itemName, itemType)</code> .
     * 
     * <p>
     * If the item does not exist or has no values, the method returns an empty
     * List.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param itemName The name of an item.
     * @return an untyped list of values contained by the item.
     */
    public List<?> getItemValue(String itemName) {
        if (itemName == null) {
            return null;
        }
        itemName = itemName.toLowerCase().trim();
        List<?> o = hash.get(itemName);
        if (o == null)
            return new ArrayList<>();
        else {
            // remove null values
            o.removeAll(Collections.singleton(null));
            return o;
        }
    }

    /**
     * Returns the resolved item value of the specified type. The method converts
     * the value to the specified type if possible, otherwise the method returns
     * null. If the item has multiple values, this method returns the first value.
     * <p>
     * If the item isn't present in the itemCollection the method returns null.
     * <p>
     * If the specified type is int, float, long, double, Integer, Float, Long or
     * Double, the method returns 0 instead of null
     * <p>
     * If the item contains no value with the specified type, the method returns
     * null. The ItemName is not case sensitive. Use hasItem to verify the existence
     * of an item.
     * 
     * @param <T> Type of result same as itemType
     * @param itemName The item Name.
     * @param itemType The type into which the resolve item value should get
     *                 converted
     * @return the resolved item value as an object of the requested type.
     */
    @SuppressWarnings({"unchecked", "UnnecessaryBoxing"})
    public <T> T getItemValue(String itemName, Class<T> itemType) {
        List<?> values = getItemValue(itemName);
        if (values == null || values.isEmpty()) {

            // test for Integer
            if (itemType == Integer.class || itemType == int.class) {
                return (T) Integer.valueOf(0);
            }
            // test for Float
            if (itemType == Float.class || itemType == float.class) {
                return (T) Float.valueOf(0.0f);
            }

            // test for Long
            if (itemType == Long.class || itemType == long.class) {
                return (T) Long.valueOf(0L);
            }

            // test for Double
            if (itemType == Double.class || itemType == double.class) {
                return (T) Double.valueOf(0.0d);
            }

            return null;
        }
        // find first value of specified type
        return convertValue(values.get(0), itemType);
    }

    /**
     * Returns the resolved list of item values of the specified type. The method
     * converts the values of the list to the specified type if possible.
     * <p>
     * If the item isn't present in the itemCollection the method returns an empty
     * list.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param <T> Type class for return
     * @param itemName The item Name.
     * @param itemType The type into which the resolved item values should get
     *                 converted
     * @return the resolved list of item values of the requested type.
     */

    public <T> List<T> getItemValueList(String itemName, Class<T> itemType) {
        List<T> result = new ArrayList<>();
        List<?> values = getItemValue(itemName);
        for(Object value : values) {
            result.add(convertValue(value, itemType));
        }
        
        return result;
        // @see details here:
        // https://stackoverflow.com/questions/51937821/how-to-define-a-generic-list-of-types-in-java?noredirect=1#comment90825856_51937821
    }

    /**
     * Returns the resolved list of list item values of the specified type. The method
     * converts the values of the list to the specified type if possible.
     * <p>
     * If the item isn't present in the itemCollection the method returns an empty
     * list.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param <T> Type class for return
     * @param itemName The item Name.
     * @param itemType The type into which the resolved item values should get
     *                 converted
     * @return the resolved list of item values of the requested type.
     */

    public <T> List<List<T>> getItemValueListList(String itemName, Class<T> itemType) {
        List<List<T>> result = new ArrayList<>();
        List<?> values = getItemValue(itemName);
        for(Object value : values) {
            if(value instanceof List) {
                List<T> partialResult = new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<Object> listValues = (List<Object>) value;
                for(Object listValue : listValues) {
                    partialResult.add(convertValue(listValue, itemType));
                }
                result.add(partialResult);
            }
        }
        
        return result;
        // @see details here:
        // https://stackoverflow.com/questions/51937821/how-to-define-a-generic-list-of-types-in-java?noredirect=1#comment90825856_51937821
    }

    /**
     * Returns the resolved map of item values of the specified type. The method
     * converts the map values of the list to the specified type if possible.
     * <p>
     * If the item isn't present in the itemCollection the method returns an empty
     * map.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param <T> Type class for return
     * @param itemName The item Name.
     * @param itemType The type into which the resolved item values should get
     *                 converted
     * @return the resolved map of item values of the requested type.
     */

    public <T> Map<String, T> getItemValueMap(String itemName, Class<T> itemType) {
        Map<String, T> result = new HashMap<>();
        List<?> values = getItemValue(itemName);
        for(Object value : values) {
            if(value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> mapValue = (Map<Object, Object>) value;
                for(Entry<Object,Object> entry : mapValue.entrySet()) {
                    result.put(entry.getKey().toString(), convertValue(entry.getValue(), itemType));
                }
            }
        }
        return result;
    }

    /**
     * removes a attribute from the item collection
     * 
     * @param name - item name
     */
    public void removeItem(String name) {
        if (name != null) {
            name = name.toLowerCase().trim();
            this.hash.remove(name);
        }
    }

    /**
     * Indicates whether an item exists in the document.
     * 
     * @param aName - item name
     * @return true if an item with name exists in the document, false if no item
     *         with name exists in the document
     * 
     */
    public boolean hasItem(String aName) {
        if (aName == null) {
            return false;
        }
        aName = aName.toLowerCase().trim();
        return (hash.get(aName) != null);
    }

    /**
     * Returns true if the given itemname does not exist or no value is assigned to.
     * This includes empty strings.
     * 
     * @param itemName - the item to be verified
     * @return - true if no value is assigned.
     */
    public boolean isItemEmpty(String itemName) {
        return !hasItem(itemName) || getItemValue(itemName).isEmpty()
                || (getItemValue(itemName).size() == 1 && getItemValueString(itemName).isEmpty());
    }

    /**
     * Returns true if the value of an item with a single numeric value is from type
     * 'Integer'
     * 
     * @param aName - item name
     * @return boolean true if object is from type Double
     * 
     */
    public boolean isItemValueInteger(String aName) {
        List<?> v = getItemValue(aName);
        if (v.isEmpty())
            return false;
        else {
            // test for object type...
            Object o = v.get(0);
            return (o instanceof Integer);
        }
    }

    /**
     * Returns true if the value of an item with a single numeric value is from type
     * 'Long'
     * 
     * @param aName - item name
     * @return boolean true if object is from type Double
     * 
     */
    public boolean isItemValueLong(String aName) {
        List<?> v = getItemValue(aName);
        if (v.isEmpty())
            return false;
        else {
            // test for object type...
            Object o = v.get(0);
            return (o instanceof Long);
        }
    }

    /**
     * Returns true if the value of an item with a single numeric value is from type
     * 'Double'
     * 
     * @param aName - item name
     * @return boolean true if object is from type Double
     * 
     */
    public boolean isItemValueDouble(String aName) {
        List<?> v = getItemValue(aName);
        if (v.isEmpty())
            return false;
        else {
            // test for object type...
            Object o = v.get(0);
            return (o instanceof Double);
        }
    }

    /**
     * Returns true if the value of an item with a single numeric value is from type
     * 'Float'
     * 
     * @param aName - item name
     * @return boolean true if object is from type Double
     * 
     */
    public boolean isItemValueFloat(String aName) {
        List<?> v = getItemValue(aName);
        if (v.isEmpty())
            return false;
        else {
            // test for object type...
            Object o = v.get(0);
            return (o instanceof Float);
        }
    }

    /**
     * Returns true if the value of an item is a numeric value (e.g.
     * float,double,int,long)
     * 
     * @param aName
     * @return
     */
    public boolean isItemValueNumeric(String aName) {
        List<?> v = getItemValue(aName);
        if (v.isEmpty())
            return false;
        else {
            // test for numeric type...
            Object o = v.get(0);
            return (o instanceof Number);
        }
    }

    /**
     * Returns true if the value of an item is from type 'Date'
     * 
     * @param aName - item name
     * @return boolean true if object is from type Double
     * 
     */
    public boolean isItemValueDate(String aName) {
        List<?> v = getItemValue(aName);
        if (v.isEmpty())
            return false;
        else {
            // test for object type...
            Object o = v.get(0);
            return (o instanceof Date);
        }
    }

    /**
     * returns all Items of the Collection as a Map
     * 
     * @return Map with all Items
     */
    public Map<String, List<?>> getAllItems() {
        return hash;

    }

    /**
     * replaces the current map object. In different to the method replaceAllItems
     * this method overwrites the hash object and did not copy the values
     * 
     * @param aHash
     */
    public void setAllItems(Map<String, List<?>> aHash) {
        hash = aHash;

    }

    /**
     * Returns a sorted list of all item names stored in the current ItemCollection.
     * 
     * @return sorted list of item names
     */
    public List<String> getItemNames() {
        List<String> result = new ArrayList<>();
        result.addAll(hash.keySet());
        // sort result
        Collections.sort(result);
        return result;
    }

    /**
     * Replaces the value of an item. If the ItemCollection does not contain an item
     * with the specified name, the method creates a new item and adds it to the
     * ItemCollection. The ItemName is not case sensitive. Use hasItem to verify the
     * existence of an item. All item names will be lower cased.
     * <p>
     * Each item can contain a list of values (multivalue item). If a single value
     * is provided the method creates a List with one single value (singlevalue
     * item).
     * <p>
     * If the value is null the method will remove the item. This is equal to the
     * method call removeItem()
     * <p>
     * If the ItemValue is not serializable the item will be removed. This method is
     * deprecated and should be replaced by the method setItemvValue.
     * 
     * @see method setItemValue.
     * @param itemName  The name of the item or items you want to replace.
     * @param itemValue The value of the new item. The data type of the item depends
     *                  upon the data type of value, and does not need to match the
     *                  data type of the old item.
     */
    public void replaceItemValue(String itemName, Object itemValue) {
        setItemValue(itemName, itemValue, false, false);
    }

    /**
     * Returns the resolved String value of the specified item. The method converts
     * the stored value to a String. If the item has no value, the method returns an
     * empty String. If the item has multiple values, this method returns the first
     * value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param itemName The name of an item.
     * @return the String value of the item
     * 
     */
    public String getItemValueString(String itemName) {
        List<?> v = (List<?>) getItemValue(itemName);
        if (v.isEmpty()) {
            return "";
        } else {
            // verify if value is null
            Object o = v.get(0);
            if (o == null) {
                return "";
            } else {
                return o.toString();
            }
        }

    }

    /**
     * Returns the resolved Integer value of the specified item. The method converts
     * the stored value to an Integer. If the item has no value or the value is not
     * convertible to an Integer, the method returns 0. If the item has multiple
     * values, this method returns the first value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param itemName The name of an item.
     * @return the integer value of the item
     */
    public int getItemValueInteger(String itemName) {
        try {
            List<?> v = getItemValue(itemName);
            if (v.isEmpty()) {
                return 0;
            }
            String sValue = v.get(0).toString();
            return new BigDecimal(sValue).intValue();
        } catch (NumberFormatException | ClassCastException e) {
            return 0;
        }
    }

    /**
     * Returns the resolved Long value of the specified item. The method converts
     * the stored value to long. If the item has no value or the value is not
     * convertible to a Long, the method returns 0. If the item has multiple values,
     * this method returns the first value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param itemName The name of an item.
     * @return the Long value of the item
     */
    public long getItemValueLong(String itemName) {
        try {
            List<?> v = getItemValue(itemName);
            if (v.isEmpty()) {
                return 0;
            }
            String sValue = v.get(0).toString();
            return new BigDecimal(sValue).longValue();
        } catch (NumberFormatException | ClassCastException e) {
            return 0;
        }
    }

    /**
     * Returns the resolved Date value of the specified item. If the item has no
     * value or the value is not of the type Date, the method returns null. If the
     * item has multiple values, this method returns the first value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param aName The name of an item.
     * @return the Date value of the item
     */
    public Date getItemValueDate(String aName) {
        try {
            List<?> v = getItemValue(aName);
            if (v.isEmpty()) {
                return null;
            }
            Object o = v.get(0);
            if (!(o instanceof Date)) {
                return null;
            }
            return (Date) o;
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * Returns the resolved LocalDateTime value of the specified item. The method
     * converts a Date object into a LocalDateTime object using the
     * ZoneId.systemDefault().
     * <p>
     * Note: internally the ItemCollection store LocalDateTime values as Date
     * objects!
     * <p>
     * If the item has no value or the value is not of the type Date, the method
     * returns null. If the item has multiple values, this method returns the first
     * value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param aName The name of an item.
     * @return the Date value of the item
     */
    public LocalDateTime getItemValueLocalDateTime(String aName) {
        Date d = this.getItemValueDate(aName);
        if (d != null) {
            LocalDateTime localDateTime = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return localDateTime;
        } else {
            return null;
        }
    }

    /**
     * Returns the resolved LocalDate value of the specified item. The method
     * converts a Date object into a LocalDate object using the
     * ZoneId.systemDefault().
     * <p>
     * Note: internally the ItemCollection store LocalDateTime values as Date
     * objects!
     * <p>
     * If the item has no value or the value is not of the type Date, the method
     * returns null. If the item has multiple values, this method returns the first
     * value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param aName The name of an item.
     * @return the Date value of the item
     */
    public LocalDate getItemValueLocalDate(String aName) {
        Date d = this.getItemValueDate(aName);
        if (d != null) {
            LocalDate localDate = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return localDate;
        } else {
            return null;
        }
    }

    /**
     * Returns the resolved Double value of the specified item. The method converts
     * the stored value to double. If the item has no value or the value is not
     * convertible to a Double, the method returns 0.0. If the item has multiple
     * values, this method returns the first value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param itemName The name of an item.
     * @return the double value of the item
     */
    public double getItemValueDouble(String itemName) {
        try {
            List<?> v = getItemValue(itemName);
            if (v.isEmpty())
                return 0.0d;
            else {
                try {
                    return new BigDecimal(v.get(0).toString()).doubleValue();
                } catch (ClassCastException | NumberFormatException e) {
                    return 0.0d;
                }
            }
        } catch (ClassCastException e) {
            return 0.0d;
        }
    }

    /**
     * Returns the resolved Float value of the specified item. The method converts
     * the stored value to float. If the item has no value or the value is not
     * convertible to a Float, the method returns 0.0. If the item has multiple
     * values, this method returns the first value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param itemName The name of an item.
     * @return the float value of the item
     */
    public float getItemValueFloat(String itemName) {
        try {
            List<?> v = getItemValue(itemName);
            if (v.isEmpty())
                return (float) 0.0;
            else {
                // try to parse string.....
                try {
                    return new BigDecimal(v.get(0).toString()).floatValue();
                } catch (ClassCastException | NumberFormatException e) {
                    return 0.0f;
                }

            }
        } catch (ClassCastException e) {
            return 0.0f;
        }
    }

    /**
     * Returns the resolved Boolean value of the specified item. The method converts
     * the stored value to Boolean. If the item has no value or the value is not
     * convertible to a Boolean, the method returns false. If the item has multiple
     * values, this method returns the first value.
     * <p>
     * The ItemName is not case sensitive. Use hasItem to verify the existence of an
     * item.
     * 
     * @param itemName The name of an item.
     * @return the boolean value of the item
     */
    public boolean getItemValueBoolean(String itemName) {
        try {
            List<?> v = getItemValue(itemName);
            if (v.isEmpty()) {
                return false;
            }
            Object sValue = v.get(0);
            return Boolean.parseBoolean(sValue.toString());
        } catch (ClassCastException e) {
            return false;
        }
    }

    /**
     * Replaces all items specified in the map with new items, which are assigned to
     * the specified values inside the map.
     * 
     * The method makes a deep copy of the source map using serialization. This is
     * to make sure, that no object reference is copied. Other wise for example
     * embedded arrays are not cloned. This is also important for JPA to avoid
     * changes of attached entity beans with references in the data of an
     * ItemCollection.
     * 
     * @see deepCopyOfMap
     * @param map
     */
    public void replaceAllItems(Map<String, List<?>> map) {
        if (map == null) {
            return;
        }
        // make a deep copy of the map
        Map<String, List<?>> clonedMap = deepCopyOfMap(map);
        if (clonedMap != null) {
            for (Entry<String, List<?>> entry : clonedMap.entrySet()) {
                replaceItemValue(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Copies all items of a source ItemCollection.
     * <p>
     * The method makes a deep copy of the source map using serialization. This is
     * to make sure, that no object reference is copied. Other wise for example
     * embedded arrays are not cloned. This is also important for JPA to avoid
     * changes of attached entity beans with references in the data of an
     * ItemCollection.
     * 
     * @param source
     * @see deepCopyOfMap
     */
    public void copy(ItemCollection source) {
        replaceAllItems(source.getAllItems());
    }

    /**
     * Merges all items from a source map into the current instance. Only Items will
     * be copied if the current instance does not have an item with the same name.
     * If you want to copy all item values, use the method replaceAllItems instead.
     * <p>
     * The method makes a deep copy of the source map using serialization. This is
     * to make sure, that no object reference is copied. Other wise for example
     * embedded arrays are not cloned. This is also important for JPA to avoid
     * changes of attached entity beans with references in the data of an
     * ItemCollection.
     * 
     * @see deepCopyOfMap
     * @param map
     */
    public void mergeItems(Map<String, List<?>> map) {
        if (map == null) {
            return;
        }
        // make a deep copy of the map
        Map<String, List<?>> clonedMap = deepCopyOfMap(map);
        if (clonedMap != null) {
            for (Entry<String, List<?>> entry : clonedMap.entrySet()) {
                // copy only the item if the hash map does not have an item with the same name
                if (!hash.containsKey(entry.getKey())) {
                    replaceItemValue(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * This method removes duplicates and null or empty values from an item list
     * 
     * @param itemName - item to be processed
     */
    public void purgeItemValue(String itemName) {
        List<?> valueList = this.getItemValue(itemName);
        // remove null or empty entries...
        valueList.removeIf(item -> item == null || "".equals(item));
        // create a new instance of a List and distinct the List.
        List<?> purgedValueList = valueList.stream().distinct().collect(Collectors.toList());
        this.setItemValue(itemName, purgedValueList);
    }

    /**
     * This method adds a fileData object to the ItemCollection. The item '$file'
     * stores all data objects.
     * 
     * @param filedata - a file data object
     * @see FileData
     */
    @SuppressWarnings("unchecked")
    public void addFileData(FileData filedata) {
        
        // purge $file....
        purgeItemValue("$file");
        if (filedata != null) {
            Map<String, List<?>> mapFiles;
            // Store files using a map....
            List<?> vFiles = getItemValue("$file");
            if (vFiles != null && !vFiles.isEmpty())
                mapFiles = (Map<String, List<?>>) vFiles.get(0);
            else
                mapFiles = new LinkedHashMap<>();

            // existing file will be overridden!
            List<Object> fileInfo = new ArrayList<>();
            // put file in a List containing the contentType, content, MD5Checksum and
            // optional attributes
            fileInfo.add(filedata.getContentType());
            fileInfo.add(filedata.getContent());
            // add optional attributes
            fileInfo.add(filedata.getAttributes());

            mapFiles.put(filedata.getName(), fileInfo);
            replaceItemValue("$file", mapFiles);

            replaceItemValue("$file.count", mapFiles.size());
            replaceItemValue("$file.names", mapFiles.keySet());
        }

    }

    /**
     * This method adds a single file to the ItemCollection. files will be stored
     * into the property $file.
     * 
     * @param data        - byte array with file data
     * @param fileName    - name of the file attachment
     * @param contentType - the contenttype (e.g. 'Text/HTML')
     * 
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public void addFile(byte[] data, String fileName, String contentType) {
        logger.warning("method addFile() is deprecated - replace with addFileData()");
        if (data != null) {
            // IE includes '\' characters! so remove all these characters....
            if (fileName.indexOf('\\') > -1)
                fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);
            if (fileName.indexOf('/') > -1)
                fileName = fileName.substring(fileName.lastIndexOf('/') + 1);

            if (contentType == null || "".equals(contentType))
                contentType = "application/unknown";

            // Store files using a map....
            Map<String, List<?>> mapFiles;
            List<?> vFiles = getItemValue("$file");
            if (vFiles != null && !vFiles.isEmpty())
                mapFiles = (Map<String, List<?>>) vFiles.get(0);
            else
                mapFiles = new LinkedHashMap<>();

            // existing file will be overridden!
            List<Object> fileInfo = new ArrayList<>();
            // put file in a list containing the byte array and also the
            // content type
            fileInfo.add(contentType);
            fileInfo.add(data);
            mapFiles.put(fileName, fileInfo);
            replaceItemValue("$file", mapFiles);

            // Update $file meta data...
            replaceItemValue("$file.count", mapFiles.size());
            replaceItemValue("$file.names", mapFiles.keySet());
        }
    }

    /**
     * Returns a data object for an attached file. The data object is a list
     * containing the contentType (String) and the content (byte[])
     * 
     * @param filename
     * @return file data contentType (String) and the content (byte[])
     */
    @Deprecated
    public List<?> getFile(String filename) {
        Map<String, List<?>> files = this.getFiles();
        if (files != null) {
            return files.get(filename);
        } else {
            return null;
        }
    }

    /**
     * Returns a FileData object for an attached file.
     * 
     * @param filename
     * @return FileData object
     */
    public FileData getFileData(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        List<FileData> files = getFileData();
        for (FileData fileData : files) {
            if (filename.equals(fileData.getName())) {
                return fileData;
            }
        }
        // not found!
        return null;
    }

    /**
     * Returns a list of all FileData objects.
     * <p>
     * FileData objects are stored in the attribute '$file'.
     *
     * @return list of FileData objects
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<FileData> getFileData() {
        purgeItemValue("$file");
        List<FileData> result = new ArrayList<>();
        List<?> vFiles = getItemValue("$file");
        if (vFiles != null && !vFiles.isEmpty()) {
            Map<String, List<?>> testContent = (Map<String, List<?>>) vFiles.get(0);
            for (Entry<String, List<?>> entry : testContent.entrySet()) {
                List<Object> data;
                String sFileName = entry.getKey();
                Object obj = entry.getValue();
                // test if the value part is a List or an Object[] ?
                // In case its an Object[] we convert the array to a List
                if (obj instanceof List) {
                    data = (List<Object>) obj;
                } else {
                    // convert array to List
                    data = Arrays.asList(obj);
                }
                // now we build the FileData object.
                String contentType = (String) data.get(0);
                byte[] content = (byte[]) data.get(1);
                Map<String, List<?>> attributes = null;
                // test if we have custom attributes?
                if (data.size() >= 3) {
                    attributes = (Map<String, List<?>>) data.get(2);
                }
                if (attributes == null) {
                    // try to migrate deprecated 'dms' item......
                    // in some cases the DMS item does not contain a Map object
                    // for that reason we test the object type
                    // see issue #509
                    List<?> vDMS = getItemValue("dms");
                    // test if we found a match....
                    for (Object aMetadataObject : vDMS) {
                        // issue #509
                        if (aMetadataObject instanceof Map aMetadata) {
                            String sName = getStringValueFromMap(aMetadata, "txtname");
                            if (sFileName.equals(sName)) {
                                attributes = aMetadata;
                                break;
                            }
                        }
                    }
                }
                result.add(new FileData(sFileName, content, contentType, attributes));
            }
        }
        return result;

    }

    /**
     * This method removes a single file attachment from the workitem
     * 
     * @param aFilename
     */
    @SuppressWarnings("unchecked")
    public void removeFile(String aFilename) {
        /* delete attachment */
        Map<String, List<Object>> mapFiles;
        List<?> vFiles = getItemValue("$file");
        if (vFiles != null && !vFiles.isEmpty()) {
            mapFiles = (Map<String, List<Object>>) vFiles.get(0);
            mapFiles.remove(aFilename);
            replaceItemValue("$file", mapFiles);

            // Update $file meta data...
            replaceItemValue("$file.count", mapFiles.size());
            replaceItemValue("$file.names", mapFiles.keySet());
        }

    }

    /**
     * Returns files stored in the property '$file'. The files are returned in a Map
     * interface where the key is the filename and the value is a list with two
     * elements - the ContenType and the file content (byte[]). s Files can be added
     * into a ItemCollection using the method addFile().
     * 
     * @return
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public Map<String, List<?>> getFiles() {
        logger.warning("method getFiles() is deprecated - replace with getFileData()");
        List<?> vFiles = getItemValue("$file");
        if (vFiles != null && !vFiles.isEmpty()) {
            // test if the value part is a List or an Object[]. In case its an
            // Object[] we convert the array to a List

            Map<String, ?> testContent = (Map<String, ?>) vFiles.get(0);
            Map<String, List<?>> mapFiles = new LinkedHashMap<>();
            for (Entry<String, ?> entry : testContent.entrySet()) {
                String sFileName = entry.getKey();
                Object obj = entry.getValue();
                if (obj instanceof List) {
                    mapFiles.put(sFileName, (List<Object>) obj);
                } else {
                    // convert array to List
                    mapFiles.put(sFileName, Arrays.asList(obj));
                }
            }
            return mapFiles;
        }

        return null;
    }

    /**
     * Returns a list of file names attached to the current workitem. File
     * Attachments can be added using the method addFile().
     * 
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<String> getFileNames() {
        if (!this.hasItem("$file.names")) {
            // This code is just for backward compatibility. Normally this case should not
            // be necessary if files were added with the version 5.2.0
            List<String> files = new ArrayList<>();
            Map<String, List<?>> mapFiles;
            List<?> vFiles = getItemValue("$file");
            if (vFiles != null && !vFiles.isEmpty()) {
                mapFiles = (Map<String, List<?>>) vFiles.get(0);
                // files = new String[mapFiles.entrySet().size()];
                for (Entry<String, List<?>> mapEntry : mapFiles.entrySet()) {
                    files.add(mapEntry.getKey());
                }
            }
            return files;
        } else {
            return this.getItemValueList("$file.names", String.class);
        }
    }

    /**
     * Returns an ItemAdapter for this instance.
     * 
     * @return
     */
    public Map<String, ?> getItem() {
        return new ItemAdapter(this);
    }

    /**
     * Returns an ItemListAdapter for this instance.
     * 
     * @return
     */
    public Map<String, ?> getItemList() {
        return new ItemListAdapter(this);
    }

    /**
     * Returns an ItemListArrayAdapter for this instance.
     * 
     * @return
     */
    public Map<String, ?> getItemListArray() {
        return new ItemListArrayAdapter(this);
    }

    /**
     * @return current type
     */
    public String getType() {
        return getItemValueString(WorkflowKernel.TYPE);
    }

    /**
     * set type
     * 
     * @param type
     */
    public void setType(String type) {
        replaceItemValue(WorkflowKernel.TYPE, type);
    }

    /**
     * @return current $TaskID
     */
    public int getTaskID() {
        int result = getItemValueInteger(WorkflowKernel.TASKID);
        // test for deprecated version
        if (result == 0 && hasItem("$processid") && getItemValueInteger("$processid") != 0) {
            // see issue #384
            /*
             * logger.
             * warning("The field $processid is deprecated. Please use $taskid instead. " +
             * "Processing a workitem with an deprecated $processid is still supported.");
             */
            result = getItemValueInteger("$processid");
            // update missing taskID
            replaceItemValue(WorkflowKernel.TASKID, result);
        }
        return result;
    }

    /**
     * set $taskID
     * 
     * @param taskID
     */
    public void setTaskID(int taskID) {
        replaceItemValue(WorkflowKernel.TASKID, taskID);
        // deprecated processID is still supported for a long period. See issue #384
        replaceItemValue("$processid", taskID);
    }

    public ItemCollection task(int taskID) {
        setTaskID(taskID);
        return this;
    }

    /**
     * @return current $EventID
     */
    public int getEventID() {
        // test for deprecated version
        int result = getItemValueInteger(WorkflowKernel.EVENTID);
        if (result == 0 && hasItem("$activityid") && getItemValueInteger("$activityid") != 0) {
            logger.warning("The field $activityid is deprecated. Please use $eventid instead. "
                    + "Processing a workitem with an deprecated $activityid is still supported.");
            result = getItemValueInteger("$activityid");
            // update eventID
            replaceItemValue(WorkflowKernel.EVENTID, result);
        }
        return result;
    }

    /**
     * set $eventID
     * 
     * @param eventID
     */
    public void setEventID(int eventID) {
        replaceItemValue(WorkflowKernel.EVENTID, eventID);

        // if deprectaed ActivityID exists we must still support it
        if (hasItem("$activityid")) {
            replaceItemValue("$activityid", eventID);
        }
    }

    /**
     * Set the event id for a workitem. If a event id is already set, the method
     * appends the event to the ACTIVITYIDLIST
     * 
     * @param eventID
     * @return
     */
    public ItemCollection event(int eventID) {
        if (this.getEventID() == 0) {
            setEventID(eventID);
        } else {
            // set
            appendItemValue(WorkflowKernel.ACTIVITYIDLIST, eventID);
        }
        return this;
    }

    /**
     * @return current $ModelVersion
     */
    public String getModelVersion() {
        return getItemValueString(WorkflowKernel.MODELVERSION);
    }

    /**
     * set the $ModelVersion
     * @param modelversion
     */
    public void setModelVersion(String modelversion) {
        replaceItemValue(WorkflowKernel.MODELVERSION, modelversion);
    }

    public ItemCollection model(String modelversion) {
        setModelVersion(modelversion);
        return this;
    }

    /**
     * @return current $ModelVersion
     */
    public String getWorkflowGroup() {
        return getItemValueString(WorkflowKernel.WORKFLOWGROUP);
    }

    /**
     * set the $ModelVersion
     * @param group
     */
    public void setWorkflowGroup(String group) {
        replaceItemValue(WorkflowKernel.WORKFLOWGROUP, group);
    }

    public ItemCollection workflowGroup(String group) {
        setWorkflowGroup(group);
        return this;
    }

    /**
     * @return $UniqueID
     */
    public String getUniqueID() {
        return getItemValueString(WorkflowKernel.UNIQUEID);
    }

    /**
     * This method is deprecated. Use instead getTaskID()
     * 
     * @return current $processID
     */
    @Deprecated
    public int getProcessID() {
        int result = getItemValueInteger(WorkflowKernel.PROCESSID);
        if (result == 0 && hasItem("$taskid")) {
            result = getTaskID();
        }
        return result;
    }

    /**
     * This method is deprecated. Use instead getEventID()
     * 
     * @return current $ActivityID
     */
    @Deprecated
    public int getActivityID() {
        return getEventID();
    }

    /**
     * set $ActivityID. This method is deprecated. Use instead setEventID()
     * 
     * @param activityID
     */
    @Deprecated
    public void setActivityID(int activityID) {
        replaceItemValue("$activityid", activityID);
        // set new field $eventID
        setEventID(activityID);
    }

    /**
     * This method converts the raw java types String, int, long, float, double and BigDecimal.
     * 
     * The method returns null if the type is no raw type.
     * 
     * @param value
     * @param type
     * @return
     */
    @SuppressWarnings({"unchecked", "UnnecessaryBoxing"})
    private <T> T convertValue(Object value, Class<T> type) {

        // test String
        if (type == String.class) {
            if (value == null) {
                // return empty if not present
                return (T) "";
            } else {
                return (T) value.toString();
            }
        }

        // test Integer/int
        if (type == Integer.class || type == int.class) {
            try {
                if (value == null) {
                    // return 0 if not present
                    return (T) Integer.valueOf(0);
                }
                int intvalue = new BigDecimal(value.toString()).intValue();
                return (T) Integer.valueOf(intvalue);
            } catch (NumberFormatException | ClassCastException e) {
                return (T) Integer.valueOf(0);
            }
        }

        // test Long/long
        if (type == Long.class || type == long.class) {
            try {
                if (value == null) {
                    // return 0 if not present
                    return (T) Long.valueOf(0L);
                }
                long longvalue = new BigDecimal(value.toString()).longValue();
                return (T) Long.valueOf(longvalue);
            } catch (NumberFormatException | ClassCastException e) {
                return (T) Long.valueOf(0L);
            }
        }

        // test Float/float
        if (type == Float.class || type == float.class) {
            try {
                if (value == null) {
                    // return 0 if not present
                    return (T) Float.valueOf(0.0f);
                }
                float floatvalue = new BigDecimal(value.toString()).floatValue();
                return (T) Float.valueOf(floatvalue);
            } catch (NumberFormatException | ClassCastException e) {
                return (T) Float.valueOf(0.0f);
            }
        }

        // test Double/double
        if (type == Double.class || type == double.class) {
            try {
                if (value == null) {
                    // return 0 if not present
                    return (T) Double.valueOf(0.0d);
                }
                double doublevalue = new BigDecimal(value.toString()).doubleValue();
                return (T) Double.valueOf(doublevalue);
            } catch (NumberFormatException | ClassCastException e) {
                return (T) Double.valueOf(0.0d);
            }
        }

        return null;
    }

    /**
     * Helper method to replace an ItemValue.
     * 
     * @param itemName  - name of the value
     * @param itemValue - value
     * @param append    - true if the value should be appended to an existing list
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void setItemValue(String itemName, Object itemValue, boolean append, boolean unique) {
        List<Object> itemValueList;

        if (itemName == null)
            return;
        // lower case itemname
        itemName = itemName.toLowerCase().trim();

        // test if value is null
        if (itemValue == null) {
            // remove the item?
            if (!append) {
                this.removeItem(itemName);
            }
            return;
        }

        // test if value is ItemCollection
        if (itemValue instanceof ItemCollection) {
            // just warn - do not remove
            logger.log(Level.WARNING, "replaceItemValue ''{0}'':"
                    + " ItemCollection can not be stored into an existing"
                    + " ItemCollection - use XMLItemCollection instead.", itemName);
        }

        // test if value is a Set
        if (itemValue instanceof Set set) {
            // Let's do a conversion from a Set to a List
            itemValue = new ArrayList<>(set);
        }

        // test if value is serializable
        if (!(itemValue instanceof java.io.Serializable)) {
            logger.log(Level.WARNING, "replaceItemValue ''{0}'':"
                    + " object is not serializable!", itemName);
            this.removeItem(itemName);
            return;
        }

        // test if value is a list and remove null values
        if (itemValue instanceof List list) {
            itemValueList = list;
            // scan List for null values and remove them
            itemValueList.removeAll(Collections.singleton(null));
            // scan List for embedded ItemCollection objects
            for (int i = 0; i < itemValueList.size(); i++) {
                if (itemValueList.get(i) instanceof ItemCollection) {
                    // just warn - do not remove
                    logger.log(Level.WARNING, "replaceItemValue ''{0}'':"
                            + " ItemCollection can not be stored into an existing"
                            + " ItemCollection - use XMLItemCollection instead.", itemName);
                }
            }
        } else {
            // create an instance of an ArrayList
            itemValueList = new ArrayList<>();
            itemValueList.add(itemValue);
        }

        // now we can be sure the itemValue is an instance of List
        convertItemValue(itemValueList);
        if (!validateItemValue(itemValueList)) {
            String message = new StringBuilder("setItemValue failed for item '").append(itemName)
                    .append("', the value is a non supported object type: ").append(itemValue.getClass().getName())
                    .append(" value=").append(itemValueList).toString();
            logger.warning(message);
            throw new InvalidAccessException(message);
        }

        // replace item value?
        if (append) {
            // append item value
            List<Object> newValueList = (List<Object>) getItemValue(itemName);
            newValueList.addAll(itemValueList);

            if (unique) {
                // build a unique list of values
                List<Object> uniqueItemValueList = new ArrayList<>();
                for (Object entry : newValueList) {
                    // skip null|empty|dupplicates
                    if (entry == null || ((entry instanceof String) && ((String) entry).isEmpty())
                            || uniqueItemValueList.contains(entry)) {
                        // skip
                        continue;
                    }
                    uniqueItemValueList.add(entry);
                }
                hash.put(itemName, uniqueItemValueList);
            } else {
                hash.put(itemName, newValueList);
            }
        } else {
            if (unique) {
                // build a unique list of values
                List<Object> uniqueItemValueList = new ArrayList<>();
                for (Object entry : itemValueList) {
                    // skip null|empty|duplicates
                    if (entry == null || ((entry instanceof String) && ((String) entry).isEmpty())
                            || uniqueItemValueList.contains(entry)) {
                        // skip
                        continue;
                    }
                    uniqueItemValueList.add(entry);
                }
                hash.put(itemName, uniqueItemValueList);
            } else {
                hash.put(itemName, itemValueList);
            }

        }
    }

    /**
     * This method converts specific itemValue in standardized object classes:
     * <p>
     * <code>java.util.Calendar => java.util.Date</code>
     * <p>
     * <code>java.time.LocalDateTime => java.util.Date</code>
     * 
     * 
     * @param itemValue
     * @return
     */
    private void convertItemValue(List<? super Object> itemValues) {

        ListIterator<? super Object> iterator = itemValues.listIterator();
        while (iterator.hasNext()) {
            Object o = iterator.next();
            if (o instanceof Calendar calendar) {
                iterator.set(calendar.getTime());
            }
            if (o instanceof LocalDateTime ldt) {
                iterator.set(Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()));
            }
            if (o instanceof LocalDate ld) {
                iterator.set(Date.from(ld.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
            }
        }
    }

    /**
     * This method validates of a itemValue is acceptable for the ItemCollection.
     * Only basic types are supported.
     * 
     * @param itemValue
     * @return
     */
    @SuppressWarnings({"rawtypes", "SuspiciousIndentAfterControlStatement"})
    private boolean validateItemValue(Object itemValue) {

        // first we test if basic type?
        if (isBasicType(itemValue)) {
            return true;
        }

        // list?
        if ((itemValue instanceof List)) {
            for (Object singleValue : (List) itemValue) {
                if (!validateItemValue(singleValue)) {
                    return false;
                }
            }
            return true;
        } else

        // array?
        if (itemValue != null && itemValue.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(itemValue); i++) {
                Object singleValue = Array.get(itemValue, i);
                if (!validateItemValue(singleValue)) {
                    return false;
                }
            }
            return true;
        } else

        // map?
        if ((itemValue instanceof Map)) {
            Map map = (Map) itemValue;
            for (Object value : map.values()) {
                if (!validateItemValue(value)) {
                    return false;
                }
            }
            return true;
        }

        // unknown type
        return false;
    }

    /**
     * This helper method test if an object is a basic type which can be stored in
     * an ItemCollection.
     * 
     * Validate for raw objects and class types java.lang.*, java.math.*
     * 
     * @return
     */
    private static boolean isBasicType(java.lang.Object o) {

        if (o == null) {
            return true;
        }
        // test raw array types first
        if (o instanceof byte[] || o instanceof boolean[] || o instanceof short[] || o instanceof char[]
                || o instanceof int[] || o instanceof long[] || o instanceof float[] || o instanceof double[]
                || o instanceof XMLItem[] || o instanceof XMLDocument[]) {
            return true;
        }

        // test package name
        Class<?> c = o.getClass();
        String name = c.getName();
        // no basic type
        return name.startsWith("java.lang.") || name.startsWith("java.math.") || "java.util.Date".equals(name)
                || "org.imixs.workflow.xml.XMLItem".equals(name) || "org.imixs.workflow.xml.XMLDocument".equals(name);
    }

    /**
     * This helper method makes a deep copy of a map by serializing and
     * deserializing.
     * 
     * It is assumed that all elements in the object's source graph are
     * serializable.
     * 
     * @see http://www.javaworld.com/article/2077578/learn-java/java-tip-76--an-alternative-to-the-deep-copy-technique.html
     * @param map
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<?>> deepCopyOfMap(Map<String, List<?>> map) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            // serialize and pass the object
            oos.writeObject(map);
            oos.flush();
            ByteArrayInputStream bais = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (Map<String, List<?>>)ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.WARNING, "Unable to clone values of ItemCollection - {0}", e);
            return null;
        }
    }

    /**
     * This is a helper method to check a string value of a map.
     * 
     * @return String object of a named key
     */
    @SuppressWarnings({ "unchecked" })
    private static String getStringValueFromMap(Map<String, List<Object>> hash, String aName) {

        if (aName == null) {
            return null;
        }
        aName = aName.toLowerCase().trim();

        Object obj = hash.get(aName);
        if (obj == null) {
            return "";
        }

        if (obj instanceof List) {

            List<Object> oList = (List<Object>) obj;
            // scan List for null values
            for (int i = 0; i < oList.size(); i++) {
                if (oList.get(i) == null)
                    oList.remove(i);
            }

            if (oList.isEmpty())
                return "";
            else {
                // verify if value is null
                Object o = oList.get(0);
                if (o == null)
                    return "";
                else
                    return o.toString();
            }
        } else {
            // Value is not a list!
            logger.log(Level.WARNING, "getStringValueFromMap - wrong value object found ''{0}''", aName);
            return obj.toString();
        }
    }

    /**
     * This class helps to adapt the behavior of a single value item to be used in a
     * jsf page using a expression language like this:
     * 
     * #{mybean.item['txtMyItem']}
     * 
     * 
     * @author rsoika
     * 
     */
    class ItemAdapter implements Map<String, Object> {
        ItemCollection itemCollection;

        public ItemAdapter() {
            itemCollection = new ItemCollection();
        }

        public ItemAdapter(ItemCollection acol) {
            itemCollection = acol;
        }

        public void setItemCollection(ItemCollection acol) {
            itemCollection = acol;
        }

        /**
         * returns a single value out of the ItemCollection if the key does not exist
         * the method will create a value automatically
         */
        @Override
        public Object get(Object key) {
            // check if a value for this key is available...
            // if not create a new empty value
            if (!itemCollection.hasItem(key.toString()))
                itemCollection.replaceItemValue(key.toString(), "");

            // return first value from List if size >0
            List<?> v = itemCollection.getItemValue(key.toString());
            if (!v.isEmpty())
                return v.get(0);
            else
                // otherwise return null
                return null;
        }

        /**
         * puts a single value into the ItemCollection
         */
        @Override
        public Object put(String key, Object value) {
            if (key == null)
                return null;
            itemCollection.replaceItemValue(key, value);
            return value;
        }

        /* ############### Default methods ################# */

        @Override
        public void clear() {
            itemCollection.getAllItems().clear();
        }

        @Override
        public boolean containsKey(Object key) {
            return itemCollection.getAllItems().containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return itemCollection.getAllItems().containsValue(value);
        }

        @Override
        public Set<Map.Entry<String,Object>> entrySet() {
            Map<String, Object> localMap = new HashMap<>(itemCollection.getAllItems());
            return localMap.entrySet();
        }

        @Override
        public boolean isEmpty() {
            return itemCollection.getAllItems().isEmpty();
        }

        @Override
        public Set<String> keySet() {
            return itemCollection.getAllItems().keySet();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public void putAll(Map m) {
            itemCollection.getAllItems().putAll(m);

        }

        @Override
        public Object remove(Object key) {
            return itemCollection.getAllItems().remove(key);
        }

        @Override
        public int size() {
            return itemCollection.getAllItems().size();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public Collection<Object> values() {
            Map<String, Object> localMap = new HashMap<>(itemCollection.getAllItems());
            return localMap.values();
        }

    }

    /**
     * This class helps to addapt the behavior of a multivalue item to be used in a
     * jsf page using a expression language like this:
     * 
     * #{mybean.item['txtMyList']}
     * 
     * 
     * @author rsoika
     * 
     */
    class ItemListAdapter extends ItemAdapter {

        public ItemListAdapter(ItemCollection acol) {
            itemCollection = acol;
        }

        /**
         * returns a multi value out of the ItemCollection if the key dos not exist the
         * method will create a value automatical
         */
        @Override
        public Object get(Object key) {
            // check if a value for this key is available...
            // if not create a new empty value
            if (!itemCollection.hasItem(key.toString()))
                itemCollection.replaceItemValue(key.toString(), "");

            return itemCollection.getItemValue(key.toString());
        }

    }

    class ItemListArrayAdapter extends ItemAdapter {

        public ItemListArrayAdapter(ItemCollection acol) {
            itemCollection = acol;
        }

        /**
         * returns a multi value out of the ItemCollection if the key dos not exist the
         * method will create a value automatical
         */
        @Override
        public Object get(Object key) {
            // check if a value for this key is available...
            // if not create a new empty value
            if (!itemCollection.hasItem(key.toString()))
                itemCollection.replaceItemValue(key.toString(), "");
            // return new ArrayList Object containing values from itemValue
            ArrayList<Object> aList = new ArrayList<>();
            Collection<?> col = itemCollection.getItemValue(key.toString());
            for (Object aEntryValue : col) {
                aList.add(aEntryValue);
            }
            return aList;

        }

        /**
         * puts a arraylist value into the ItemCollection
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public Object put(String key, Object value) {
            if (key == null)
                return null;

            // skipp null values
            if (value == null) {
                itemCollection.replaceItemValue(key, new ArrayList());
                return null;
            }
            // convert value
            if (value instanceof List || value instanceof Object[]) {
                List aList = new ArrayList();
                // check type of list (array and list are supported but need
                // to be read in different ways
                if (value instanceof List list) {
                    for (Object aEntryValue : list) {
                        aList.add(aEntryValue);
                    }
                }
                if (value instanceof Object[] objects) {
                    aList.addAll(Arrays.asList(objects));
                }
                itemCollection.replaceItemValue(key, aList);
            } else
                // non convertable object!
                itemCollection.replaceItemValue(key, value);

            return value;
        }
    }
}
