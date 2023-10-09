package org.imixs.workflow.engine.adapters;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.WorkflowMockEnvironment;
import org.imixs.workflow.engine.WorkflowService;
import org.imixs.workflow.engine.plugins.OwnerPlugin;
import org.imixs.workflow.exceptions.AdapterException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.Before;
import org.junit.Test;

import org.junit.Assert;

/**
 * Test the ACL plug-in concerning the ACL settings in a process entity.
 * 
 * If ACL settings are provided by the next process entity than these settings
 * should be set per default and the activity entity can provide additional
 * setting.
 * 
 * 
 * e.g.
 * 
 * ProcessEntity 'namaddwriteaccess' = 'jo'
 * 
 * ActivityEntity 'namaddwriteaccess' = 'anna'
 * 
 * 
 * then $writeAccess should be 'jo','anna'
 * 
 * 
 * 
 * These tests extend the JUnit test in TestAccessPlugin
 * 
 * 
 * @author rsoika
 * 
 */
public class TestAccessAdapterProcessEntity {

	private final static Logger logger = Logger.getLogger(TestAccessAdapterProcessEntity.class.getName());

	
	protected ItemCollection documentContext;
	protected ItemCollection documentActivity;
	protected ItemCollection documentProcess;
	protected WorkflowMockEnvironment workflowMockEnvironment;
	protected AccessAdapter adapter;
	
	@Before
	public void setUp() throws PluginException, ModelException {

		workflowMockEnvironment = new WorkflowMockEnvironment();
		workflowMockEnvironment.setModelPath("/bpmn/acl-test.bpmn");
		workflowMockEnvironment.setup();

		adapter = new AccessAdapter();
		adapter.workflowService=workflowMockEnvironment.getWorkflowService();

		// prepare data
		documentContext = new ItemCollection().model(WorkflowMockEnvironment.DEFAULT_MODEL_VERSION).task(100);
		logger.info("[TestAccessAdapterProcessEntity] setup test data...");
		List<String> list = new ArrayList<>();
		list.add("manfred");
		list.add("anna");
		documentContext.replaceItemValue("namTeam", list);
		documentContext.replaceItemValue("namCreator", "ronny");
		
	}

	/**
	 * Test if the ACL settings will not be changed if no ACL is set be process or
	 * activity
	 * 
	 * @throws ModelException
	 ***/
	@Test
	public void testACLNoUpdate() throws ModelException {
		List<String> list = new ArrayList<>();
		list.add("Kevin");
		list.add("Julian");
		documentContext.replaceItemValue(WorkflowService.WRITEACCESS, list);

		documentActivity = workflowMockEnvironment.getModel().getEvent(100, 10);
		documentContext.setEventID(10);
		try {
			adapter.execute(documentContext, documentActivity);
		} catch (AdapterException e) {
			logger.severe(e.getMessage());
			Assert.fail();
		}

		@SuppressWarnings("unchecked")
		List<String> writeAccess = documentContext.getItemValueList(WorkflowService.WRITEACCESS, String.class);

		Assert.assertEquals(2, writeAccess.size());
		Assert.assertTrue(writeAccess.contains("Kevin"));
		Assert.assertTrue(writeAccess.contains("Julian"));

	}

	/**
	 * Test if the ACL settings from the next processEntity are injected into the
	 * workitem
	 * 
	 * @throws ModelException
	 **/
	@Test
	public void testACLfromProcessEntity() throws ModelException {

		documentActivity = workflowMockEnvironment.getModel().getEvent(300, 10);
		documentContext.setTaskID(300);
		documentContext.setEventID(10);
		
		try {
			adapter.execute(documentContext, documentActivity);
		} catch (AdapterException e) {
			logger.severe(e.getMessage());
			Assert.fail();
		}

		@SuppressWarnings("unchecked")
		List<String> writeAccess = documentContext.getItemValueList(WorkflowService.WRITEACCESS, String.class);

		Assert.assertEquals(2, writeAccess.size());
		Assert.assertTrue(writeAccess.contains("joe"));
		Assert.assertTrue(writeAccess.contains("sam"));

	}

	/**
	 * Test if the ACL settings from the activityEntity are injected into the
	 * workitem
	 * 
	 * @throws ModelException
	 **/
	@Test
	public void testACLfromActivityEntity() throws ModelException {

		documentActivity = workflowMockEnvironment.getModel().getEvent(100, 20);
		documentContext.setEventID(20);
		
		try {
			adapter.execute(documentContext, documentActivity);
		} catch (AdapterException e) {
			logger.severe(e.getMessage());
			Assert.fail();
		}

		@SuppressWarnings("unchecked")
		List<String> writeAccess = documentContext.getItemValueList(WorkflowService.WRITEACCESS, String.class);

		Assert.assertEquals(3, writeAccess.size());
		Assert.assertTrue(writeAccess.contains("joe"));
		Assert.assertTrue(writeAccess.contains("samy"));
		Assert.assertTrue(writeAccess.contains("anna"));

	}

	/**
	 * Test if the ACL settings from the next processEntity are ignored in case the
	 * ActivityEnttiy provides settings. Merge is not supported!
	 * 
	 * @throws ModelException
	 **/
	@SuppressWarnings("unchecked")
	@Test
	public void testACLfromProcessEntityAndActivityEntity() throws ModelException {

		// set some old values
		List<String> list = new ArrayList<>();
		list.add("Kevin");
		list.add("Julian");
		documentContext.replaceItemValue(OwnerPlugin.OWNER, list);
		documentContext.setTaskID(300);

		documentActivity = workflowMockEnvironment.getModel().getEvent(300, 20);
		documentContext.setEventID(20);
		
		try {
			adapter.execute(documentContext, documentActivity);
		} catch (AdapterException e) {
			logger.severe(e.getMessage());
			Assert.fail();
		}

		// $writeAccess= anna , manfred, joe, sam
		List<String> writeAccess = documentContext.getItemValueList(WorkflowService.WRITEACCESS, String.class);
		Assert.assertEquals(3, writeAccess.size());
		Assert.assertTrue(writeAccess.contains("joe"));
		// Assert.assertTrue(writeAccess.contains("sam"));
		Assert.assertTrue(writeAccess.contains("manfred"));
		Assert.assertTrue(writeAccess.contains("anna"));

		// $readAccess= anna , manfred, joe, sam
		writeAccess = documentContext.getItemValueList(WorkflowService.READACCESS, String.class);
		Assert.assertEquals(2, writeAccess.size());
		Assert.assertTrue(writeAccess.contains("tom"));
		Assert.assertTrue(writeAccess.contains("manfred"));

	}

}
