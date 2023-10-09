package org.imixs.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.imixs.workflow.bpmn.BPMNModel;
import org.imixs.workflow.bpmn.BPMNParser;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import org.junit.Assert;

/**
 * Test class for testing the BPMN Rule Engine. The method loads a test model
 * 
 * 
 * @author rsoika
 */
public class TestRuleEngineBPMN {

	private final static Logger logger = Logger.getLogger(TestRuleEngineBPMN.class.getName());

	final static String MODEL_PATH = "/bpmn/workflowkernel_eval.bpmn";
	final static String MODEL_VERSION = "1.0.0";

	ItemCollection workitem = null;
	
	BPMNRuleEngine bpmnRuleEngine=null;

	// setup the workflow envirnment
	@Before
	public void setup() throws PluginException, ModelException {

		// load model
		long lLoadTime = System.currentTimeMillis();
		InputStream inputStream = getClass().getResourceAsStream(MODEL_PATH);

		try {
			logger.info("loading model: " + MODEL_PATH + "....");
			BPMNModel model = BPMNParser.parseModel(inputStream, "UTF-8");

			bpmnRuleEngine=new BPMNRuleEngine(model);

			logger.log(Level.FINE, "...loadModel processing time={0}ms", System.currentTimeMillis() - lLoadTime);
		} catch (ModelException | ParseException | ParserConfigurationException | SAXException | IOException e) {
			Assert.fail(e.getMessage());
		}

	
	}

	@Test
	public void testRuleMatch() {
		long l = System.currentTimeMillis();
		workitem = new ItemCollection();
		workitem.model(MODEL_VERSION).task(100).event(10);
		workitem.setItemValue("a", 1);
		workitem.setItemValue("b", "DE");

		try {
			Assert.assertEquals(200, bpmnRuleEngine.eval(workitem));
			logger.log(Level.INFO, "evaluate BPMN-Rule in {0}ms", System.currentTimeMillis() - l);

			// task and event must still be set to 100.10
			Assert.assertEquals(10, workitem.getEventID());
			Assert.assertEquals(100, workitem.getTaskID());
		} catch (ModelException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testRuleNoMatch() {
		long l = System.currentTimeMillis();
		workitem = new ItemCollection();
		workitem.model(MODEL_VERSION).task(100).event(10);
		workitem.setItemValue("a", 1);
		workitem.setItemValue("b", "I");

		try {
			Assert.assertEquals(900, bpmnRuleEngine.eval(workitem));
			logger.log(Level.INFO, "evaluate BPMN-Rule in {0}ms", System.currentTimeMillis() - l);

			// task and event must still be set to 100.10
			Assert.assertEquals(10, workitem.getEventID());
			Assert.assertEquals(100, workitem.getTaskID());
		} catch ( ModelException e) {
			Assert.fail(e.getMessage());
		}
	}

	
}
