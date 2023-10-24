import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import processing.core.PApplet;
import processing.sound.AudioIn;
import processing.sound.FFT;
import processing.sound.Sound;
import processing.sound.Waveform;

/* A class with the main function and Processing visualizations to run the demo */

public class ClassifyVibration extends PApplet {

	FFT fft;
	AudioIn in;
	Waveform waveform;
	int bands = 512;
	int nsamples = 1024;
	float[] spectrum = new float[bands];
	float[] fftFeatures = new float[bands];
	
	// Change class names for vibration: quiet, knocking, etc.
	String[] classNames = {"Neutral", "Scratch", "Tap"};
	int classIndex = 0;
	int dataCount = 0;

	MLClassifier classifier;
	String saveFileName = "test2.csv";
	String loadFileName = "test.csv";
	String delimiter = ",";
	
	/*variables for space bar data collection */
	boolean collectData = false;
	ArrayList<String> dataCollected = new ArrayList<String>();
	ArrayList<String> results = new ArrayList<String>();
	
	Map<String, List<DataInstance>> trainingData = new HashMap<>();
	{
		for (String className : classNames){
			trainingData.put(className, new ArrayList<DataInstance>());
		}
	}
	
	DataInstance captureInstance (String label){
		DataInstance res = new DataInstance();
		res.label = label;
		res.measurements = fftFeatures.clone();
		return res;
	}
	
	public String findMode(ArrayList<String> values) {
		if (values.size() == 0) {	//edge case of all neutral values
			return "No Action Detected";
		}
		
		String mostFound = "";
		int[] classifications = {0, 0};
		for (String i : values) {
			if (i == "Scratch") {classifications[0] = classifications[0] + 1;}
			else if (i == "Tap") {classifications[1] = classifications[1] + 1;}
		}
		if (classifications[0] > classifications[1]) {mostFound = "Scratch";}
		else if (classifications[0] < classifications[1]) {mostFound = "Tap";}
		else {
			//mostFound = "Tap";
			mostFound = "Neutral";
			}	//default to tap in case that there is a tie since it is less reliable than scratch. Wrote separately to make updating edge case easier
		
		System.out.println(classifications[0] + " " + classifications[1]);
		
		return mostFound;
	}
	
	public static void main(String[] args) {
		PApplet.main("ClassifyVibration");
	}
	
	public void settings() {
		size(512, 400);
	}

	public void setup() {
		
		/* list all audio devices */
		Sound.list();
		Sound s = new Sound(this);
		  
		/* select microphone device */
		s.inputDevice(5);
		    
		/* create an Input stream which is routed into the FFT analyzer */
		fft = new FFT(this, bands);
		in = new AudioIn(this, 0);
		waveform = new Waveform(this, nsamples);
		waveform.input(in);
		
		/* start the Audio Input */
		in.start();
		  
		/* patch the AudioIn */
		fft.input(in);
	}

	public void draw() {
		background(0);
		fill(0);
		stroke(255);
		
		waveform.analyze();

		beginShape();
		  
		for(int i = 0; i < nsamples; i++)
		{
			vertex(
					map(i, 0, nsamples, 0, width),
					map(waveform.data[i], -1, 1, 0, height)
					);
		}
		
		endShape();

		fft.analyze(spectrum);

		for(int i = 0; i < bands; i++){

			/* the result of the FFT is normalized */
			/* draw the line for frequency band i scaling it up by 40 to get more amplitude */
			line( i, height, i, height - spectrum[i]*height*40);
			fftFeatures[i] = spectrum[i];
		} 

		fill(255);
		textSize(30);
		if(classifier != null) {
			String guessedLabel = classifier.classify(captureInstance(null));
			
			// Yang: add code to stabilize your classification results
			
			if (collectData == false) {
				text("Press space bar to start recording data", 20, 30);
			}
			
			if ((collectData == true)) {	//space bar pressed the first time, start collecting data
				text("Now collecting data", 20, 30);
				if (guessedLabel != "Neutral") {
					dataCollected.add(guessedLabel);
				}
			}
			
//			text("classified as: " + guessedLabel, 20, 30);
//			if(guessedLabel != "Neutral") {
//				System.out.println(guessedLabel);
			}
		else {
			text(classNames[classIndex], 20, 30);
			dataCount = trainingData.get(classNames[classIndex]).size();
			text("Data collected: " + dataCount, 20, 60);
		}
	}
	
	public void keyPressed() {
		

		if (key == CODED && keyCode == DOWN) {
			classIndex = (classIndex + 1) % classNames.length;
		}
		
		// Start training model
		else if (key == 't') {
			if(classifier == null) {
				println("Start training ...");
				classifier = new MLClassifier();
				classifier.train(trainingData);
			}else {
				classifier = null;
			}
		}
		
		// Save data as a model
		else if (key == 's') {
			System.out.println("Saving file");
			
			File csvFile = new File(saveFileName);
			FileWriter fileWriter;
			try {
				fileWriter = new FileWriter(csvFile);
			
				for (String className : classNames) {
					for (DataInstance data : trainingData.get(className)) {
						String line = data.toCSVRow();
						fileWriter.write(line);
					}
				}
				fileWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		
		// Load previously trained model
		else if (key == 'l') {    
			System.out.println("Loading!");
			String line = "";
			try {
				BufferedReader br = new BufferedReader(new FileReader(loadFileName));
				while((line = br.readLine()) != null) {
					line = line.substring(1);
					String[] dataInstance = line.split(",");
					String label = dataInstance[dataInstance.length - 1];
					float[] floatdata = new float[dataInstance.length - 2];
					for (int i = 1; i < dataInstance.length - 1; i++) {
						floatdata[i - 1] = Float.parseFloat(dataInstance[i]);
					}
					DataInstance res = new DataInstance();
					res.label = label;
					res.measurements = floatdata;
					
					trainingData.get(label).add(res);
					
				}
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if(classifier == null) {
				println("Start training ...");
				classifier = new MLClassifier();
				classifier.train(trainingData);
			}else {
				classifier = null;
			}
			System.out.println("Load complete!");
		}
		else if (key == ' ') {
			if (collectData == false) {collectData = true;}	//start collecting data
			else {	//stop collecting data, call findMode function, store result & output results
				collectData = false;	//stop collecting data
				results.add(findMode(dataCollected));	//add classification result to results array
				int results_size = results.size();
				for (int i = 0; i < results_size; i++) {	//print results array each time
					System.out.print("The classification of trial ");
					System.out.print(i);
					System.out.print(": ");
					System.out.println(results.get(i));
				}
				System.out.println("-----------------------------------------------------");
				dataCollected.clear();
				}
		}
		else {
			trainingData.get(classNames[classIndex]).add(captureInstance(classNames[classIndex]));
		}
		
	}

}
