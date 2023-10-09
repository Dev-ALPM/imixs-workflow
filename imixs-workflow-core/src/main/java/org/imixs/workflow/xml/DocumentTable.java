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

package org.imixs.workflow.xml;

import java.util.List;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * The JAXB DocumentTable represents a list of documents in a table format. For
 * each document the same list of items will be added into a separate row. The
 * property labels contans the table headers.
 * 
 * 
 * @author rsoika
 * @version 2.0.0
 */
@XmlRootElement(name = "data")
public class DocumentTable implements Serializable {

    private static final long serialVersionUID = 1L;
    private XMLDocument[] documents;
    private ArrayList<String> items;
    private ArrayList<String> labels;
    private String encoding;

    public DocumentTable() {
        this(new XMLDocument[] {}, null, null, null);
    }

    public DocumentTable(XMLDocument[] documents, List<String> items, List<String> labels, String encoding) {
        this.documents = documents;
        this.items = new ArrayList<>(items);
        this.labels = new ArrayList<>(labels);
        this.encoding = encoding;
    }

    public XMLDocument[] getDocuments() {
        return documents;
    }

    public void setDocuments(XMLDocument[] document) {
        this.documents = document;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = new ArrayList<>(items);
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = new ArrayList<>(labels);
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

}
