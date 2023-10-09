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
 *      Volker Probst, Ralph Soika - Software Developer
 */

package org.imixs.workflow.faces.util;

import java.util.ListIterator;
import java.util.List;

import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.ConverterException;
import jakarta.faces.convert.FacesConverter;
import java.util.ArrayList;

/**
 * Converts a list of integer values into a string an vice versa. 
 */
@FacesConverter(value = "org.imixs.ListIntegerConverter")
public class ListIntegerConverter implements Converter<List<Integer>> {

    String separator = "\n";

    @Override
    public List<Integer> getAsObject(FacesContext context, UIComponent component, String value) throws ConverterException {

        List<Integer> result = new ArrayList<>();
        String[] tokens = value.split(separator);
        for (String token : tokens) {
            String sValue = token.trim();
            Integer intValue = Integer.valueOf(sValue);
            result.add(intValue);
        }

        return result;
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, List<Integer> value) throws ConverterException {
        StringBuilder result = new StringBuilder();
        ListIterator<Integer> iterator = value.listIterator();
        while (iterator.hasNext()) {
            result.append(iterator.next());
            // append separator?
            if (iterator.hasNext()) {
                result.append(separator);
            }
        }

        return result.toString();
    }

}
