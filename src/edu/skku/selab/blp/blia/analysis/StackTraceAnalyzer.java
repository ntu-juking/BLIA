/**
 * Copyright (c) 2014 by Software Engineering Lab. of Sungkyunkwan University. All Rights Reserved.
 * 
 * Permission to use, copy, modify, and distribute this software and its documentation for
 * educational, research, and not-for-profit purposes, without fee and without a signed licensing agreement,
 * is hereby granted, provided that the above copyright notice appears in all copies, modifications, and distributions.
 */
package edu.skku.selab.blp.blia.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import edu.skku.selab.blp.Property;
import edu.skku.selab.blp.common.Bug;
import edu.skku.selab.blp.db.IntegratedAnalysisValue;
import edu.skku.selab.blp.db.dao.BugDAO;
import edu.skku.selab.blp.db.dao.IntegratedAnalysisDAO;
import edu.skku.selab.blp.db.dao.SourceFileDAO;


/**
 * @author Klaus Changsun Youm(klausyoum@skku.edu)
 *
 */
public class StackTraceAnalyzer {
	private final static double DEFAULT_BOOST_SCORE = 0.1;
	private ArrayList<Bug> bugs;
	
	public StackTraceAnalyzer() {
		bugs = null;
	}
	
    public StackTraceAnalyzer(ArrayList<Bug> bugs) {
    	this.bugs = bugs;
    }
	
	public void analyze() throws Exception {
		String productName = Property.getInstance().getProductName();
		
		SourceFileDAO sourceFileDAO = new SourceFileDAO();
		IntegratedAnalysisDAO integratedAnalysisDAO = new IntegratedAnalysisDAO();
		
		HashMap<String, HashSet<String>> classNamesMap = new HashMap<String, HashSet<String>>();
		for (int i = 0; i < bugs.size(); i++) {
			Bug bug = bugs.get(i);
			String version = bug.getVersion();
			
			if (!classNamesMap.containsKey(version)) {
				HashSet<String> classNames = sourceFileDAO.getClassNames(productName, version);
				classNamesMap.put(version, classNames);
			}
		}
		
		for (int i = 0; i < bugs.size(); i++) {
			Bug bug = bugs.get(i);
			
			String version = bug.getVersion();
			HashMap<String, IntegratedAnalysisValue> stackTraceAnalysisValues = new HashMap<String, IntegratedAnalysisValue>();
			HashMap<String, IntegratedAnalysisValue> importedClassAnalysisValues = new HashMap<String, IntegratedAnalysisValue>();
			ArrayList<String> stackTraceClasses = bug.getStackTraceClasses();
			
			if (null == stackTraceClasses) {
				continue;
			}
			
			HashSet<String> classNames = classNamesMap.get(version);
			for (int j = 0; j < stackTraceClasses.size(); j++) {
				int currentRank = j + 1;
				
				String className = stackTraceClasses.get(j);
				String fileName = className + ".java";
				if (!stackTraceAnalysisValues.containsKey(className) && classNames.contains(className)) {
					IntegratedAnalysisValue stackTraceAnalysisValue = new IntegratedAnalysisValue();
					stackTraceAnalysisValue.setBugID(bug.getID());
					
					int sourceFileVersionID = sourceFileDAO.getSourceFileVersionID(fileName, productName, version);
					stackTraceAnalysisValue.setSourceFileVersionID(sourceFileVersionID);
					
					if (j < 10) {
						// set boostscore for class in stack-trace within rank 10
						stackTraceAnalysisValue.setStackTraceScore(1.0 / currentRank);
					} else {
						// set boostscore, 0.1 for class in stack-trace above rank 10
						stackTraceAnalysisValue.setStackTraceScore(DEFAULT_BOOST_SCORE);
					}
					stackTraceAnalysisValues.put(className, stackTraceAnalysisValue);
				}
			}
			
			for (int j = 0; j < stackTraceClasses.size(); j++) {
				String className = stackTraceClasses.get(j);
				String fileName = className + ".java";
				ArrayList<String> importedClasses = sourceFileDAO.getImportedClasses(productName, version, fileName);
				
				if (null == importedClasses) {
					continue;
				}
				
				// find import files and filter them => set C
				// give boostscore to set C		
				for (int k = 0; k < importedClasses.size(); k++) {
					if (!stackTraceAnalysisValues.containsKey(className) &&
							!importedClassAnalysisValues.containsKey(className) && classNames.contains(className)) {
						IntegratedAnalysisValue importedClassAnalysisValue = new IntegratedAnalysisValue();
						importedClassAnalysisValue.setBugID(bug.getID());
						
						int sourceFileVersionID = sourceFileDAO.getSourceFileVersionID(fileName, productName, version);
						importedClassAnalysisValue.setSourceFileVersionID(sourceFileVersionID);
						importedClassAnalysisValue.setStackTraceScore(DEFAULT_BOOST_SCORE);
						
						importedClassAnalysisValues.put(className, importedClassAnalysisValue);
					}
				}
				
				Iterator<IntegratedAnalysisValue> analysisValuesIter = stackTraceAnalysisValues.values().iterator();
				while (analysisValuesIter.hasNext()) {
					integratedAnalysisDAO.updateStackTraceScore(analysisValuesIter.next());
				}
				
				analysisValuesIter = importedClassAnalysisValues.values().iterator();
				while (analysisValuesIter.hasNext()) {
					integratedAnalysisDAO.updateStackTraceScore(analysisValuesIter.next());
				}
			}
		}
	}
}
