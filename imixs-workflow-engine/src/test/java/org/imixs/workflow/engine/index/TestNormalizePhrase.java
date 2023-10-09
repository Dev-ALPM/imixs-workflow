package org.imixs.workflow.engine.index;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.Before;
import org.junit.Test;

import org.junit.Assert;

/**
 * Test normalization of a search prase
 * 
 * @author rsoika
 * 
 */
public class TestNormalizePhrase {

	SchemaService schemaService = null;
	private static final Logger logger = Logger.getLogger(TestNormalizePhrase.class.getName());

	@Before
	public void setUp() throws PluginException, ModelException {

		schemaService = new SchemaService();

	}

	/**
	 * Test "Lukas Podolski"
	 * @throws org.imixs.workflow.exceptions.PluginException
	 */
	@Test
	public void test1() throws PluginException {
		String searchTerm = "Lukas Podolski";
		String result = schemaService.normalizeSearchTerm(searchTerm);
			logger.log(Level.INFO, "{0}  -->  {1}", new Object[]{searchTerm, result});
	

		Assert.assertEquals("lukas podolski", result);

	}

	
	
	
	/**
	 * Test "Europe/Berlin"
	 * @throws org.imixs.workflow.exceptions.PluginException
	 */
	@Test
	public void test2() throws PluginException {
		String searchTerm = "Europe/Berlin";
		String result = schemaService.normalizeSearchTerm(searchTerm);
			logger.log(Level.INFO, "{0}  -->  {1}", new Object[]{searchTerm, result});
		

		Assert.assertEquals("europe berlin", result);

	}

	
	/**
	 * Test "rs/82550/201618"
	 * @throws org.imixs.workflow.exceptions.PluginException
	 */
	@Test
	public void test3() throws PluginException {
		String searchTerm = "rs/82550/201618";
		String result = schemaService.normalizeSearchTerm(searchTerm);
			logger.log(Level.INFO, "{0}  -->  {1}", new Object[]{searchTerm, result});
		

		Assert.assertEquals("rs\\/82550\\/201618", result);

	}
	
	
	/**
	 * Test "rs-82550/201618"
	 * @throws org.imixs.workflow.exceptions.PluginException
	 */
	@Test
	public void test4() throws PluginException {
		String searchTerm = "rs-82550/201618";
		String result = schemaService.normalizeSearchTerm(searchTerm);
			logger.log(Level.INFO, "{0}  -->  {1}", new Object[]{searchTerm, result});
		
		Assert.assertEquals("rs\\-82550\\/201618", result);

	}

	
	
	
	
}
