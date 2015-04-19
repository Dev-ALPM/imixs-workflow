/*******************************************************************************
 *  Imixs Workflow 
 *  Copyright (C) 2001, 2011 Imixs Software Solutions GmbH,  
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
 *  	http://www.imixs.org
 *  	http://java.net/projects/imixs-workflow
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.workflow.jaxrs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.xml.XMLItemCollection;
import org.imixs.workflow.xml.XMLItemCollectionAdapter;

@Provider
@Produces("text/html")
public class XMLItemCollectionWriter implements MessageBodyWriter<XMLItemCollection> {

	
	public boolean isWriteable(Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		
		return XMLItemCollection.class.isAssignableFrom(type);
	}

	public void writeTo(XMLItemCollection xmlItemCollection, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders,
			OutputStream entityStream) throws IOException,
			WebApplicationException {
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
				entityStream));
		
		
		
		bw.write("<html>");
		printHead(bw);
		bw.write("<body>");
		try {
			bw.write("<h1>Entity</h1>");
			
			printXMLItemCollectionHTML(bw, xmlItemCollection);

			
		} catch (Exception e) {
			bw.write("ERROR<br>");
			//e.printStackTrace(bw.);
		}
		

		bw.write("</body>");
		bw.write("</html>");
		
		bw.flush();
	}

	public long getSize(XMLItemCollection arg0, Class<?> arg1, Type arg2,
			Annotation[] arg3, MediaType arg4) {
		return -1;
	}

	
	
	

	
	
	
	/**
	 * This Method prints a single XMLItemCollection in html format
	 * 
	 * @param out
	 * @param workItem
	 * @throws IOException 
	 */
	@SuppressWarnings("rawtypes")
	public static void printXMLItemCollectionHTML(BufferedWriter bw, XMLItemCollection xmlworkItem) throws IOException {
		boolean trClass = false;

		ItemCollection workItem = XMLItemCollectionAdapter.getItemCollection(xmlworkItem);
		bw.write("<table><tbody>");

		bw.write("<tr class=\"a\">");

		/*
		bw.write("<th colspan=\"2\"><b>UniqueID: </b><a href=\""
				+workItem.getItemValueString("$uniqueid") + 
				"\">"
				+ workItem.getItemValueString("$uniqueid") + "</a></th></tr>");
		*/
		bw.write("<th colspan=\"2\"><b>UniqueID: "
				+ workItem.getItemValueString("$uniqueid") + "</b></th></tr>");

		bw.write("<tr class=\"a\">");

		bw.write("<th>Property</th><th>Value</th></tr>");

		Iterator iter = workItem.getAllItems().entrySet().iterator();
		while (iter.hasNext()) {

			// WorkItemAttribute da = new WorkItemAttribute();
			Map.Entry mapEntry = (Map.Entry) iter.next();
			String sName = mapEntry.getKey().toString();
			List value = (List) mapEntry.getValue();

			if (trClass)
				bw.write("<tr class=\"a\">");
			else
				bw.write("<tr class=\"b\">");
			trClass = !trClass;

			bw.write("<td>" + sName + "</td><td>"
					+ convertValuesToString(value) + "</td></tr>");

		}
		bw.write("</tbody></table>");

		bw.write("<br/><br/>");
	}
	
	
	/**
	 * This method converts the Values of a vector into a string representation.
	 * 
	 * Multivalues will be separated with '~' characters. Date Objects will be
	 * converted into a short String representation taking the server locale
	 * 
	 * 
	 * @param values
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static String convertValuesToString(List values) {
		String convertedValue = "";

		if (values == null)
			return convertedValue;

		boolean bFirstValue = true;
		// Iterate over vector list
		Iterator iter = values.iterator();
		while (iter.hasNext()) {
			Object o = iter.next();
			Date dateValue = null;
			// now test the objct type to date
			if (o instanceof Date) {
				dateValue = (Date) o;
			}
			if (o instanceof Calendar) {
				Calendar cal = (Calendar) o;
				dateValue = cal.getTime();
			}

			// if it is not the first value then add the delimiter ~
			String singleValue = "";
			if (!bFirstValue)
				convertedValue += "~";

			// if value is a date object format date into a string
			// otherwise take the value as it is
			if (dateValue != null) {
				singleValue = DateFormat.getDateTimeInstance(DateFormat.SHORT,
						DateFormat.SHORT).format(dateValue);
			} else {
				// take value as it is
				if (o != null)
					singleValue = singleValue + o.toString();
			}

			convertedValue += singleValue;
			bFirstValue = false;
		}

		// return values as string
		return convertedValue;
	}
	
	/**
	 * prints the generic HTML Head for HTML output.
	 * The method genereates a default CSS definition for table output
	 * 
	 * @param bw
	 * @throws IOException 
	 */
	public static void printHead(BufferedWriter bw) throws IOException {
		bw.write("<head>");
		bw.write("<style>");
		bw.write("table {padding:0px;width: 100%;margin-left: -2px;margin-right: -2px;}");
		bw.write("body,td,select,input,li {font-family: Verdana, Helvetica, Arial, sans-serif;font-size: 13px;}");
		bw.write("table th {color: white;background-color: #bbb;text-align: left;font-weight: bold;}");

		bw.write("table th,table td {font-size: 12px;}");

		bw.write("table tr.a {background-color: #ddd;}");

		bw.write("table tr.b {background-color: #eee;}");

		bw.write("</style>");
		bw.write("</head>");
	}
	
}
