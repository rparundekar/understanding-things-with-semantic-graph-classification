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


## Usage (Python)
Please use the ```.ipynb``` notebooks (on ```github``` itself) to see the code. 

## Background
This project is my final report for the Udacity Machine Learning Nanodegree.
