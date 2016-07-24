package org.imixs.workflow.bpmn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.Model;
import org.imixs.workflow.WorkflowKernel;
import org.imixs.workflow.exceptions.ModelException;

/**
 * The BPMNModel implements the Imixs Model Interface. The class is used by the
 * class BPMNModelHandler.
 * 
 * @see BPMNModelHandler
 * @author rsoika
 * 
 */
public class BPMNModel implements Model {

	private Map<Integer, ItemCollection> taskList = null;
	private Map<Integer, List<ItemCollection>> eventList = null;
	private List<String> workflowGroups = null;
	private ItemCollection definition = null;
	private byte[] rawData = null;
	private static Logger logger = Logger.getLogger(BPMNModel.class.getName());

	public BPMNModel() {
		taskList = new HashMap<Integer, ItemCollection>();
		eventList = new HashMap<Integer, List<ItemCollection>>();
		workflowGroups = new ArrayList<String>();
	}

	/**
	 * Returns the raw data of the BPMN file
	 * 
	 * @return
	 */
	public byte[] getRawData() {
		return rawData;
	}

	/**
	 * Set the raw data of the bpmn source file
	 * 
	 * @param rawData
	 */
	public void setRawData(byte[] data) {
		this.rawData = data;
	}

	

	@Override
	public String getVersion() {
		if (definition != null) {
			return definition.getModelVersion();
		}
		return null;
	}

	/**
	 * Returns the model profile entity
	 * 
	 * @return
	 */
	public ItemCollection getDefinition() {
		return definition;
	}

	@Override
	public ItemCollection getTask(int processid) throws ModelException {
		ItemCollection process = taskList.get(processid);
		if (process != null)
			return process;
		else
			throw new ModelException(BPMNModel.class.getSimpleName(), ModelException.UNDEFINED_MODEL_ENTRY);
	}

	@Override
	public ItemCollection getEvent(int processid, int activityid) throws ModelException {
		List<ItemCollection> activities = findAllEventsByTask(processid);
		for (ItemCollection aactivity : activities) {
			if (activityid == aactivity.getItemValueInteger("numactivityid")) {
				return aactivity;
			}
		}
		// not found!
		throw new ModelException(BPMNModel.class.getSimpleName(), ModelException.UNDEFINED_MODEL_ENTRY);
	}

	public List<String> getGroups() {
		return workflowGroups;
	}

	@Override
	public List<ItemCollection> findAllTasks() {
		return new ArrayList<ItemCollection>(taskList.values());
	}

	@Override
	public List<ItemCollection> findAllEventsByTask(int processid) {
		List<ItemCollection> result = eventList.get(processid);
		if (result == null)
			result = new ArrayList<ItemCollection>();
		return result;
	}


	/***
	 * Returns a list of tasks filtert by txtworkflowgroup.
	 */
	@Override
	public List<ItemCollection> findTasksByGroup(String group) {
		List<ItemCollection> result = new ArrayList<ItemCollection>();
		if (group != null && !group.isEmpty()) {
			List<ItemCollection> allTasks = findAllTasks();
			for (ItemCollection task : allTasks) {
				if (group.equals(task.getItemValueString("txtworkflowgroup"))) {
					result.add(task);
				}
			}
		}
		return result;
	}

	protected void setDefinition(ItemCollection profile) {
		this.definition = profile;
	}

	/**
	 * Adds a ProcessEntiy into the process list
	 * 
	 * @param entity
	 * @throws ModelException
	 */
	protected void addProcessEntity(ItemCollection entity) throws ModelException {
		if (entity == null)
			return;

		if (!"ProcessEntity".equals(entity.getItemValueString("type"))) {
			logger.warning("Invalid Process Entity - wrong type '" + entity.getItemValueString("type") + "'");
			throw new ModelException(ModelException.INVALID_MODEL_ENTRY,
					"Invalid Process Entity - wrong type '" + entity.getItemValueString("type") + "'");
		}

		// add group?
		String group = entity.getItemValueString("txtworkflowgroup");
		if (!workflowGroups.contains(group)) {
			workflowGroups.add(group);
		}
		taskList.put(entity.getItemValueInteger("numprocessid"), entity);
	}

	/**
	 * Adds a ProcessEntiy into the process list
	 * 
	 * @param entity
	 */
	protected void addActivityEntity(ItemCollection aentity) throws ModelException {
		if (aentity == null)
			return;

		// we need to clone the entity because of shared events....
		ItemCollection clonedEntity = new ItemCollection(aentity);

		if (!"ActivityEntity".equals(clonedEntity.getItemValueString("type"))) {
			logger.warning("Invalid Activity Entity - wrong type '" + clonedEntity.getItemValueString("type") + "'");
		}

		int pID = clonedEntity.getItemValueInteger("numprocessid");
		if (pID <= 0) {
			logger.warning("Invalid Activiyt Entity - no numprocessid defined!");
			throw new ModelException(ModelException.INVALID_MODEL_ENTRY,
					"Invalid Activiyt Entity - no numprocessid defined!");
		}

		// test version
		String activitymodelversion = clonedEntity.getItemValueString(WorkflowKernel.MODELVERSION);
		ItemCollection process = this.getTask(pID);
		if (process == null) {
			logger.warning("Invalid Activiyt Entity - no numprocessid defined in model version '" + activitymodelversion
					+ "' ");
			throw new ModelException(ModelException.INVALID_MODEL_ENTRY,
					"Invalid Activiyt Entity - no numprocessid defined!");
		}

		List<ItemCollection> activities = findAllEventsByTask(pID);

		activities.add(clonedEntity);
		eventList.put(pID, activities);
	}

}
