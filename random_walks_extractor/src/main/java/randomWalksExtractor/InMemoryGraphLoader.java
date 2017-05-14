package randomWalksExtractor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

/**
 * We use this class to load DBpedia Semantic Graph into memory and 
 * create a data set using the Target Variables file and Random Walks. 
 * This is implemented using the Apache Jena Stream RDF
 * @author rparundekar
 */
public class InMemoryGraphLoader implements StreamRDF{
	// SLF4J Logger bound to Log4J 
	private static final Logger logger=LoggerFactory.getLogger(InMemoryGraphLoader.class);

	// Data structure to ensure join
	private File targetVectorsFile;
	private Set<String> instancesInTargetVectorsFile;

	// The data structures used to hold the graph
	private Map<String,List<String>> attributes;
	private Map<String,Map<String,Set<String>>> relationships;
	private Map<String,Map<String,Set<String>>> incomingRelationships;

	// Data for output
	private File folder;
	private PrintWriter statsFile;
	
	private static final int TEST_LINES = 3100;
	
	/**
	 * Constructor for initializing and loading the graph.
	 * @param semanticGraphFile The file for the Semantic Graph 
	 * @param targetVectorsFile The target vectors file
	 * @throws IOException Thrown if there's a problem accessing files.
	 */
	public InMemoryGraphLoader(File semanticGraphFile, File targetVectorsFile) throws IOException{
		this.targetVectorsFile=targetVectorsFile;
		attributes=new HashMap<>();
		relationships=new HashMap<>();
		incomingRelationships=new HashMap<>();
		folder=semanticGraphFile.getParentFile();
		statsFile=new PrintWriter(new File(folder,targetVectorsFile.getName() + "_stats.txt"));

		//Load the instances in target vector file for inner join
		instancesInTargetVectorsFile=getInstancesInTargetVectorsFile(targetVectorsFile);
		//Load the semantic graph
		load(semanticGraphFile);
	}

