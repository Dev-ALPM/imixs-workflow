package org.imixs.workflow.engine.solr;

import java.util.Calendar;
import java.util.List;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.junit.Assert;

/**
 * Test the WorkflowService method parseJSONQueyResult from SolrSerachService
 * 
 * @author rsoika
 * 
 */
public class TestParseSolrJSONResult {
		
	
	SolrSearchService solrSearchService=null;

	@Before
	public void setUp() throws PluginException, ModelException {
		 solrSearchService=new SolrSearchService();
	}
	

	/**
	 * Test 
	 * 
	 */
	@Test
	@Ignore
	public void testParseResult() {
		String testString = """
                                    {
                                      "responseHeader":{
                                        "status":0,
                                        "QTime":4,
                                        "params":{
                                          "q":"*:*",
                                          "_":"1567286252995"}},
                                      "response":{"numFound":2,"start":0,"docs":[
                                          {
                                            "type":["model"],
                                            "id":"3a182d18-33d9-4951-8970-d9eaf9d337ff",
                                            "_modified":[20190831211617],
                                            "_created":[20190831211617],
                                            "_version_":1643418672068296704},
                                          {
                                            "type":["adminp"],
                                            "id":"60825929-4d7d-4346-9333-afd7dbfca457",
                                            "_modified":[20190831211618],
                                            "_created":[20190831211618],
                                            "_version_":1643418672172105728}]
                                      }}""";
		
		
		
		List<ItemCollection> result = solrSearchService.parseQueryResult(testString);
		Assert.assertEquals(2,result.size());
		
		ItemCollection document = result.get(0);
		Assert.assertEquals("model", document.getItemValueString("type"));
		Assert.assertEquals("3a182d18-33d9-4951-8970-d9eaf9d337ff", document.getItemValueString("id"));
		
		Calendar cal=Calendar.getInstance();
		cal.setTime(document.getItemValueDate("_modified"));
		Assert.assertEquals(7,cal.get(Calendar.MONTH));
		Assert.assertEquals(31,cal.get(Calendar.DAY_OF_MONTH));
		
		document=result.get(1);
		Assert.assertEquals("adminp", document.getItemValueString("type"));
		
		
	}

}
