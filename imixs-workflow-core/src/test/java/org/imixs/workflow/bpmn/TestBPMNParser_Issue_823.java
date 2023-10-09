package org.imixs.workflow.bpmn;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.ModelException;
import org.junit.Test;
import org.xml.sax.SAXException;

import org.junit.Assert;

/**
 * Test class test the Imixs BPMNParser.
 * 
 * Special case: Conditional-Events
 * 
 * @see issue #299
 * @author rsoika
 */
public class TestBPMNParser_Issue_823 {

	
	/**
	 * Test resolving a complex model situation with a conditional event.....
	 * 
	 * Check event 2200.100 pointing to 2100
	 * 
	 * 
	 * See Issue #823 for details!
	 * 
	 * 
	 * @throws ParseException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws ModelException
	 */
	@Test
	public void testComplex0()
			throws ParseException, ParserConfigurationException, SAXException, IOException, ModelException {

		InputStream inputStream = getClass().getResourceAsStream("/bpmn/conditional_complex_event0.bpmn");

		BPMNModel model = null;
		try {
			model = BPMNParser.parseModel(inputStream, "UTF-8");

			List<ItemCollection> events2000 = model.findAllEventsByTask(2000);
			Assert.assertEquals(3,events2000.size());

			List<ItemCollection> events2200 = model.findAllEventsByTask(2200);
			Assert.assertEquals(3,events2200.size());

			// NOTE:
			// The following check is not resolvelable because in the demo model 
			// task 2200 contains a duplicate eventID which is not detected by the Parser!!


			// Check event 2200.100 pointing to 2100
			ItemCollection event =model.getEvent(2200,100);
			Assert.assertEquals(2100, event.getItemValueInteger("numnextprocessid"));

			event =model.getEvent(2200,100);
			Assert.assertEquals(2100, event.getItemValueInteger("numnextprocessid"));


		} catch (UnsupportedEncodingException | ModelException e) {
			Assert.fail();
		}
		Assert.assertNotNull(model);

	
	}




}