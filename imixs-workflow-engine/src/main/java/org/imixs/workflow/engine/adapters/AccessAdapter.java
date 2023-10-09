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

package org.imixs.workflow.engine.adapters;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import org.imixs.workflow.GenericAdapter;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;

/**
 * The AccessAdapter is a generic adapter class responsible to update the ACL of
 * a workitem. The CID Bean updates the following Items
 * <ul>
 * <li>$writeAccess</li>
 * <li>$readAccess</li>
 * <li>$participants</li>
 * </ul>
 * <p>
 * The read and write access for a workitem can be defined by the BPMN model
 * with the ACL Properties of the Imixs-BPMN modeler.
 * <p>
 * The participants is a computed list of all users who edited this workitem.
 * <p>
 * By defining an CDI alternative an application can overwrite the behavior of
 * this bean.
 * 
 * @author rsoika
 * @version 1.0.0
 */
@Named
public class AccessAdapter implements GenericAdapter {

    private static final Logger logger = Logger.getLogger(AccessAdapter.class.getName());

    // See CDI Constructor
    protected WorkflowService workflowService;

    /**
     * Default Constructor
     */
    public AccessAdapter() {
        super();
    }

    /**
     * CDI Constructor to inject WorkflowService
     * 
     * @param workflowService
     */
    @Inject
    public AccessAdapter(WorkflowService workflowService) {
        super();
        this.workflowService = workflowService;
    }

    /**
     * The Execute method updates the ACL of a process instance based on a given
     * event.
     * 
     */
    @Override
    public ItemCollection execute(ItemCollection document, ItemCollection event) throws AdapterException {
        ItemCollection nextTask;
        // get next process entity
        try {
            // nextTask = workflowService.evalNextTask(document, event);
            nextTask = workflowService.evalNextTask(document);
            // in case the event is connected to a followup activity the
            // nextProcess can be null!

            updateParticipants(document);
            updateACL(document, event, nextTask);

        } catch (ModelException | PluginException e) {
            throw new AdapterException(AccessAdapter.class.getSimpleName(), e.getErrorCode(), e.getMessage());
        }
        return null;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;

    }

    /**
     * Update the $PARTICIPANTS.
     * 
     * @param workitem
     * @return
     */
    public ItemCollection updateParticipants(ItemCollection workitem) {
        List<String> participants = workitem.getItemValueList(WorkflowService.PARTICIPANTS, String.class);
        String user = workflowService.getUserName();
        if (!participants.contains(user)) {
            participants.add(user);
            workitem.replaceItemValue(WorkflowService.PARTICIPANTS, participants);
        }

        return workitem;
    }

    /**
     * This method updates the $readAccess and $writeAccess attributes of a WorkItem
     * depending to the configuration of a Activity Entity.
     * 
     * The method evaluates the new model flag keyupdateacl. If 'false' then acl
     * will not be updated.
     * 
     * 
     * @param workitem
     * @param event
     * @param nextTask
     * @return 
     * @throws org.imixs.workflow.exceptions.PluginException 
     */
    public ItemCollection updateACL(ItemCollection workitem, ItemCollection event, ItemCollection nextTask)
            throws PluginException {

        if (event == null && nextTask == null) {
            // no update!
            return workitem;
        }
        ItemCollection documentContext = workitem;

        // test update mode of activity and process entity - if true clear the
        // existing values.
        if ((event == null || event.getItemValueBoolean("keyupdateacl") == false)
                && (nextTask == null || nextTask.getItemValueBoolean("keyupdateacl") == false)) {
            // no update!
            return documentContext;
        } else {
            // clear existing settings!
            documentContext.replaceItemValue(WorkflowService.READACCESS, new ArrayList<>());
            documentContext.replaceItemValue(WorkflowService.WRITEACCESS, new ArrayList<>());

            // event settings will not be merged with task settings!
            if (event != null && event.getItemValueBoolean("keyupdateacl") == true) {
                updateACLByItemCollection(documentContext, event);
            } else {
                updateACLByItemCollection(documentContext, nextTask);
            }
        }

        return documentContext;
    }

