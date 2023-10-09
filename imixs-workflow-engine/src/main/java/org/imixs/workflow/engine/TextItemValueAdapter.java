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

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Observes;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.util.XMLParser;

/**
 * The TextItemValueAdapter replaces text fragments with the values of a named
 * Item.
 * 
 * @author rsoika
 *
 */
@Stateless
public class TextItemValueAdapter {

    private static final Logger logger = Logger.getLogger(TextItemValueAdapter.class.getName());

    /**
     * This method reacts on CDI events of the type TextEvent and parses a string
     * for xml tag [{@code<itemvalue>}. Those tags will be replaced with the
     * corresponding item value:
     * <p>
     * {@code hello <itemvalue>$Creator</itemvalue>}
     * <p>
     * Item values can also be formated. e.g. for date/time values:
     * <p>
     * {@code  Last access Time= <itemvalue format="mm:ss">$created</itemvalue>}
     * <p>
     * If the itemValue is a multiValue object the single values can be spearated by
     * a separator:
     * <p>
     * {@code Phone List: <itemvalue separator="<br />">txtPhones</itemvalue>}
     * 
     * 
     * @param event
     */
    public void onEvent(@Observes TextEvent event) {
        boolean debug = logger.isLoggable(Level.FINE);
        String text = event.getText();
        ItemCollection documentContext = event.getDocument();

        if (text == null)
            return;

        // lower case <itemValue> into <itemvalue>
        if (text.contains("<itemValue") || text.contains("</itemValue>")) {
            logger.warning("Deprecated <itemValue> tag should be lowercase <itemvalue> !");
            text = text.replace("<itemValue", "<itemvalue");
            text = text.replace("</itemValue>", "</itemvalue>");
        }

        List<String> tagList = XMLParser.findTags(text, "itemvalue");
        if (debug) {
            logger.log(Level.FINEST, "......{0} tags found", tagList.size());
        }
        // test if a <value> tag exists...
        for (String tag : tagList) {

            // next we check if the start tag contains a 'format' attribute
            String sFormat = XMLParser.findAttribute(tag, "format");

            // next we check if the start tag contains a 'separator' attribute
            String sSeparator = XMLParser.findAttribute(tag, "separator");

            // next we check if the start tag contains a 'position' attribute
            String sPosition = XMLParser.findAttribute(tag, "position");

            // extract locale...
            Locale locale = null;
            String sLocale = XMLParser.findAttribute(tag, "locale");
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

            // extract Item Value
            String sItemValue = XMLParser.findTagValue(tag, "itemvalue");

            // format field value
            List<?> vValue = documentContext.getItemValue(sItemValue);

            String sResult = formatItemValues(vValue, sSeparator, sFormat, locale, sPosition);

            // now replace the tag with the result string
            int iStartPos = text.indexOf(tag);
            int iEndPos = text.indexOf(tag) + tag.length();

            text = text.substring(0, iStartPos) + sResult + text.substring(iEndPos);
        }
        event.setText(text);

    }

    /**
     * This method returns a formated a string object.
     * 
     * In case a Separator is provided, multiValues will be separated by the
     * provided separator.
     * 
     * If no separator is provide, only the first value will returned.
     * 
     * The format and locale attributes can be used to format number and date
     * values.
     * 
     * @param aItem
     * @param aSeparator
     * @param sFormat
     * @param locale
     * @param sPosition
     * @return 
     */
    public String formatItemValues(List<?> aItem, String aSeparator, String sFormat, Locale locale, String sPosition) {

        StringBuilder sBuffer = new StringBuilder();

        if (aItem == null || aItem.isEmpty())
            return "";

        // test if a position was defined?
        if (sPosition == null || sPosition.isEmpty()) {
            // no - we iterate over all...
            for (Object aSingleValue : aItem) {
                String aValue = formatObjectValue(aSingleValue, sFormat, locale);
                sBuffer.append(aValue);
                // append delimiter only if a separator is defined
                if (aSeparator != null) {
                    sBuffer.append(aSeparator);
                } else {
                    // no separator, so we can exit with the first value
                    break;
                }
            }
        } else {
            // evaluate position
            if ("last".equalsIgnoreCase(sPosition)) {
                sBuffer.append(aItem.get(aItem.size() - 1));
            } else {
                // default first poistion
                sBuffer.append(aItem.get(0));
            }

        }

        String sString = sBuffer.toString();

        // cut last separator
        if (aSeparator != null && sString.endsWith(aSeparator)) {
            sString = sString.substring(0, sString.lastIndexOf(aSeparator));
        }

        return sString;

    }

    /**
     * this method formats a string object depending of an attribute type.
     * MultiValues will be separated by the provided separator
     * @param aItem
     * @param aSeparator
     * @param sFormat
     * @return 
     */
    public String formatItemValues(List<?> aItem, String aSeparator, String sFormat) {
        return formatItemValues(aItem, aSeparator, sFormat, null, null);
    }

    /**
     * this method formats a string object depending of an attribute type.
     * MultiValues will be separated by the provided separator
     * @param aItem
     * @param aSeparator
     * @param sFormat
     * @param alocale
     * @return 
     */
    public String formatItemValues(List<?> aItem, String aSeparator, String sFormat, Locale alocale) {
        return formatItemValues(aItem, aSeparator, sFormat, alocale, null);
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
     * @param value
     * @return
     */
    private String customNumberFormat(String pattern, Locale _locale, double value) {
        DecimalFormat formatter;

        // test if we have a locale
        if (_locale != null) {
            formatter = (DecimalFormat) DecimalFormat.getInstance(_locale);
        } else {
            formatter = (DecimalFormat) DecimalFormat.getInstance();
        }
        formatter.applyPattern(pattern);
        String output = formatter.format(value);

        return output;
    }

    /**
     * This helper method test the type of an object provided by a itemcollection
     * and formats the object into a string value.
     * 
     * Only Date Objects will be formated into a modified representation. other
     * objects will be returned using the toString() method.
     * 
     * If an optional format is provided this will be used to format date objects.
     * 
     * @param o
     * @return
     */
    private String formatObjectValue(Object o, String format, Locale locale) {
        String singleValue;
        Date dateValue = null;
        
        if(o == null) {
            return "";
        }
        
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
                    if (locale != null) {
                        formatter = new SimpleDateFormat(format, locale);
                    } else {
                        formatter = new SimpleDateFormat(format);
                    }
                    singleValue = formatter.format(dateValue);
                } catch (Exception ef) {
                    logger.log(Level.WARNING, "TextItemValueAdapter: Invalid format String ''{0}''", format);
                    logger.log(Level.WARNING, "TextItemValueAdapter: Can not format value - error: {0}", ef.getMessage());
                    return "" + dateValue;
                }
            } else {
                // use standard formate short/short
                singleValue = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(dateValue);
            }

        } else {
            // test if number formater is provided....
            if (format != null && format.contains("#")) {
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

        return singleValue;

    }

}
