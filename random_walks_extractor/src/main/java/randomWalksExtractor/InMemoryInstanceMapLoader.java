package randomWalksExtractor;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a loader for DBpedia data as a Graph.
 * @author rparundekar
 */
public class InMemoryInstanceMapLoader implements StreamRDF{
	// SLF4J Logger bound to Log4J 
	private static final Logger logger=LoggerFactory.getLogger(InMemoryInstanceMapLoader.class);
	private Map<String,Set<String>> instances;
	public InMemoryInstanceMapLoader(){
		instances=new HashMap<>();
	}

	/**
	 * Load the RDF data from the DBPedia turtle (.ttl) file 
	 * @param turtleFile The DBPedia turtle file e.g. instance_types.en 
	 */
	public void load(File turtleFile){
		try{
			// Since the turtle file might contain errors (e.g. in the properties 
			// there is a value 'Infinity', with datatype xsd:double), we need to read each line
			// and then call the RDFDataMgr on that.
			// It sucks, since it's slow. But hey 'Infinity' cant be parsed as a double. 
			LineNumberReader lnr=new LineNumberReader(new FileReader(turtleFile));
			String line=null;
			while((line=lnr.readLine())!=null){
				// Print progress
				if(lnr.getLineNumber()%10000==0){
					logger.info("{} lines parsed.", lnr.getLineNumber());
				}
				// Parse the line read using the stream API. Make sure we catch parsing errors. 
				try{
					RDFDataMgr.parse(this, new StringReader(line), Lang.TURTLE);
				}catch(DatatypeFormatException de){
					logger.error("Illegal data format in line : " +line);
				}
			}
			// Close IO
			lnr.close();
		}catch(IOException e){
			// Something went wrong with the files.
			logger.error("Cannot load the data from the file due to file issue:" + e.getMessage());
		}
	}
	
	/**
	 * Close the driver to avoid memory leaks.
	 */
	public void close(){	
	}
	
	@Override
	public void base(String base) {
		// Do Nothing
		// That's the base. DBpedia doesn't use this it seems.
	}

	@Override
	public void finish() {
		// Do Nothing
	}

	@Override
	public void prefix(String prefix, String iri) {
		// Do Nothing
	}

	@Override
	public void quad(Quad arg0) {
		// Do Nothing
	}

	@Override
	public void start() {
		// Do Nothing
	}

	@Override
	public void triple(Triple triple) {
		// Handle the triple
		
		// Get and clean the subject URI (There are no blank nodes in DBpedia)
		String subject = triple.getMatchSubject().getURI();
		subject=DBpediaHelper.stripClean(subject);
		if(!instances.containsKey(subject))
			instances.put(subject, new HashSet<String>());
		// Get and clean the predicate URI (There are no blank nodes in DBpedia)
		String predicate = triple.getMatchPredicate().getURI();
		predicate = DBpediaHelper.stripClean(predicate);
		
		// Get the object. It can be a URI or a literal (There are no blank nodes in the DBpedia)
		Node object = triple.getMatchObject();
		Object o=null;
		if(object.isURI()){
			// Get and clean the object URI (There are no blank nodes in DBpedia)
			o=object.getURI();
			String obj=DBpediaHelper.stripClean((String)o);
			if(!instances.containsKey(obj))
				instances.put(obj, new HashSet<>());
		}
	}
	
	/**
	 * Get the distinct instances
	 * @return The instance map
	 */
	public Map<String,Set<String>> getInstances(){
		return instances;
	}
	
}
