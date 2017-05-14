package randomWalksExtractor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVWriter;

/**
 * Target vector generation from the DBpedia types file using the DBpedia Ontology.
 * We use the Apache Jena Stream RDF API for parsing the Triple file. 
 * NOTE: Currently only tested on Oct 2016 files for instance_types_en.ttl
 * @author rparundekar
 */
public class DBpediaTypes2TargetVectors implements StreamRDF{
	// SLF4J Logger bound to Log4J 
	private static final Logger logger=LoggerFactory.getLogger(DBpediaTypes2TargetVectors.class);

	//The ontology
	private final OntModel ontModel;
	
	//Data for the target vectors
	private int targetVectorCount = 0;
	private final Map<String, Integer> targetVectorPosition;
	private Map<String, Set<String>> types;
	private int notFoundCount=0;
	
	/**
	 * Constructor that loads the Owl Ontology
	 * @param ontologyFile The OWL file
	 * @throws FileNotFoundException If file is not found
	 */
	public DBpediaTypes2TargetVectors(File ontologyFile){
		logger.info("Loading OWL file ...");
		OntModel base = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM );
		try {
			base.read( new FileInputStream(ontologyFile), "RDF/XML" );
		} catch (FileNotFoundException e) {
			logger.error("Could not find ontology file");
		}

		// Create the reasoning model using the base
		ontModel = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF, base );
		targetVectorPosition=new HashMap<>();
		
		logger.info("...Done");
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
					logger.info("{} lines parsed in {} ms. {} types present. {} lines with no instances", lnr.getLineNumber(), (System.currentTimeMillis()-start), targetVectorCount, notFoundCount);
					start=System.currentTimeMillis();
				}
				// Parse the line read using the stream API. Make sure we catch parsing errors. 
				try{
					RDFDataMgr.parse(this, new StringReader(line), Lang.TURTLE);
				}catch(DatatypeFormatException|RiotException de){
					logger.error("Illegal data format in line : " +line);
				}
			}
			lnr.close();
		}catch(IOException e){
			// Something went wrong with the files.
			logger.error("Cannot load the data from the file due to file issue:" + e.getMessage());
		}
		try{	
			// Step 2: Create the csv with the target vector types;
			File outputCsv = new File(turtleFile.getParentFile(), "targetVectorFile.csv");
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
				String[] row = new String[typeOf.size()+1];
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
		DBpediaTypes2TargetVectors loadFile = new DBpediaTypes2TargetVectors(new File("/Users/rparundekar/dataspace/dbpedia2016/dbpedia_2016-04.owl"));
		loadFile.load(new File("/Users/rparundekar/dataspace/dbpedia2016/infobox_properties_en.ttl"), new File("/Users/rparundekar/dataspace/dbpedia2016/instance_types_en.ttl"));
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


		// Get and clean the predicate URI (There are no blank nodes in DBpedia)
		String predicate = triple.getMatchPredicate().getURI();
		if(!predicate.equals(RDF.type.getURI())){
			logger.error("Property is not rdf:type");
		}

		if(types.containsKey(subject)){
			// Get the object. It can be a URI or a literal (There are no blank nodes in the DBpedia)
			Node object = triple.getMatchObject();
			if(object.isURI()){
				// Get and clean the object URI (There are no blank nodes in DBpedia)
				String t=object.getURI();
				if(t.equals("http://www.w3.org/2002/07/owl#Thing")||t.equals("http://www.w3.org/2000/01/rdf-schema#Resource")){
					return;
				}
				String o=DBpediaHelper.stripClean(t);
				makeTarget(o);
				putTypes(subject,o);

				OntClass ontClass=ontModel.getOntClass(t);
				for (Iterator<OntClass> i = ontClass.listSuperClasses(true); i.hasNext(); ) {
					OntClass c = i.next();
					String type=c.getURI();
					if(type.equals("http://www.w3.org/2002/07/owl#Thing")||type.equals("http://www.w3.org/2000/01/rdf-schema#Resource")){
						continue;
					}
					type=DBpediaHelper.stripClean(type);
					makeTarget(type);
					putTypes(subject,type);
				}
			}
			else{
				logger.error("Value of type is not a URI");
			}
		}
		else{
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