    /**
     * This method updates the read/write access of a workitem depending on a given
     * model entity The model entity should provide the following attributes:
     * 
     * keyupdateacl,
     * namaddreadaccess,keyaddreadfields,keyaddwritefields,namaddwriteaccess
     * 
     * 
     * The method did not clear the exiting values of $writeAccess and $readAccess
     * 
     * @throws PluginException
     */
    private void updateACLByItemCollection(ItemCollection documentContext, ItemCollection modelEntity)
            throws PluginException {
        boolean debug = logger.isLoggable(Level.FINE);
        if (modelEntity == null || modelEntity.getItemValueBoolean("keyupdateacl") == false) {
            // no update necessary
            return;
        }

        List<String> access;
        access = documentContext.getItemValueList(WorkflowService.READACCESS, String.class);
        // add roles
        mergeRoles(access, modelEntity.getItemValueList("namaddreadaccess", String.class), documentContext);
        // add Mapped Fields
        mergeFieldList(documentContext, access, modelEntity.getItemValueList("keyaddreadfields", String.class));
        // clean List
        access = uniqueList(access);

        // update accesslist....
        documentContext.replaceItemValue(WorkflowService.READACCESS, access);
        if ((debug) && (!access.isEmpty())) {
            logger.finest("......[AccessPlugin] ReadAccess:");
            for (int j = 0; j < access.size(); j++)
                logger.log(Level.FINEST, "               ''{0}''", access.get(j));
        }

        // update WriteAccess
        access = documentContext.getItemValueList(WorkflowService.WRITEACCESS, String.class);
        // add Names
        mergeRoles(access, modelEntity.getItemValueList("namaddwriteaccess", String.class), documentContext);
        // add Mapped Fields
        mergeFieldList(documentContext, access, modelEntity.getItemValueList("keyaddwritefields", String.class));
        // clean List
        access = uniqueList(access);

        // update accesslist....
        documentContext.replaceItemValue(WorkflowService.WRITEACCESS, access);
        if ((debug) && (!access.isEmpty())) {
            logger.finest("......[AccessPlugin] WriteAccess:");
            for (int j = 0; j < access.size(); j++)
                logger.log(Level.FINEST, "               ''{0}''", access.get(j));
        }

    }

    /**
     * This method merges the values of fieldList into valueList and test for
     * duplicates.
     * 
     * If an entry of the fieldList is a single key value, than the values to be
     * merged are read from the corresponding documentContext property
     * 
     * e.g. 'namTeam' -> maps the values of the documentContext property 'namteam'
     * into the valueList
     * 
     * If an entry of the fieldList is in square brackets, than the comma separated
     * elements are mapped into the valueList
     * 
     * e.g. '[user1,user2]' - maps the values 'user1' and 'user2' int the valueList.
     * Also Curly brackets are allowed '{user1,user2}'
     * 
     * 
     * @param documentContext
     * @param valueList
     * @param fieldList
     */
    public void mergeFieldList(ItemCollection documentContext, List<String> valueList, List<String> fieldList) {
        if (valueList == null || fieldList == null)
            return;
        List<String> values;
        if (!fieldList.isEmpty()) {
            // iterate over the fieldList
            for (String key : fieldList) {
                if (key == null) {
                    continue;
                }
                key = key.trim();
                // test if key contains square or curly brackets?
                if ((key.startsWith("[") && key.endsWith("]")) || (key.startsWith("{") && key.endsWith("}"))) {
                    // extract the value list with regExpression (\s matches any
                    // white space, The * applies the match zero or more times.
                    // So \s* means "match any white space zero or more times".
                    // We look for this before and after the comma.)
                    values = Arrays.asList(key.substring(1, key.length() - 1).split("\\s*,\\s*"));
                } else {
                    // extract value list form documentContext
                    values = documentContext.getItemValueList(key, String.class);
                }
                // now append the values into valueList
                if ((values != null) && (!values.isEmpty())) {
                    for (String o : values) {
                        // append only if not used
                        if (valueList.indexOf(o) == -1)
                            valueList.add(o);
                    }
                }
            }
        }

    }

    /**
     * This method removes duplicates and null values from a list.
     * 
     * @param <T>
     * @param valueList - list of elements
     * @return 
     */
    public <T> List<T> uniqueList(List<T> valueList) {
        int iListSize = valueList.size();
        List<T> cleanedList = new ArrayList<>();

        for (int i = 0; i < iListSize; i++) {
            T o = valueList.get(i);
            if (o == null || cleanedList.indexOf(o) > -1 || "".equals(o.toString()))
                continue;

            // add unique object
            cleanedList.add(o);
        }
        valueList = cleanedList;
        // do not work with empty list....
//        if (valueList.isEmpty())
//            valueList.add("");

        return valueList;
    }

    /**
     * This method merges the role names from a SourceList into a valueList and
     * removes duplicates.
     * 
     * The AddaptText event is fired so a client can adapt a role name.
     * 
     * @param valueList
     * @param sourceList
     * @param documentContext
     * @throws PluginException
     */
    public void mergeRoles(List<String> valueList, List<String> sourceList, ItemCollection documentContext) throws PluginException {
        if ((sourceList != null) && (!sourceList.isEmpty())) {
            for (String o : sourceList) {
                if (valueList.indexOf(o) == -1) {
                    // addapt textList
                    List<String> adaptedRoles = workflowService.adaptTextList(o, documentContext);
                    valueList.addAll(adaptedRoles);// .add(getWorkflowService().adaptText((String)o,
                }
            }
        }
    }
}