	/**
	 * Load the RDF data from the DBPedia Semantic Graph turtle (.ttl) file 
	 * @param turtleFile The DBPedia turtle file e.g. infobox_properties.en.ttl 
	 */
	private void load(File turtleFile){
		try{
			// Read each line and use the RDFDataMgr to parse
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
		// Clean the URI 
		subject=DBpediaHelper.stripClean(subject);
		// Ensure inner join
		if(!instancesInTargetVectorsFile.contains(subject)){
			return;
		}
		// Get and clean the predicate URI (There are no blank nodes in DBpedia)
		String predicate = triple.getMatchPredicate().getURI();
		predicate = DBpediaHelper.stripClean(predicate);

		// Get the object. It can be a URI or a literal (There are no blank nodes in the DBpedia)
		Node object = triple.getMatchObject();
		Object o=null;
		if(object.isURI()){
			// Get and clean the object URI (There are no blank nodes in DBpedia)
			o=object.getURI();
			String obj =DBpediaHelper.stripClean((String)o);
			// Ensure inner join also on the object URIs
			if(!instancesInTargetVectorsFile.contains(obj)){
				return;
			}

			// Add to the relationships data structure
			Map<String, Set<String>> itsRelationships = relationships.get(subject);
			if(itsRelationships==null){
				itsRelationships=new HashMap<>();
				relationships.put(subject, itsRelationships);
			}
			Set<String> others=itsRelationships.get(predicate);
			if(others==null)
			{
				others=new HashSet<>();
				itsRelationships.put(predicate, others);
			}
			others.add(obj);
			
			// Add to the incoming relationships
			Map<String,Set<String>> itsIncomingRelationships = incomingRelationships.get(obj);
			if(itsIncomingRelationships==null){
				itsIncomingRelationships=new HashMap<>();
				incomingRelationships.put(obj, itsIncomingRelationships);
			}
			others=itsIncomingRelationships.get(predicate);
			if(others==null)
			{
				others=new HashSet<>();
				itsIncomingRelationships.put(predicate, others);
			}
			others.add(subject);
		}else{
			// Add the name of the attribute to the attribute data structure
			List<String> itsAttributes = attributes.get(subject);
			if(itsAttributes==null){
				itsAttributes=new ArrayList<>();
				attributes.put(subject, itsAttributes);
			}
			itsAttributes.add(predicate);
		}
	}

	/**
	 * Perform basic statistical analysis of the in-memory graph 
	 */
	public void count(){
		int numberOfInstances=0;
		int totalCount=0;
		Set<String> instances=new HashSet<>();
		int distinctCount=0;
		
		//Count the attributes
		for(String subject:attributes.keySet()){
			numberOfInstances++;
			totalCount+=attributes.get(subject).size();
			distinctCount+=new HashSet<>(attributes.get(subject)).size();
			if(!instances.contains(subject))
				instances.add(subject);
		}
		statsFile.println("Average number of attributes: " + totalCount + "/"+ numberOfInstances +" = " + (totalCount*1.0/numberOfInstances));
		logger.info("Average number of attributes: {}/{}={}", totalCount, numberOfInstances, (totalCount*1.0/numberOfInstances));
		statsFile.println("Average number of distinct attributes: " + distinctCount + "/"+ numberOfInstances +" = " + (distinctCount*1.0/numberOfInstances));
		logger.info("Average number of distinct attributes: {}/{}={}", distinctCount, numberOfInstances, (distinctCount*1.0/numberOfInstances));

		//Count the relationships
		numberOfInstances=0;
		totalCount=0;
		distinctCount=0;
		for(String subject:relationships.keySet()){
			numberOfInstances++;
			Map<String, Set<String>> itsRelationships = relationships.get(subject);
			for(String rel:itsRelationships.keySet()){
				distinctCount++;
				totalCount+=itsRelationships.get(rel).size();
			}
			if(!instances.contains(subject))
				instances.add(subject);
		}
		statsFile.println("Average number of relationships: " + totalCount + "/"+ numberOfInstances +" = " + (totalCount*1.0/numberOfInstances));
		statsFile.println("Average number of distinct relationships: " + distinctCount + "/"+ numberOfInstances +" = " + (distinctCount*1.0/numberOfInstances));
		logger.info("Average number of relationships: {}/{}={}", totalCount, numberOfInstances, (totalCount*1.0/numberOfInstances));
		logger.info("Average number of distinct relationships: {}/{}={}", distinctCount, numberOfInstances, (distinctCount*1.0/numberOfInstances));

		//Count the incoming relationships
		totalCount=0;
		distinctCount=0;
		for(String subject:incomingRelationships.keySet()){
			numberOfInstances++;
			Map<String, Set<String>> itsIncomingRelationships = incomingRelationships.get(subject);
			for(String rel:itsIncomingRelationships.keySet()){
				distinctCount++;
				totalCount+=itsIncomingRelationships.get(rel).size();
			}
			if(!instances.contains(subject))
				instances.add(subject);
		}
		statsFile.println("Average number of incoming relationships: " + totalCount + "/"+ numberOfInstances +" = " + (totalCount*1.0/numberOfInstances));
		statsFile.println("Average number of distinct incoming relationships: " + distinctCount + "/"+ numberOfInstances +" = " + (distinctCount*1.0/numberOfInstances));
		logger.info("Average number of incoming relationships: {}/{}={}", totalCount, numberOfInstances, (totalCount*1.0/numberOfInstances));
		logger.info("Average number of distinct incoming relationships: {}/{}={}", distinctCount, numberOfInstances, (distinctCount*1.0/numberOfInstances));

		//Count the total number of instances 
		logger.info("Total number of instances: {}", instances.size());
		statsFile.println("Total number of instances:" + instances.size());
		logger.info("Done");
		statsFile.flush();
	}

	/**
	 * Loads the instances in the target vectors file for inner-join.
	 * @param targetVectorsFile The target vectors file
	 * @return The set of instances in the target vectors file
	 * @throws IOException Thrown if there's an error in reading the file
	 */
	private static Set<String> getInstancesInTargetVectorsFile(File targetVectorsFile) throws IOException {
		logger.info("Loading instances from one hot file...");
		Set<String> instances=new HashSet<>();
		LineNumberReader lineNumberReader=new LineNumberReader(new FileReader(targetVectorsFile));
		lineNumberReader.readLine();
		String line=null;
		//Read each line and first item on each line after splitting on comma
		while((line=lineNumberReader.readLine())!=null){
			try{
				String id=line.substring(0, line.indexOf(","));
				instances.add(id.trim());
			}catch(StringIndexOutOfBoundsException se){
				logger.error("Error in line: {}",line);
			}
		}
		logger.info("Done. Loaded {} instances...", instances.size());
		lineNumberReader.close();
		return instances;
	}

	/**
	 * Get the random walks
	 * @param id The instance id to start on
	 * @param allowedTypes The allowed type of steps
	 * @param maxLengths The max lengths to be extracted
	 * @param numbersOfWalks The number of walks
	 * @return The random walks for each parameter combination
	 */
	public Map<String, Set<String>> getWalks(String id, List<StepType> allowedTypes, List<Integer> maxLengths, List<Integer> numbersOfWalks) {
		Map<String, Set<String>> allWalks = new HashMap<>();
		// Make sure the instance is in the joined instances
		if(!instancesInTargetVectorsFile.contains(id))
			return allWalks;
		
		// Cache to help with faster listing the random walks available
		Map<String, List<String>> stepsCache = new HashMap<>();
		Map<String, List<String>> nextNodeCache = new HashMap<>();
		
		// Repeat for each length and number of walks
		for(Integer maxLength:maxLengths){
			for(Integer numberOfWalks:numbersOfWalks){
				Set<String> walks=new HashSet<>();
				
				// Code for strategy of variable length
				// Uncomment if needed
				//				List<Integer> lengthList = new ArrayList<>();
				//				for(int i=1;i<=maxLength;i++)
				//					for(int j=maxLength;j>=i;j--)
				//						lengthList.add(i);
				for(int eachWalk=0;eachWalk<numberOfWalks;eachWalk++){
					// Pick length of walk using fixed length strategy
					int lengthOfWalk = maxLength;
					// Pick length of walk using variable length strategy
					// int lengthOfWalk = lengthList.get((int)Math.floor(Math.random()*lengthList.size()));
					
					// Starting at the current node, note the steps
					String currentNodeId=id;
					
					// While still at the current node, order steps lexicographically
					Set<String> stepsAtNode = new TreeSet<>();
					
					// The walk feature
					StringBuilder walk=new StringBuilder();
					
					// For each step
					for(int step=0;step<lengthOfWalk;step++){
						// Ensure that the node we are on is part of the inner join
						if(!instancesInTargetVectorsFile.contains(currentNodeId))
							break;
						
						//Available steps and next nodes
						List<String> availableSteps = null;
						List<String> nextNodes = null;
						
						// If the available steps is not in cache
						if(!stepsCache.containsKey(currentNodeId)){
							availableSteps = new ArrayList<String>();
							nextNodes = new ArrayList<String>();
							stepsCache.put(currentNodeId, availableSteps);
							nextNodeCache.put(currentNodeId, nextNodes);
							
							// Add all attribute presence as available steps 
							if(allowedTypes.contains(StepType.HAS_ATTRIBUTE)){
								List<String> itsAttributes=attributes.get(currentNodeId);
								if(itsAttributes!=null){
									for(String attr:itsAttributes){
										String s="has_" + attr+",";
										if(!availableSteps.contains(s)){
											availableSteps.add(s);
											nextNodes.add(currentNodeId);
										}
									}
								}
							}
							
							// Add all relationship presence and outgoing relationships available steps 
							if((allowedTypes.contains(StepType.HAS_RELATIONSHIP)||allowedTypes.contains(StepType.RELATIONSHIP_STEP))){
								Map<String, Set<String>> itsRelationships=relationships.get(currentNodeId);
								if(itsRelationships!=null){
									for(String relationship:itsRelationships.keySet()){
										if(allowedTypes.contains(StepType.HAS_RELATIONSHIP)){
											String s="hasRel_" + relationship +",";
											if(!availableSteps.contains(s)){
												availableSteps.add(s);
												nextNodes.add(currentNodeId);
											}
										}
										//Only add outgoing relationships if length is > 1. 
										//Else it is same as above
										Set<String> others=itsRelationships.get(relationship);
										if(others!=null){
											if(allowedTypes.contains(StepType.RELATIONSHIP_STEP) && lengthOfWalk>1){
												for(String other:others){
													//Add only unique relationships
													if(!other.equals(id) && !availableSteps.contains(relationship +"->")){
														availableSteps.add(relationship +"->");
														nextNodes.add(other);
													}
												}
											}
										}
									}
								}
							}
							
							// Add all incoming relationship presence and incoming relationships available steps
							if(allowedTypes.contains(StepType.HAS_INCOMING_RELATIONSHIP)||allowedTypes.contains(StepType.RELATIONSHIP_STEP)){
								Map<String, Set<String>> itsIncomingRelations = incomingRelationships.get(currentNodeId);
								if(itsIncomingRelations!=null){
									for(String relationship:itsIncomingRelations.keySet()){
										if(allowedTypes.contains(StepType.HAS_INCOMING_RELATIONSHIP)){
											String s="hasInRel_" + relationship+",";
											if(!availableSteps.contains(s)){
												availableSteps.add(s);
												nextNodes.add(currentNodeId);
											}
										}
										//Only add outgoing relationships if length is > 1. 
										//Else it is same as above
										Set<String> others=itsIncomingRelations.get(relationship);
										if(others!=null){
											if(allowedTypes.contains(StepType.RELATIONSHIP_STEP) && lengthOfWalk>1){
												for(String other:others){
													//Add only unique relationships
													if(!other.equals(id) && !availableSteps.contains(relationship +"<-")){
														availableSteps.add(relationship +"<-");
														nextNodes.add(other);
													}
												}
											}
										}
									}
								}
							}
						}else{
							// Get the steps available from the cache
							availableSteps=stepsCache.get(currentNodeId);
							nextNodes=nextNodeCache.get(currentNodeId);
						}
						
						if(availableSteps.isEmpty()){
							//Stay on same node if no available steps
							continue;
						}
						
						// Pick one step randomly
						int index=(int) Math.floor(Math.random()*availableSteps.size());
						String s=availableSteps.get(index);
						String nextNodeId = nextNodes.get(index);
						
						if(currentNodeId.equals(nextNodeId)){
							//If we are on same node add to lexicographic order of steps
							stepsAtNode.add(s);
						}else{
							//Else pop the lexicographic order of steps and append to the walk
							for(String st:stepsAtNode)
								walk.append(st);
							stepsAtNode.clear();
							walk.append(s);
						}
						currentNodeId=nextNodeId;
					}
					// Pop any remaining lexicographic order of steps and append to the walk
					for(String st:stepsAtNode)
						walk.append(st);
					stepsAtNode.clear();

					// Cleanup
					String w = walk.toString().trim();
					if(w.endsWith(","))
						w=w.substring(0, w.length()-1);

					if(!walks.contains(w) && !w.isEmpty())
						walks.add(w);
				}
				// Add to the walks
				allWalks.put(maxLength+ "x" + numberOfWalks, walks);
			}
		}
		return allWalks;
	}

	/**
	 * Get all the walks available of length 1 and allowed types
	 * @param id The id of the instance
	 * @param allowedTypes The list of allowed types
	 * @return All the walks available
	 */
	public Map<String,Set<String>> getAll(String id, List<StepType> allowedTypes) {
		Set<String> walks=new HashSet<>();
		String currentNodeId=id;
		// Ensure inner join
		if(instancesInTargetVectorsFile.contains(currentNodeId))
		{
			Set<String> availableSteps = new HashSet<String>();
			// Add the attribute presence to the available steps
			if(allowedTypes.contains(StepType.HAS_ATTRIBUTE)){
				List<String> itsAttributes=attributes.get(currentNodeId);
				if(itsAttributes!=null){
					for(String attr:itsAttributes){
						String s="has_" + attr;
						if(!availableSteps.contains(s)){
							availableSteps.add(s);
						}
					}
				}
			}
			// Add the relationship presence to the available steps
			if(allowedTypes.contains(StepType.HAS_RELATIONSHIP)){
				Map<String, Set<String>> itsRelationships=relationships.get(currentNodeId);
				if(itsRelationships!=null){
					for(String relationship:itsRelationships.keySet()){
						if(allowedTypes.contains(StepType.HAS_RELATIONSHIP)){
							String s="hasRel_" + relationship;
							if(!availableSteps.contains(s)){
								availableSteps.add(s);
							}
						}
					}
				}
			}
			// Add the incoming relationship presence to the available steps
			if(allowedTypes.contains(StepType.HAS_INCOMING_RELATIONSHIP)){
				Map<String, Set<String>> itsIncomingRelations = incomingRelationships.get(currentNodeId);
				if(itsIncomingRelations!=null){
					for(String relationship:itsIncomingRelations.keySet()){
						String s="hasInRel_" + relationship;
						if(!availableSteps.contains(s)){
							availableSteps.add(s);
						}
					}
				}
			}
			// Cleanup
			for(String walk:availableSteps){
				String w = walk.toString().trim();
				if(w.endsWith(","))
					w=w.substring(0, w.length()-1);
				else if(w.endsWith("->") || w.endsWith("<-"))
					w+="id="+currentNodeId;

				if(!walks.contains(w) && !w.isEmpty())
					walks.add(w);
			}
		}
		// Add all the available walks and return 
		Map<String, Set<String>> returnObject =  new HashMap<>();
		returnObject.put("1xall", walks);
		return returnObject;
	}

	/**
  	 * Create the dataset from the target vector file using the random walk methods.
	 * Also creates batches of data.
	 * @param datasetName The name of the dataset
	 * @param allowedSteps The steps allowed
	 * @param maxLengths The max length
	 * @param numbersOfWalks The number of walks
	 */
	public void create(String datasetName, List<StepType> allowedSteps, List<Integer> maxLengths, List<Integer> numbersOfWalks){
		boolean test=false;
		//Then, we create the dataset using random walks.
		logger.info("Creating the dataset {} _ {} x {}", datasetName, maxLengths, numbersOfWalks);
		Map<String,Map<String,int[]>> allRandomWalks;
		Map<String,Map<String,Integer>> allRandomWalkIds;
		Map<String,Integer> walkCounters; 
		allRandomWalks=new HashMap<>();
		allRandomWalkIds=new HashMap<>();
		walkCounters=new HashMap<>();
		try{
			//Read each line from the target vectors file to get the id & then get random walks
			CSVReader csvReader = new CSVReader(new FileReader(targetVectorsFile));
			//Read the header
			String[] header=csvReader.readNext();
			if(!header[0].equals("id")){
				logger.error("First column is not the id");
			}else{
				String[] row=null;

				long start = System.currentTimeMillis();
				while((row=csvReader.readNext())!=null){
					// Print progress
					String id=row[0];
					
					// Get the walks
					Map<String, Set<String>> allWalks=null;
					if(numbersOfWalks!=null){
						allWalks=getWalks(id, allowedSteps, maxLengths, numbersOfWalks);
					}else{
						allWalks=getAll(id, allowedSteps);
					}
					
					// Add to the dataset of walks
					for(String dataset:allWalks.keySet()){
						Set<String> walks=allWalks.get(dataset);
						if(!walks.isEmpty()){
							Set<String> ws = walks;
							Map<String, int[]> randomWalks = allRandomWalks.get(dataset);
							if(randomWalks==null)
							{
								randomWalks=new HashMap<>();
								allRandomWalks.put(dataset, randomWalks);
							}
							Map<String, Integer> randomWalkIds = allRandomWalkIds.get(dataset);
							if(randomWalkIds==null)
							{
								randomWalkIds=new HashMap<>();
								allRandomWalkIds.put(dataset, randomWalkIds);
							}
							if(!walkCounters.containsKey(dataset))
								walkCounters.put(dataset, 1);

							int walkCounter=walkCounters.get(dataset);

							int[] oneHotWs=randomWalks.get(id);
							if(oneHotWs==null){
								oneHotWs=new int[ws.size()];
								for(int i=0;i<oneHotWs.length;i++){
									oneHotWs[i]=-1;
								}
								randomWalks.put(id, oneHotWs);
							}
							int counter=0;
							for(String w:ws){
								Integer walkId=randomWalkIds.get(w);
								if(walkId==null){
									walkId=walkCounter++;
									randomWalkIds.put(w, walkId);
								}
								if(oneHotWs==null)
									System.err.println("Here");
								oneHotWs[counter]=walkId;
								counter++;
							}

							walkCounters.put(dataset, walkCounter);
						}
					}
					if(csvReader.getLinesRead()%1000==0){
						logger.info("{} lines parsed to random walk in {} ms.", csvReader.getLinesRead(), (System.currentTimeMillis()-start));
						start = System.currentTimeMillis();
					}
					if(test && csvReader.getLinesRead()>TEST_LINES){
						break;
					}
				}
			}
			// Close IO
			csvReader.close();
		}catch(IOException e){
			// Something went wrong with the files.
			logger.error("Cannot load the data from the file due to file issue:" + e.getMessage());
		}

		// Create the batches
		for(String dataset:allRandomWalks.keySet()){
			try{
				File datasetFolder=new File(targetVectorsFile.getParentFile(),datasetName+"_"+dataset);
				datasetFolder.mkdir();

				CSVReader csvReader = new CSVReader(new FileReader(targetVectorsFile));
				//Read the header
				String[] header=csvReader.readNext();
				String[] newHeader=new String[header.length];
				PrintWriter headersX=new PrintWriter(new File(datasetFolder,"headerX.csv"));
				PrintWriter headersY=new PrintWriter(new File(datasetFolder,"headerY.csv"));

				headersY.println("header, short");
				for(int i=0;i<newHeader.length;i++){
					newHeader[i]="c"+i;
				}
				newHeader[0]="id";
				for(int i=0;i<newHeader.length;i++){
					headersY.println(header[i]+","+newHeader[i]);
					headersY.flush();
				}
				headersY.close();


				CSVWriter datasetYWriter=null;
				int walkCounter=walkCounters.get(dataset);
				Map<String, int[]> randomWalks = allRandomWalks.get(dataset);
				Map<String, Integer> randomWalkIds = allRandomWalkIds.get(dataset);

				String[] oneHotWalksHeader= new String[walkCounter];
				String[] shortOneHotWalksHeader= new String[walkCounter];
				oneHotWalksHeader[0]="id";
				shortOneHotWalksHeader[0]="id";
				for(String oneHotWalk:randomWalkIds.keySet()){
					oneHotWalksHeader[randomWalkIds.get(oneHotWalk)]=oneHotWalk;
					shortOneHotWalksHeader[randomWalkIds.get(oneHotWalk)]="walk_"+(randomWalkIds.get(oneHotWalk));
				}
				headersX.println("header, short");
				for(int i=0;i<oneHotWalksHeader.length;i++){
					headersX.println(oneHotWalksHeader[i]+","+shortOneHotWalksHeader[i]);
					headersX.flush();
				}
				headersX.close();

				PrintWriter sparseDataXWriter=null;
				File folder = new File(datasetFolder, "dataset");
				if(!folder.exists())
					folder.mkdir();

				//Read each line to get the id & then get random walks & create a dataset 
				//file for the walks and a dataset file for the classes.
				String[] row=null;
				int batch=0;
				int batchSize=5000;
				while((row=csvReader.readNext())!=null){
					long linesRead = csvReader.getLinesRead();
					if(linesRead>(batch*batchSize)){
						batch++;
						if(datasetYWriter!=null)
							datasetYWriter.close();
						datasetYWriter=new CSVWriter(new FileWriter(new File(folder,"datasetY_" + batch +".csv")), ',', CSVWriter.NO_QUOTE_CHARACTER);
						datasetYWriter.writeNext(newHeader);
						datasetYWriter.flush();

						if(sparseDataXWriter!=null)
							sparseDataXWriter.close();
						sparseDataXWriter=new PrintWriter(new FileWriter(new File(folder,"datasetX_" + batch +".csv")));
						String head=Arrays.toString(shortOneHotWalksHeader);
						head=head.substring(1,head.length()-1).trim();
						sparseDataXWriter.println(head);
						sparseDataXWriter.flush();
					}
					// Print progress
					String id=row[0];
					if(!randomWalks.containsKey(id))
						continue;
					int[] walks=randomWalks.get(id);
					Set<Integer> set = new HashSet<>();
					for(int k=0;k<walks.length;k++)
					{
						if(walks[k]==-1)
							continue;
						set.add(walks[k]);
					}

					String r = set.toString();
					r=r.substring(1, r.length()-1).trim();
					sparseDataXWriter.println(id + "," + r);
					sparseDataXWriter.flush();

					datasetYWriter.writeNext(row);
					datasetYWriter.flush();

					if(csvReader.getLinesRead()%10000==0){
						logger.info("{} lines parsed to create the final dataset - {}.", csvReader.getLinesRead(), datasetFolder.getName());
					}
					if(test && csvReader.getLinesRead()>TEST_LINES){
						break;
					}
				}
				csvReader.close();
				datasetYWriter.close();
				sparseDataXWriter.close();
			}catch(IOException e){
				// Something went wrong with the files.
				logger.error("Cannot write data to dataset file due to file issue:" + e.getMessage());
			}
		}
	}
	
	/**
	 * Get stuff running.
	 * @param args Have the username, password, if DB should be cleared AND list of files to load here
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws IOException{
		File semanticGraphFile=new File("/Users/rparundekar/dataspace/dbpedia2016/infobox_properties_en.ttl");
		File targetVectorsFile=new File("/Users/rparundekar/dataspace/dbpedia2016/oneHot.csv");
		InMemoryGraphLoader inMemoryGraphLoader=new InMemoryGraphLoader(semanticGraphFile,targetVectorsFile);
		inMemoryGraphLoader.statsFile.println("Stats for data after one hot inner join");
		inMemoryGraphLoader.statsFile.flush();
		logger.info("Stats for data after one hot inner join:");
		inMemoryGraphLoader.count();
		List<StepType> allowedSteps=null;
		List<Integer> maxLengths=null;
		List<Integer> numbersOfWalks=null;
		
		// Example: Extract walk of length of 2 with only move.
		allowedSteps=new ArrayList<>();
		allowedSteps.add(StepType.RELATIONSHIP_STEP);
		maxLengths = new ArrayList<>();
		maxLengths.add(2);
		numbersOfWalks = new ArrayList<>();
		numbersOfWalks.add(125);
		inMemoryGraphLoader.create("step",allowedSteps, maxLengths, numbersOfWalks);
	}
}