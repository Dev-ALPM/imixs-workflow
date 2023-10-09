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

package org.imixs.workflow.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import jakarta.xml.bind.JAXBException;

import org.imixs.workflow.FileData;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.ItemCollectionComparator;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.QueryException;
import org.imixs.workflow.util.XMLParser;
import org.imixs.workflow.xml.XSLHandler;
import javax.xml.transform.TransformerException;
import jakarta.ejb.Stateless;
import java.util.logging.Level;

/**
 * The ReportService supports methods to create, process and find report
 * instances.
 * 
 * A Report Entity is identified by its name represented by the attribute 'name'
 * So a ReportService Implementation should ensure that name is a unique key for
 * the report entity.
 * 
 * Also each report entity holds a EQL Query in the attribute "txtquery". this
 * eql statement will be processed by the processQuery method and should return
 * a collection of entities defined by the query.
 * 
 * 
 * @author Ralph Soika
 * 
 */

@DeclareRoles({ "org.imixs.ACCESSLEVEL.NOACCESS", "org.imixs.ACCESSLEVEL.READERACCESS",
        "org.imixs.ACCESSLEVEL.AUTHORACCESS", "org.imixs.ACCESSLEVEL.EDITORACCESS",
        "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RolesAllowed({ "org.imixs.ACCESSLEVEL.NOACCESS", "org.imixs.ACCESSLEVEL.READERACCESS",
        "org.imixs.ACCESSLEVEL.AUTHORACCESS", "org.imixs.ACCESSLEVEL.EDITORACCESS",
        "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@Stateless
public class ReportService {

    private static final Logger logger = Logger.getLogger(ReportService.class.getName());

    @Inject
    DocumentService documentService;

    @Inject
    WorkflowService workflowService;

    /**
     * Returns a Report Entity by its identifier. The identifier can either be the
     * $uniqueId of the report or the report name. The method returns null if no
     * report with the given identifier exists.
     * 
     * @param reportID - name of the report or its $uniqueId.
     * @return ItemCollection representing the Report
     */
    public ItemCollection findReport(String reportID) {

        // try to load report by uniqueid
        ItemCollection result = documentService.load(reportID);
        if (result == null) {
            // try to search for name
            String searchTerm = "(type:\"ReportEntity\" AND txtname:\"" + reportID + "\")";
            Collection<ItemCollection> col;
            try {
                col = documentService.find(searchTerm, 1, 0);
            } catch (QueryException e) {
                logger.log(Level.SEVERE, "findReport - invalid id: {0}", e.getMessage());
                return null;
            }
            if (!col.isEmpty()) {
                result = col.iterator().next();
            }
        }

        return result;
    }

    /**
     * Returns a list of all reports sorted by name.
     * 
     * @return list of ItemCollection objects.
     */
    public List<ItemCollection> findAllReports() {
        List<ItemCollection> col = documentService.getDocumentsByType("ReportEntity");
        // sort resultset by name
        Collections.sort(col, new ItemCollectionComparator("txtname", true));
        return col;
    }

    /**
     * updates a Entity Report Object. The Entity representing a report must have at
     * least the attributes : txtQuery, numMaxCount, numStartPost, txtName.
     * 
     * txtName is the unique key to be use to get a query.
     * 
     * The method checks if a report with the same key allready exists. If so this
     * report will be updated. If no report exists the new report will be created
     * 
     * @param aReport
     * @throws AccessDeniedException
     * 
     */
    public void updateReport(ItemCollection aReport) throws AccessDeniedException {

        aReport.replaceItemValue("type", "ReportEntity");
        aReport.replaceItemValue("$snapshot.history", 1);

        // check if Report has a $uniqueid
        String sUniqueID = aReport.getItemValueString("$uniqueID");
        // if not try to find report by its name
        if ("".equals(sUniqueID)) {
            String sReportName = aReport.getItemValueString("txtname");
            // try to find existing Report by name.
            ItemCollection oldReport = findReport(sReportName);
            if (oldReport != null) {
                // old Report exists allready
                aReport = updateReport(aReport, oldReport);
            }
        }

        documentService.save(aReport);
    }

    /**
     * Returns the data source defined by a report.
     * <p>
     * The method executes the lucene search query defined by the Report. The values
     * of the returned entities will be cloned and formated in case a itemList is
     * provided.
     * <p>
     * The method parses the attribute txtname for a formating expression to format
     * the item value. E.g.:
     * <p>
     * {@code
     * 
     *  datDate<format locale="de" label="Date">yy-dd-mm</format>
     * 
     * }
     * <p>
     * Optional the lucene search query my contain params which will be replaced by
     * a given param Map:
     * <p>
     * 
     * <pre>
     * ($created:{date_from})
     * </pre>
     * <p>
     * In this example the literal ?{date_from} will be replaced with the given
     * value provided in the param map.
     * <p>
     * 
     * @param reportEntity - report to be executed
     * @param pageSize
     * @param pageIndex
     * @param sortBy
     * @param sortReverse
     * @param params     - optional parameter list to be mapped to the JQPL
     *                   statement
     * @return collection of entities
     * @throws QueryException
     * 
     */
    public List<ItemCollection> getDataSource(ItemCollection reportEntity, int pageSize, int pageIndex, String sortBy,
            boolean sortReverse, Map<String, String> params) throws QueryException {

        List<ItemCollection> clonedResult = new ArrayList<>();

        long l = System.currentTimeMillis();
        logger.log(Level.FINEST, "......executeReport: {0}", reportEntity.getItemValueString("txtname"));

        String query = reportEntity.getItemValueString("txtquery");

        // replace params in query statement
        if (params != null) {
            Set<String> keys = params.keySet();
            Iterator<String> iter = keys.iterator();
            while (iter.hasNext()) {
                // read key
                String sKeyName = iter.next().trim();
                String sParamValue = params.get(sKeyName);
                // test if key is contained in query
                if (query.contains("{" + sKeyName + "}")) {
                    query = query.replace("{" + sKeyName + "}", sParamValue);
                    logger.log(Level.FINEST, "......executeReport set param {0}={1}", new Object[]{sKeyName, sParamValue});
                } else {
                    // support old param format
                    if (query.contains("?" + sKeyName)) {
                        query = query.replace("?" + sKeyName, sParamValue);
                        logger.log(Level.WARNING, "......query definition in Report ''{0}'' is deprecated!"
                                + " Please replace the param ''?{1}'' with '''{'{2}'}'''",
                                new Object[]{reportEntity.getItemValueString("txtname"), sKeyName, sKeyName});
                    }
                }

            }
        }

        // now we replace dynamic Date values
        query = replaceDateString(query);

        // execute query
        logger.log(Level.FINEST, "......executeReport query={0}", query);
        List<ItemCollection> result = documentService.find(query, pageSize, pageIndex, sortBy, sortReverse);

        // test if a itemList is provided or defined in the reportEntity...
        List<List<String>> attributes = reportEntity.getItemValueListList("attributes", String.class);
        List<String> itemNames = new ArrayList<>();
        for (List<String> attribute : attributes) {
            itemNames.add(attribute.get(0));
        }

        // next we iterate over all entities from the result set and clone
        // each entity with the given attribute list and format instructions
        for (ItemCollection entity : result) {

            // in case _ChildItems are requested the entity will be
            // duplicated for each child attribute.
            // a child item is identified by the '~' char in the item name
            List<ItemCollection> embeddedChildItems = getEmbeddedChildItems(entity, itemNames);
            if (!embeddedChildItems.isEmpty()) {
                for (ItemCollection child : embeddedChildItems) {
                    ItemCollection clone = cloneEntity(child, attributes);
                    clonedResult.add(clone);
                }
            } else {
                // default - clone the entity
                ItemCollection clone = cloneEntity(entity, attributes);
                clonedResult.add(clone);
            }
        }
        logger.log(Level.FINE, "...executed report ''{0}'' in {1}ms",
                new Object[]{reportEntity.getItemValueString("txtname"), System.currentTimeMillis() - l});
        return clonedResult;

    }

    /**
     * Transforms a datasource based on the XSL template from a report into a
     * FileData object.
     * 
     * @param report   - the report definition
     * @param data     - the data source
     * @param fileName
     * @return FileData object containing the transformed data source.
     * @throws JAXBException
     * @throws TransformerException
     * @throws IOException
     */
    public FileData transformDataSource(ItemCollection report, List<ItemCollection> data, String fileName)
            throws JAXBException, IOException, TransformerException {

        String xslTemplate = report.getItemValueString("xsl").trim();
        // execute the transformation based on the report defintion....
        String sContentType = report.getItemValueString("contenttype");
        if ("".equals(sContentType)) {
            sContentType = MediaType.TEXT_XML;
        }
        String encoding = report.getItemValueString("encoding");
        if ("".equals(encoding)) {
            // no encoding defined so we default to UTF-8
            encoding = "UTF-8";
        }

        byte[] _bytes;
        // create a ByteArray Output Stream
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSLHandler.transform(data, xslTemplate, encoding, outputStream);
            _bytes = outputStream.toByteArray();
        }
        FileData fileData = new FileData(fileName, _bytes, sContentType, null);

        return fileData;

    }

    /**
     * This method parses a {@code <date />} xml tag and computes a dynamic date by parsing
     * the attributes:
     * 
     * DAY_OF_MONTH
     * 
     * DAY_OF_YEAR
     * 
     * MONTH
     * 
     * YEAR
     * 
     * ADD (FIELD,OFFSET)
     * 
     * <p>
     * e.g. {@code<date DAY_OF_MONTH="1" MONTH="2" />}
     * <p>
     * results in 1. February of the current year
     * <p>
     * 
     * {@code<date DAY_OF_MONTH="ACTUAL_MAXIMUM" MONTH="12" ADD="MONTH,-1" />}
     * <p>
     * results in 30.November of current year
     * 
     * @param xmlDate
     * @return
     */
    public Calendar computeDynamicDate(String xmlDate) {
        Calendar cal = Calendar.getInstance();

        Map<String, String> attributes = XMLParser.findAttributes(xmlDate);

        // test MONTH
        if (attributes.containsKey("MONTH")) {
            String value = attributes.get("MONTH");
            if ("ACTUAL_MAXIMUM".equalsIgnoreCase(value)) {
                // last month of year
                cal.set(Calendar.MONTH, cal.getActualMaximum(Calendar.MONTH));
            } else {
                cal.set(Calendar.MONTH, Integer.parseInt(value) - 1);
            }
        }

        // test YEAR
        if (attributes.containsKey("YEAR")) {
            String value = attributes.get("YEAR");
            cal.set(Calendar.YEAR, Integer.parseInt(value));

        }

        // test DAY_OF_MONTH
        if (attributes.containsKey("DAY_OF_MONTH")) {
            String value = attributes.get("DAY_OF_MONTH");
            if ("ACTUAL_MAXIMUM".equalsIgnoreCase(value)) {
                // last day of month
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            } else {
                cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(value));
            }
        }

        // test DAY_OF_YEAR
        if (attributes.containsKey("DAY_OF_YEAR")) {
            cal.set(Calendar.DAY_OF_YEAR, Integer.parseInt(attributes.get("DAY_OF_YEAR")));
        }

        // test ADD
        if (attributes.containsKey("ADD")) {
            String value = attributes.get("ADD");
            String[] fieldOffset = value.split(",");

            String field = fieldOffset[0];
            int offset = Integer.parseInt(fieldOffset[1]);

            if ("MONTH".equalsIgnoreCase(field)) {
                cal.add(Calendar.MONTH, offset);
            } else if ("DAY_OF_MONTH".equalsIgnoreCase(field)) {
                cal.add(Calendar.DAY_OF_MONTH, offset);
            } else if ("DAY_OF_YEAR".equalsIgnoreCase(field)) {
                cal.add(Calendar.DAY_OF_YEAR, offset);
            }

        }

        return cal;

    }

    /**
     * This method replaces all occurrences of {@code<date>} tags with the
     * corresponding dynamic date. See computeDynamicdate.
     * 
     * @param content
     * @return
     */
    public String replaceDateString(String content) {

        List<String> dates = XMLParser.findTags(content, "date");
        for (String dateString : dates) {
            Calendar cal = computeDynamicDate(dateString);
            // convert into lucene format 20020101
            DateFormat f = new SimpleDateFormat("yyyyMMdd");
            // f.setTimeZone(tz);
            String dateValue = f.format(cal.getTime());
            content = content.replace(dateString, dateValue);
        }

        return content;
    }

    /**
     * This method converts a double value into a custom number format including an
     * optional locale.
     * 
     * <pre>
     * {@code
     * 
     * "###,###.###", "en_UK", 123456.789
     * 
     * "EUR #,###,##0.00", "de_DE", 1456.781
     * 
     * }
     * </pre>
     * 
     * @param pattern
     * @param locale
     * @param value
     * @return
     */
    public String customNumberFormat(String pattern, String locale, double value) {
        DecimalFormat formatter;
        Locale _locale = getLocaleFromString(locale);
        // test if we have a locale
        if (_locale != null) {
            formatter = (DecimalFormat) DecimalFormat.getInstance(getLocaleFromString(locale));
        } else {
            formatter = (DecimalFormat) DecimalFormat.getInstance();
        }
        formatter.applyPattern(pattern);
        return formatter.format(value);
    }

    /**
     * This helper method clones a entity with a given format and converter map.
     * 
     * @param formatMap
     * @param converterMap
     * @param entity
     * @return
     */
    private ItemCollection cloneEntity(ItemCollection entity, List<List<String>> attributes) {
        ItemCollection clone;

        // if we have a itemList we clone each entity of the result set
        if (attributes != null && !attributes.isEmpty()) {
            clone = new ItemCollection();
            for (List<String> attribute : attributes) {

                String field = attribute.get(0);
                if (attribute.size() >= 1) {
                    attribute.get(1);
                }
                String convert = "";
                if (attribute.size() >= 2) {
                    convert = attribute.get(2);
                }

                String format = "";
                if (attribute.size() >= 3) {
                    format = attribute.get(3);
                }

                // first look for converter

                // did we have a format definition?
                List<? super Object> values = new ArrayList<>(entity.getItemValue(field));
                if (!convert.isEmpty()) {
                    values = convertItemValue(entity, field, convert);
                }

                // did we have a format definition?
                if (!format.isEmpty()) {
                    String sLocale = XMLParser.findAttribute(format, "locale");
                    // test if we have a XML format tag
                    List<String> content = XMLParser.findTagValues(format, "format");
                    if (!content.isEmpty()) {
                        format = content.get(0);
                    }
                    // create string array of formated values
                    List<?> rawValues = values;
                    values = new ArrayList<>();
                    for (Object rawValue : rawValues) {
                        values.add(formatObjectValue(rawValue, format, sLocale));
                    }

                }

                clone.replaceItemValue(field, values);
            }
        } else {
            // clone all attributes
            clone = entity.clone();

        }
        return clone;
    }

    /**
     * This methode updates the a itemCollection with the attributes supported by
     * another itemCollection without the $uniqueid
     * 
     * @param aworkitem
     * 
     */
    private ItemCollection updateReport(ItemCollection newReport, ItemCollection oldReport) {
        for (Map.Entry<String, List<?>> mapEntry : newReport.getAllItems().entrySet()) {
            String sName = mapEntry.getKey();
            Object o = mapEntry.getValue();
            if (isValidAttributeName(sName)) {
                oldReport.replaceItemValue(sName, o);
            }
        }
        return oldReport;
    }

    /**
     * This method returns true if the attribute name can be updated by a client.
     * Workflow Attributes are not valid
     * 
     * @param aName
     * @return
     */
    private boolean isValidAttributeName(String aName) {
        if ("$creator".equalsIgnoreCase(aName))
            return false;
        if ("namcreator".equalsIgnoreCase(aName))
            return false;
        if ("$created".equalsIgnoreCase(aName))
            return false;
        if ("$modified".equalsIgnoreCase(aName))
            return false;
        if ("$uniqueID".equalsIgnoreCase(aName))
            return false;
        return !"$isAuthor".equalsIgnoreCase(aName);

    }

    /**
     * This helper method test the type of an object and formats the objects value.
     * 
     * If the object if from type Date or Calendar it will be formated unsing the
     * Java SimpleDateFormat.
     * 
     * If the object is String, Integer or Double the method tries to format the
     * value into a number
     * 
     *
     * 
     * @param o
     * @return
     */
    private String formatObjectValue(Object o, String format, String locale) {
        String singleValue = "";
        Date dateValue = null;

        // now test the objct type to date
        if (o instanceof Date date) {
            dateValue = date;
        }

        if (o instanceof Calendar cal) {
            dateValue = cal.getTime();
        }

        // format date string?
        if (dateValue != null) {
            if (format != null && !"".equals(format)) {
                // format date with provided formater
                try {
                    SimpleDateFormat formatter;
                    if (locale != null && !locale.isEmpty()) {
                        formatter = new SimpleDateFormat(format, getLocaleFromString(locale));
                    } else {
                        formatter = new SimpleDateFormat(format);
                    }
                    singleValue = formatter.format(dateValue);
                } catch (Exception ef) {
                    logger.log(Level.WARNING, "ReportService: Invalid format String ''{0}''", format);
                    logger.log(Level.WARNING, "ReportService: Can not format value - error: {0}", ef.getMessage());
                    return "" + dateValue;
                }
            } else {
                // use standard formate short/short
                singleValue = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(dateValue);
            }

        } else {
            if(o != null) {
                // test if number formater is provided....
                if (format.contains("#")) {
                    try {
                        double d = Double.parseDouble(o.toString());
                        singleValue = customNumberFormat(format, locale, d);
                    } catch (IllegalArgumentException e) {
                        logger.log(Level.WARNING, "Format Error ({0}) = {1}", new Object[]{format, e.getMessage()});
                        singleValue = "0";
                    }

                } else {
                    // return object as string
                    singleValue = o.toString();
                }
            }
        }

        return singleValue;

    }

    /**
     * This method converts a single item value into a specified type. If the
     * converter is not adaptable a default value will be set.
     *
     * 
     * @param o
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<Object> convertItemValue(ItemCollection itemcol, String itemName, String converter) {

        if (converter == null || converter.isEmpty()) {
            return new ArrayList<>(itemcol.getItemValue(itemName));
        }

        List values = itemcol.getItemValue(itemName);
        // if list is empty we add a dummy null value here!
        if (values.isEmpty()) {
            values.add(null);
        }

        // first test if we have a custom converter
        List<String> adaptedValueList = null;
        if (converter.startsWith("<") && converter.endsWith(">")) {
            try {
                logger.log(Level.FINEST, "......converter = {0}", converter);
                // adapt the value list...
                adaptedValueList = workflowService.adaptTextList(converter, itemcol);
            } catch (PluginException e) {
                logger.log(Level.WARNING, "Unable to adapt text converter: {0}", converter);
            }
        }

        if (adaptedValueList != null) {
            values = new ArrayList<String>();
            values.addAll(adaptedValueList);
        }

        for (int i = 0; i < values.size(); i++) {
            Object o = values.get(i);

            if (converter.equalsIgnoreCase("double") || converter.equalsIgnoreCase("xs:decimal")) {
                try {
                    double d = 0;
                    if (o != null) {
                        d = Double.parseDouble(o.toString());
                    }
                    values.set(i, d);
                } catch (NumberFormatException e) {
                    values.set(i, 0.0d);
                }
            }

            if (converter.equalsIgnoreCase("integer") || converter.equalsIgnoreCase("xs:int")) {
                try {
                    int d = 0;
                    if (o != null) {
                        i = Integer.parseInt(o.toString());
                    }
                    values.set(i, d);
                } catch (NumberFormatException e) {
                    values.set(i, 0);
                }
            }

        }
        return values;

    }

    /**
     * generates a Locale Object form a String
     * 
     * fr_FR , en_US,
     * 
     * @param sLocale
     * @return
     */
    private static Locale getLocaleFromString(String sLocale) {
        Locale locale = null;

        // genreate locale?
        if (sLocale != null && !sLocale.isEmpty()) {
            // split locale
            StringTokenizer stLocale = new StringTokenizer(sLocale, "_");
            if (stLocale.countTokens() == 1) {
                // only language variant
                String sLang = stLocale.nextToken();
                String sCount = sLang.toUpperCase();
                locale = new Locale(sLang, sCount);
            } else {
                // language and country
                String sLang = stLocale.nextToken();
                String sCount = stLocale.nextToken();
                locale = new Locale(sLang, sCount);
            }
        }

        return locale;
    }

    /**
     * This method returns all embedded child items of a entity. The childItem are
     * identified by a fieldname containing a '~'. The left part is the container
     * item (List of Map), the right part is the attribute name in the child
     * itemcollection.
     * 
     * @param entity
     * @param keySet
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<ItemCollection> getEmbeddedChildItems(ItemCollection entity, List<String> fieldNames) {
        List<String> embeddedItemNames = new ArrayList<>();
        List<ItemCollection> result = new ArrayList<>();
        // first find all items containing a child element
        for (String field : fieldNames) {
            field = field.toLowerCase();
            if (field.contains("~")) {
                field = field.substring(0, field.indexOf('~'));
                if (!embeddedItemNames.contains(field)) {
                    embeddedItemNames.add(field);
                }
            }
        }
        if (!embeddedItemNames.isEmpty()) {
            for (String field : embeddedItemNames) {
                List<Object> mapChildItems = new ArrayList<>(entity.getItemValue(field));
                // try to convert
                for (Object mapOderItem : mapChildItems) {
                    if (mapOderItem instanceof Map map) {
                        ItemCollection child = new ItemCollection(map);
                        // clone entity and add all map entries
                        ItemCollection clone = new ItemCollection(entity);
                        Set<String> childFieldNameList = child.getAllItems().keySet();
                        for (String childFieldName : childFieldNameList) {
                            clone.replaceItemValue(field + "~" + childFieldName, child.getItemValue(childFieldName));
                        }
                        result.add(clone);
                    }
                }
            }
        }
        return result;
    }
}
