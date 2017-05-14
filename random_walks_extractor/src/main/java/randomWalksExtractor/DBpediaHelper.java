package randomWalksExtractor;

public class DBpediaHelper {
	/**
	 * Strips the DBpedia URI prefixes & replaces non alphanumeric characters to avoid property errors 
	 * @param uri The URI to strip & clean
	 * @return The last part of the URI if in DBpedia.
	 */
	static String stripClean(String uri) {
		if(uri.startsWith("http://dbpedia.org/ontology/"))
			uri=uri.substring("http://dbpedia.org/ontology/".length());
		if(uri.startsWith("http://dbpedia.org/resource/Category:"))
			uri=uri.substring("http://dbpedia.org/resource/Category:".length());
		if(uri.startsWith("http://dbpedia.org/resource/"))
			uri=uri.substring("http://dbpedia.org/resource/".length());
		if(uri.startsWith("http://dbpedia.org/property/"))
			uri=uri.substring("http://dbpedia.org/property/".length());
		if(uri.startsWith("http://dbpedia.org/datatype/"))
			uri=uri.substring("http://dbpedia.org/datatype/".length());
		if(uri.startsWith("http://dbpedia.org/class/yago/"))
			uri=uri.substring("http://dbpedia.org/class/yago/".length());
		// Note: This may cause some loss of information, but will prevent errors.
		return uri.replaceAll("[^A-Za-z0-9]", "_");
	}
}
