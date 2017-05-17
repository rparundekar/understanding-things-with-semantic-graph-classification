# Understanding 'Things' using Semantic Graph Classification
The world around us contains different types of things (e.g. people, places, objects, ideas, etc.) that are defined by their attributes and relationship. To act automatically on such data, any software Agent needs to be able to infer the meaning of these things that form a Semantic Graph. Using DBpedia as an exemplary dataset, we create a robust type inferencing system in the presence of noisy data and the OpenWorld Assumption on the SemanticWeb. Our approach extracts features from the Semantic Graph using multiple Random Walks and then performs multilabel classification using Deep Neural Networks. This report presents our exploration, experimentation, and results of identifying DBpedia ontology types, categories and Yago ontology types for individuals in DBpedia. Our method consistently performs better than state-of-the-art type inferencing systems, like SDtype and SLCN, from which we conclude that Random Walk based feature extraction and multi-label classification is a promising approach in understanding things and contexts in domains that represent information as a Semantic Graph. 


## Code
The code has two parts:
1. random_walks_extractor: Java code for extracting features from RDF semantic graph
 - To run this code, please open the Maven project in this folder in your IDE of choice (e.g. eclipse)
 - Download the datasets from DBpedia as described in teh Dataset section below.
 - Run the 'main' method in the DBpediaTypes2TargetVectors.java file to create the target vector file.
 - Run the 'main' method in the InMemoryGraphLoader.java with the patameters for the random walk generation. 
 - In both these cases, make sure to change the path of the files to the target vector file and the graph properties file.  
 - Each execution takes a few hours. The program is memory intensive, and so please use a machine with 16GB or more RAM.
2. type_inferencing: Multi-label classification on the features and target vectors extracted in 1.
 - For the type inferencing using Deep Learning, please use an environment with Python3, TensorFlow (v1.0.0) and Keras (v1.2.2) on a machine with GPGPU (at least 4GB RAM). Please make sure that TensorFlow uses the graphics card for learning and testing.
 - Start Jupyter Notebook server
 - Open the type_inferencing.ipynb code
 - Change the path of the dataset folder to the dataset extracted.
 - Make sure to change the number of files in the dataset.
 - Run the notebook

## Dataset
Since the dataset files are large, you might need to recreate them using the random_walks_extractor code above. 
1. You need to download the following files from DBpedia <a href='http://wiki.dbpedia.org/downloads-2016-04'>downloads page</a>:
 - DBpedia Ontology (dbpedia_2016-04.owl)
 - Infobox properties file (infobox_properties_en.ttl)
 - Instance Types file (instance_types_en.ttl) 
2. Then run the DBpediaTypes2TargetVectors.java and InMemoryGraphLoader.java code with the steps above.

Additionally, if you'd like to test DBpedia-Categories and DBpedia-YagoTypes classifications download the two files below and run DBpediaCategories2TargetVectors.java and DBpediaYAGO2TargetVectors.java:
- Article categories file (article_categories_en.ttl)
- Yago types file (yago_types.ttl)

## Background
This project is my final report for the Udacity Machine Learning Nanodegree.
