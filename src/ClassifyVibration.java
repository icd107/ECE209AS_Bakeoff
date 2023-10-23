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
	String[] classNames = {"quiet", "hand drill", "whistling", "class clapping"};
	int classIndex = 0;
	int dataCount = 0;

	MLClassifier classifier;
	String fileName = "test.csv";
	String delimiter = ",";
	
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
			
			text("classified as: " + guessedLabel, 20, 30);
		} else {
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
			
			File csvFile = new File(fileName);
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
			String line = "";
			try {
				BufferedReader br = new BufferedReader(new FileReader(fileName));
				while((line = br.readLine()) != null) {
					String[] dataInstance = line.split(delimiter);
					int s = dataInstance.length;
					String label = dataInstance[dataInstance.length - 1];
					double[] data = Arrays.stream(dataInstance).mapToDouble(Double::parseDouble).toArray();
					double[] doubledata = new double[data.length - 1];
					for (int i = 1; i < data.length - 1; i++) {
						doubledata[i] = data[i];
					}
					
					Instance trainingData = classifier.featureCalc.instanceFromArray(doubledata, label);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
			
		else {
			trainingData.get(classNames[classIndex]).add(captureInstance(classNames[classIndex]));
		}
	}

}
