package org.imixs.workflow.engine;

import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

/**
 * Test class for WorkflowService
 * 
 * This test verifies specific method implementations of the workflowService by
 * mocking the WorkflowService with the @spy annotation.
 * 
 * 
 * @author rsoika
 */
public class TestWorkflowService {
    
        private final static Logger logger = Logger.getLogger(TestModelService.class.getName());
        
	protected WorkflowMockEnvironment workflowMockEnvironment;

	@Before
	public void setup() throws PluginException, ModelException {

		workflowMockEnvironment = new WorkflowMockEnvironment();
		workflowMockEnvironment.setup();

		workflowMockEnvironment.loadModel("/bpmn/TestWorkflowService.bpmn");

	}

	/**
	 * This test simulates a workflowService process call by mocking the entity and
	 * model service.
	 * 
	 * This is just a simple simulation...
	 * 
	 * @throws ProcessingErrorException
	 * @throws AccessDeniedException
         * @throws org.imixs.workflow.exceptions.PluginException
	 * @throws ModelException
	 */
	@Test
	public void testProcessSimple()
			throws AccessDeniedException, ProcessingErrorException, PluginException, ModelException {
		// load test workitem
		ItemCollection workitem = workflowMockEnvironment.database.get("W0000-00001");
		workitem.replaceItemValue(WorkflowKernel.MODELVERSION, WorkflowMockEnvironment.DEFAULT_MODEL_VERSION);
		workitem.setTaskID(100);

		workitem = workflowMockEnvironment.workflowService.processWorkItem(workitem);

		Assert.assertEquals("1.0.0", workitem.getItemValueString("$ModelVersion"));

	}

	/**
	 * test if the method getEvents returns correct lists of public events.
	 */
	@Test
	public void testGetEventsSimple() {

		// get workitem
		ItemCollection workitem = workflowMockEnvironment.database.get("W0000-00001");
		workitem.setTaskID(200);

		try {
			List<ItemCollection> eventList = workflowMockEnvironment.workflowService.getEvents(workitem);
                        // only one event is public!
                        Assert.assertEquals(1, eventList.size());
		} catch (ModelException e) {
			logger.severe(e.getMessage());
			Assert.fail();
		}
	}

	/**
	 * test if the method getEvents returns correct lists of public and restricted
	 * events. User "manfred" is listed in current workitem namTeam.
	 */
	@Test
	public void testGetEventsComplex() {

		// get workitem
		ItemCollection workitem = workflowMockEnvironment.database.get("W0000-00001");
		workitem.setTaskID(100);

		List<String> members = new ArrayList<>();
		members.add("jo");
		members.add("");
		members.add("manfred");
		members.add("alex");
		workitem.replaceItemValue("nammteam", "tom");
		workitem.replaceItemValue("nammanager", members);
		workitem.replaceItemValue("namassist", "");

		try {
                    List<ItemCollection> eventList = workflowMockEnvironment.workflowService.getEvents(workitem);
                    Assert.assertEquals(3, eventList.size());
		} catch (ModelException e) {
			logger.severe(e.getMessage());
			Assert.fail();
		}
	}

	/**
	 * test if the method getEvents returns correct lists of workflow events, with a
	 * more complex setup. User 'manfred' is not listed in namManger!
	 */
	@Test
	public void testGetEventsComplexRestricted() {

		// get workitem
		ItemCollection workitem = workflowMockEnvironment.database.get("W0000-00001");
		workitem.setTaskID(100);

		List<String> members = new ArrayList<>();
		members.add("jo");
		members.add("alex");
		workitem.replaceItemValue("nammteam", "tom");
		workitem.replaceItemValue("nammanager", members);
		workitem.replaceItemValue("namassist", "");

		try {
			List<ItemCollection> eventList = workflowMockEnvironment.workflowService.getEvents(workitem);
			Assert.assertEquals(2, eventList.size());
                } catch (ModelException e) {
			logger.severe(e.getMessage());
			Assert.fail();
		}
	}

	/**
	 * This test verifies if the method getEvents returns only events where the
	 * current user has read access! In this case, the user "manfred" is in the role
	 * 'org.imixs.ACCESSLEVEL.MANAGERACCESS' and granted to the event 300.20
	 * 
	 * So we expect both events!
	 */
	@Test
	public void testGetEventsReadRestrictedForManagerAccess() {

		when(workflowMockEnvironment.workflowService.getUserNameList()).thenAnswer((InvocationOnMock invocation) -> {
                    List<String> result = new ArrayList<>();
                    result.add("manfred");
                    result.add("org.imixs.ACCESSLEVEL.MANAGERACCESS");
                    return result;
                });

		// get workitem
		ItemCollection workitem = workflowMockEnvironment.database.get("W0000-00001");
		workitem.setTaskID(300);

		List<String> members = new ArrayList<>();
		members.add("jo");
		members.add("alex");
		workitem.replaceItemValue("nammteam", "tom");
		workitem.replaceItemValue("nammanager", members);
		workitem.replaceItemValue("namassist", "");

		try {
			List<ItemCollection> eventList = workflowMockEnvironment.workflowService.getEvents(workitem);
                        Assert.assertEquals(2, eventList.size());
		} catch (ModelException e) {
			logger.severe(e.getMessage());
			Assert.fail();
		}
	}
}
