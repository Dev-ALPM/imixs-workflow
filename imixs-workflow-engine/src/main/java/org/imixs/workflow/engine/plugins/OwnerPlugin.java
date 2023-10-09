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

package org.imixs.workflow.engine.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;

/**
 * This plugin implements a ownership control by evaluating the configuration of
 * an BPMN Event element. The Plugin updates the WorkItem attribute '$owner'
 * depending on the provided information.
 * 
 * These attributes defined in Activity Entity are evaluated by the plugin:
 * <ul>
 * <li>keyupdateacl (Boolean): if false no changes are necessary</li>
 * <li>keyOwnershipFields (List): Properties of the current WorkItem</li>
 * <li>namOwnershipNames (List): Names & Groups to be added /replaced</li>
 * </ul>
 *  
 * NOTE: Models generated with the first version of the Imixs-Workflow Modeler
 * provide a different set of attributes. Therefore the plugin implements a
 * fallback method to support deprecated models. The fallback method evaluate
 * the following list of attributes defined in Activity Entity:
 *  
 * <ul>
 * <li>keyOwnershipMode (List): '1'=modify access '0'=renew access</li>
 * <li>keyOwnershipFields (List): Properties of the current WorkItem</li>
 * <li>namOwnershipNames (List): Names & Groups to be added /replaced</li>
 * </ul>
 * 
 * 
 * 
 * #Issue 133: Extend access plug-in to resolve owner settings in process entity
 * 
 * The AccessPlugin also evaluates the ACL settings in the next ProcessEntity
 * which is supported by newer versions of the imixs-bpmn modeler.
 * 
 * 
 * 
 * @author Ralph Soika
 * @version 1.0
 * @see org.imixs.workflow.WorkflowManager
 */

public class OwnerPlugin extends AbstractPlugin {

    public final static String OWNER = "$owner";

    private ItemCollection documentContext;
    private ItemCollection documentActivity;
    private ItemCollection documentNextProcessEntity;

    private static final Logger logger = Logger.getLogger(OwnerPlugin.class.getName());

    /**
     * changes the '$owner' item depending to the activityentity or processEntity
     * 
     * @param adocumentContext
     * @param adocumentActivity
     * @throws org.imixs.workflow.exceptions.PluginException
     */
    @Override
    public ItemCollection run(ItemCollection adocumentContext, ItemCollection adocumentActivity)
            throws PluginException {

        documentContext = adocumentContext;
        documentActivity = adocumentActivity;

        // get next process entity
        try {
            // documentNextProcessEntity =
            // this.getWorkflowService().evalNextTask(adocumentContext,
            // adocumentActivity);
            documentNextProcessEntity = this.getWorkflowService().evalNextTask(adocumentContext);
        } catch (ModelException e) {
            throw new PluginException(OwnerPlugin.class.getSimpleName(), e.getErrorCode(), e.getMessage());
        }

        // in case the activity is connected to a followup activity the
        // nextProcess can be null!

        // test update mode of activity and process entity - if true clear the
        // existing values.
        if (documentActivity.getItemValueBoolean("keyupdateacl") == false && (documentNextProcessEntity == null
                || documentNextProcessEntity.getItemValueBoolean("keyupdateacl") == false)) {
            // no update!
            return documentContext;
        } else {
            // activity settings will not be merged with process entity
            // settings!
            if (documentActivity.getItemValueBoolean("keyupdateacl") == true) {
                updateOwnerByItemCollection(documentActivity);
            } else {
                updateOwnerByItemCollection(documentNextProcessEntity);
            }
        }

        return documentContext;
    }

    /**
     * This method updates the owner of a workitem depending on a given model entity
     * The model entity should provide the following attributes:
     * 
     * keyupdateacl, namOwnershipNames,keyOwnershipFields
     * 
     * 
     * The method did not clear the exiting values of namowner
     * 
     * @throws PluginException
     */
    private void updateOwnerByItemCollection(ItemCollection modelEntity) throws PluginException {

        if (modelEntity == null || modelEntity.getItemValueBoolean("keyupdateacl") == false) {
            // no update necessary
            return;
        }

        List<String> newOwnerList = new ArrayList<>();

        // add names
        mergeRoles(newOwnerList, modelEntity.getItemValueList("namOwnershipNames", String.class), documentContext);
        // add Mapped Fields
        mergeFieldList(documentContext, newOwnerList, modelEntity.getItemValueList("keyOwnershipFields", String.class));
        // clean List
        newOwnerList = uniqueList(newOwnerList);

        // update ownerlist....
        documentContext.replaceItemValue(OWNER, newOwnerList);
        if ((logger.isLoggable(Level.FINE)) && (!newOwnerList.isEmpty())) {
            logger.finest("......Owners:");
            for (int j = 0; j < newOwnerList.size(); j++)
                logger.log(Level.FINEST, "               ''{0}''", newOwnerList.get(j));
        }

        // we also need to support the deprecated iten name "namOwner" which was
        // replaced since version
        // 5.0.2 by "owner"
        documentContext.replaceItemValue("namOwner", newOwnerList);

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
                        List<String> adaptedRoles = this.getWorkflowService().adaptTextList(o,
                                documentContext);
                        valueList.addAll(adaptedRoles);// .add(getWorkflowService().adaptText((String)o,
                                                       // documentContext));
                }
            }
        }
    }
}
