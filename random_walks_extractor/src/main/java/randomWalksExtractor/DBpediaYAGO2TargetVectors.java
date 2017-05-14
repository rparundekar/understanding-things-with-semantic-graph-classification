package randomWalksExtractor;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVWriter;

/**
 * Target vector generation from the DBpedia-Yago types
 * @author rparundekar
 */
public class DBpediaYAGO2TargetVectors implements StreamRDF{
	// SLF4J Logger bound to Log4J 
	private static final Logger logger=LoggerFactory.getLogger(DBpediaTypes2TargetVectors.class);

	//Data for the target vectors
	private int targetVectorCount = 0;
	private final Map<String, Integer> targetVectorPosition;
	private Map<String, Set<String>> types;
	private int notFoundCount=0;
	private final Map<String, Integer> instanceCount;
	private int skipCount=0;
	
	/**
	 * Constructor
	 */
	public DBpediaYAGO2TargetVectors(){
		targetVectorPosition=new HashMap<>();
		instanceCount=new HashMap<>();
	}

	/**
	 * Load the RDF data from the DBPedia turtle (.ttl) file, 
	 * by taking an inner join on the Semantic Graph file.
	 * @param semanticGraphFile The file for the Semantic Graph e.g. infobox_properties.en.ttl 
	 * @param turtleFile The DBPedia turtle file e.g. instance_types.en.ttl 
	 */
	public void load(File semanticGraphFile, File turtleFile){
		logger.info("Loading instance sets from the semantic graph file for inner join");
		InMemoryInstanceMapLoader inMemoryInstanceSetLoader=new InMemoryInstanceMapLoader();
		inMemoryInstanceSetLoader.load(semanticGraphFile);
		types=inMemoryInstanceSetLoader.getInstances();
		logger.info("...Done");

		try{
			// Step 1: Find the different types by iterating once through the instances
			// Since the turtle file might contain errors (e.g. in the properties 
			// there is a value 'Infinity', with datatype xsd:double), we need to read each line
			// and then call the RDFDataMgr on that.
			// It sucks, since it's slow. But hey 'Infinity' cant be parsed as a double. 
			LineNumberReader lnr=new LineNumberReader(new FileReader(turtleFile));
			String line=null;
			long start=System.currentTimeMillis();
			while((line=lnr.readLine())!=null){
				// Print progress
				if(lnr.getLineNumber()%1000==0){
					logger.info("{} lines parsed in {} ms. {} lines with no instances. {} lines skipped with wikicat", lnr.getLineNumber(), (System.currentTimeMillis()-start), notFoundCount, skipCount);
					start=System.currentTimeMillis();
				}
				// Parse the line read using the stream API. Make sure we catch parsing errors. 
				try{
					RDFDataMgr.parse(this, new StringReader(line), Lang.TURTLE);
				}catch(DatatypeFormatException|RiotException de){
					logger.error("Illegal data format in line : " +line);
				}
			}
			// Close IO
			lnr.close();
		}catch(IOException e){
			// Something went wrong with the files.
			logger.error("Cannot load the data from the file due to file issue:" + e.getMessage());
		}
		
		//We add additional step to only consider types with a support of atleast 200 instances since
		// otherwise there are too many
		try{
			PrintWriter pw = new PrintWriter(new File(turtleFile.getParentFile(), "yagoCounts.csv"));
			for(String type:instanceCount.keySet()){
				int count =  instanceCount.get(type);
				pw.println(type +","+ count);
				pw.flush();
				if(count>=200)
					makeTarget(type);
			}
			pw.close();
		}catch(IOException e){
			e.printStackTrace();
		}
		logger.info("{} possible types present.", targetVectorCount);

		try{	
			// Step 2: Create the csv with the target vectors types;
			File outputCsv = new File(turtleFile.getParentFile(), "yagoOneHot.csv");
			CSVWriter csvWriter = new CSVWriter(new FileWriter(outputCsv), ',', CSVWriter.NO_QUOTE_CHARACTER);
			//Write the header
			String[] header = new String[targetVectorCount+1];
			header[0]="id";
			for(String id:targetVectorPosition.keySet()){
				header[targetVectorPosition.get(id)+1]=DBpediaHelper.stripClean(id);
			}
			csvWriter.writeNext(header);
			logger.info("Writing to targetVectorFile... (Sit back & go grab a coffee. This may take a while.)");
			for(String subject:types.keySet()){
				Set<String> typeOf=types.get(subject);
				if(typeOf.isEmpty())
					continue;
				int count=0;
				for(String id:targetVectorPosition.keySet()){
					if(typeOf.contains(id))
					{
						count++;
					}
				}
				String[] row = new String[count+1];
				row[0]=subject;
				int i=1;
				for(String id:targetVectorPosition.keySet()){
					if(typeOf.contains(id))
					{
						row[i]=""+(targetVectorPosition.get(id)+1);
						i++;
					}
				}
				csvWriter.writeNext(row);
				csvWriter.flush();
			}
			csvWriter.close();
		}catch(IOException e){
			// Something went wrong with the files.
			logger.error("Cannot write the data to the file due to file issue:" + e.getMessage());
		}
	}

	/**
	 * Get stuff running.
	 */
	public static void main(String[] args){
		DBpediaYAGO2TargetVectors loadFile = new DBpediaYAGO2TargetVectors();
		loadFile.load(new File("/Users/rparundekar/dataspace/dbpedia2016/infobox_properties_en.ttl"), new File("/Users/rparundekar/dataspace/dbpedia2016/yago_types.ttl"));
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
		Node object = triple.getMatchObject();
		if(object.isURI()){
			// Get and clean the object URI (There are no blank nodes in DBpedia)
			String o=object.getURI();
			if(o.toLowerCase().contains("wikicat")){
				skipCount++;
				return;
			}
		}

		// Get and clean the subject URI (There are no blank nodes in DBpedia)
		String subject = triple.getMatchSubject().getURI();
		subject=DBpediaHelper.stripClean(subject);
		if(types.containsKey(subject)){
			// Get the object. It can be a URI or a literal (There are no blank nodes in the DBpedia)
			if(object.isURI()){
				// Get and clean the object URI (There are no blank nodes in DBpedia)
				String o=object.getURI();
				o=DBpediaHelper.stripClean(o);
				Integer c = instanceCount.get(o);
				if(c==null)
					instanceCount.put(o, 1);
				else
					instanceCount.put(o, c+1);

				putTypes(subject,o);
			}
			else{
				logger.error("Value of type is not a URI");
			}
		}else{
			notFoundCount++;
		}


	}

	/**
	 * Function to keep track of the types for an instance
	 * @param individual The individual for which we want to track the type
	 * @param type The type
	 */
	private void putTypes(String individual, String type) {
		Set<String> typeOf = types.get(individual);
		if(typeOf==null)
		{
			typeOf=new TreeSet<>();
			types.put(individual, typeOf);
		}
		typeOf.add(type);
	}


	/**
	 * Make the target and increment the count
	 * @param type The type
	 */
	private void makeTarget(String type) {
		if(!targetVectorPosition.containsKey(type))
		{
			targetVectorPosition.put(type, targetVectorCount++);
		}
	}

}
